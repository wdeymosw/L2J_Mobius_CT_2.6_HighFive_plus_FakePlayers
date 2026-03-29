/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core;

import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

import org.l2jmobius.gameserver.bot.core.action.DrinkPotionAction;
import org.l2jmobius.gameserver.bot.core.action.MoveToAction;
import org.l2jmobius.gameserver.bot.core.action.TeleportAction;
import org.l2jmobius.gameserver.bot.core.action.WaitAction;
import org.l2jmobius.gameserver.bot.core.behavior.BotBehavior;
import org.l2jmobius.gameserver.bot.core.behavior.FarmBehavior;
import org.l2jmobius.gameserver.bot.core.behavior.PassiveBehavior;
import org.l2jmobius.gameserver.bot.core.brain.BotBrain;
import org.l2jmobius.gameserver.bot.core.brain.BotIntention;
import org.l2jmobius.gameserver.bot.core.model.BotInstance;
import org.l2jmobius.gameserver.bot.core.model.BotContext;
import org.l2jmobius.gameserver.bot.core.model.BotPhase;
import org.l2jmobius.gameserver.bot.core.model.BotState;
import org.l2jmobius.gameserver.bot.core.model.BotType;
import org.l2jmobius.gameserver.bot.core.service.ShopService;
import org.l2jmobius.gameserver.bot.core.service.SkillService;
import org.l2jmobius.gameserver.bot.core.service.TargetService;
import org.l2jmobius.gameserver.bot.core.zone.BotZoneData;
import org.l2jmobius.gameserver.ai.Intention;
import org.l2jmobius.gameserver.config.custom.BotConfig;
import org.l2jmobius.gameserver.model.Location;
import org.l2jmobius.gameserver.model.actor.Attackable;
import org.l2jmobius.gameserver.model.actor.Creature;
import org.l2jmobius.gameserver.model.actor.Player;

/**
 * Layer 2 entry point — orchestrates one tick for a bot.
 * <p>
 * Handles cross-cutting concerns (throttle, death, state transitions, random mistakes),
 * then delegates action queuing to the bot's {@link BotBehavior} strategy.
 * <p>
 * Owns all state and phase transitions.
 * Behavior implementations queue actions and handle city/farm arrival via {@link BotBehavior#onArrived}.
 */
public class BotController
{
	private static final Logger LOGGER = Logger.getLogger(BotController.class.getName());

	private static final long THINK_DELAY_MIN = 250;
	private static final long THINK_DELAY_MAX = 400;
	private static final long REVIVE_DELAY_MIN = 1000;
	private static final long REVIVE_DELAY_MAX = 5000;
	private static final long TELEPORT_SETTLE_MS = 3_000;

	private static final float TICKS_PER_MINUTE = 185f;
	private static final float MISTAKE_CHANCE_MIN = 3f;
	private static final float MISTAKE_CHANCE_MAX = 7f;

	private static final BotBehavior FARM_BEHAVIOR = new FarmBehavior();
	private static final BotBehavior PASSIVE_BEHAVIOR = new PassiveBehavior();

	/** Set to {@code true} to log every tick — disable in production. */
	public static volatile boolean DEBUG_TICKS = true;

	private BotController()
	{
	}

	// =========================================================================
	// Main entry point
	// =========================================================================

	/**
	 * Called every scheduler tick (via {@code bot.update(now)}).
	 * Runs cross-cutting checks, decides, then delegates action queuing to behavior.
	 *
	 * @param bot the bot to tick
	 * @param now current time in ms
	 */
	public static void tick(BotInstance bot, long now)
	{
		// Throttle: 250–400 ms between ticks per bot.
		if (now < bot.getNextThinkTime())
		{
			return;
		}
		bot.setNextThinkTime(now + THINK_DELAY_MIN + ThreadLocalRandom.current().nextLong(THINK_DELAY_MAX - THINK_DELAY_MIN));

		// Dead — separate pipeline before any state logic.
		if (bot.getPlayer().isDead())
		{
			handleDead(bot, now);
			return;
		}

		final BotBehavior behavior = bot.getProfile().getType() == BotType.ACTIVE ? FARM_BEHAVIOR : PASSIVE_BEHAVIOR;

		// RESTING: wait until HP and MP are full (or fallback max-timer expires).
		if (bot.getPhase() == BotPhase.RESTING)
		{
			final Player restingPlayer = bot.getPlayer();
			final boolean recovered = (restingPlayer.getCurrentHp() >= restingPlayer.getMaxHp() * 0.99)
				&& (restingPlayer.getCurrentMp() >= restingPlayer.getMaxMp() * 0.99);
			if (recovered || (now >= bot.getStateEndTime()))
			{
				enterTravel(bot, BotPhase.TRAVELING_OUT, now);
			}
			return;
		}

		// Non-farming phases: just tick the executor and check for arrival.
		// The action queue was pre-loaded by enterTravel; no tactical decisions needed.
		// RESTING: timer handled above — queue is intentionally empty, do not call onArrived again.
		if (bot.getPhase() != BotPhase.FARMING)
		{
			if (bot.getPhase() == BotPhase.RESTING)
			{
				return;
			}
			bot.tickExecutor(now);
			if (bot.isQueueIdle())
			{
				final BotContext ctx = BotContext.of(bot, now);
				behavior.onArrived(bot, ctx, now);
			}
			return;
		}

		// --- FARMING phase below ---

		// Drifted out of zone while farming → return to farm zone center.
		if (!bot.isInZone() && ((bot.getState() == BotState.SEARCH_TARGET) || (bot.getState() == BotState.ATTACK)))
		{
			enterTravel(bot, BotPhase.TRAVELING_OUT, now);
			return;
		}

		// Level-up: give newly unlocked skill levels immediately without waiting for city visit.
		if (bot.hasLeveledUp())
		{
			SkillService.setup(bot);
		}

		// Stale combat queue: target died before the bot reached it.
		// Clear leftover MoveToAction/AttackAction so RECOVER/USE_POTION do not
		// run pathfinding toward a dead mob (causes spurious stuck-teleports).
		if ((bot.getState() == BotState.ATTACK) && !bot.hasTarget() && !bot.isQueueIdle())
		{
			bot.clearQueue();
			bot.setState(BotState.SEARCH_TARGET);
			bot.setNextSearchTime(now + 1000);
		}

		// Build context snapshot for environment transition checks.
		BotContext ctx = BotContext.of(bot, now);

		// --- Environment → state transitions (BotController owns all state changes) ---

		// 1. Critical conditions → RETREATING.
		//    Highest priority: overrides everything except an already-running retreat.
		final boolean critical = (ctx.hpPercent < BotBrain.LOW_HP_THRESHOLD)
			|| ctx.inventoryFull
			|| ctx.outOfAmmo
			|| (ctx.weightPenalty >= 2)
			|| (ctx.attackerCount >= 3);
		if (critical && (bot.getState() != BotState.RETREATING))
		{
			bot.clearQueue();
			bot.clearTarget();
			bot.setState(BotState.RETREATING);
		}
		// 2. Under attack — always switch to the attacker unless already fighting it
		//    or retreating. Fires even when in ATTACK state so the bot never ignores
		//    a mob that is targeting it.
		else if ((ctx.attackerCount > 0) && (bot.getState() != BotState.RETREATING))
		{
			final Creature current = bot.getTarget();
			final boolean alreadyOnAttacker = (current instanceof Attackable)
				&& (current.getTarget() == bot.getPlayer());
			if (!alreadyOnAttacker)
			{
				bot.clearQueue();
				bot.clearTarget();
				TargetService.findTarget(bot); // priority 1: nearest mob targeting the bot
				bot.setState(BotState.DEFENDING);
			}
		}

		// 2b. DEFENDING with no target — attacker not found (out of range?) or already dead.
		//     Resume searching immediately without the 2-4 s WaitAction pause.
		if ((bot.getState() == BotState.DEFENDING) && !bot.hasTarget())
		{
			bot.clearQueue();
			bot.setState(BotState.SEARCH_TARGET);
			bot.setNextSearchTime(now + 500);
		}

		// Rebuild context so Brain sees the target set by findTarget above.
		ctx = BotContext.of(bot, now);

		// Random distraction (human-like mistake) — only while farming, never while defending.
		if (((bot.getState() == BotState.ATTACK) || (bot.getState() == BotState.SEARCH_TARGET)) && shouldMakeMistake())
		{
			bot.clearTarget();
			bot.clearQueue();
			bot.setState(BotState.IDLE);
			bot.queueAction(new WaitAction(2000 + ThreadLocalRandom.current().nextLong(3000)));
			return;
		}

		final BotIntention intention = BotBrain.decide(ctx, bot.getState());

		if (DEBUG_TICKS)
		{
			logTick(bot, ctx, intention);
		}

		// RETREAT — handled here, not by behavior.
		if (intention == BotIntention.RETREAT)
		{
			final boolean lowHp = ctx.hpPercent < BotBrain.LOW_HP_THRESHOLD;
			if (!lowHp || !SkillService.tryHealSkill(bot))
			{
				enterTravel(bot, BotPhase.TRAVELING_BACK, now);
			}
			bot.tickExecutor(now);
			if (bot.isQueueIdle())
			{
				onQueueCompleted(bot, ctx, now, behavior);
			}
			return;
		}

		// USE_POTION — cancel any pending movement, queue a one-shot DrinkPotionAction.
		if (intention == BotIntention.USE_POTION)
		{
			bot.clearQueue(); // stop any stale movement (wander, chase)
			bot.queueAction(new DrinkPotionAction());
			bot.tickExecutor(now);
			if (bot.isQueueIdle())
			{
				onQueueCompleted(bot, ctx, now, behavior);
			}
			return;
		}

		// RECOVER — cancel any pending movement and sit down for faster HP/MP regeneration.
		if (intention == BotIntention.RECOVER)
		{
			bot.clearQueue(); // stop any stale movement so PathService doesn't run while sitting
			if (!bot.getPlayer().isSitting())
			{
				bot.getPlayer().sitDown();
			}
			return;
		}

		// Force stand up immediately — bypasses the 2.5s animation lock so the bot
		// can react to combat without waiting for the SitDown/StandUp task to finish.
		if (bot.getPlayer().isSitting())
		{
			forceStandUp(bot);
		}

		// Pre-set state based on intention — BotController owns all state transitions.
		// Behavior receives a bot whose state is already correct and only queues actions.
		switch (intention)
		{
			case ATTACK_TARGET:
			case APPROACH_TARGET:
			{
				// DEFENDING stays DEFENDING — it resolves to SEARCH_TARGET via onQueueCompleted.
				if ((bot.getState() != BotState.ATTACK) && (bot.getState() != BotState.DEFENDING))
				{
					bot.setState(BotState.ATTACK);
					bot.clearQueue();
				}
				break;
			}
			case SEARCH_TARGET:
			{
				if (bot.getState() != BotState.SEARCH_TARGET)
				{
					bot.setState(BotState.SEARCH_TARGET);
					bot.clearQueue();
					bot.setNextSearchTime(0);
				}
				break;
			}
			default:
				break;
		}

		// Delegate action queuing to behavior strategy.
		behavior.think(bot, intention, now);

		// Tick executor and check for queue completion.
		bot.tickExecutor(now);
		if (bot.isQueueIdle())
		{
			onQueueCompleted(bot, ctx, now, behavior);
		}
	}

	// =========================================================================
	// Queue-completion callbacks
	// =========================================================================

	private static void onQueueCompleted(BotInstance bot, BotContext ctx, long now, BotBehavior behavior)
	{
		// Non-farming phases: arrival is handled by behavior.
		if (bot.getPhase() != BotPhase.FARMING)
		{
			behavior.onArrived(bot, ctx, now);
			return;
		}

		switch (bot.getState())
		{
			case ATTACK:
			{
				if (bot.hasTarget())
				{
					// Target still alive — Brain will re-queue AttackAction next tick.
					break;
				}
				// Target is dead.
				bot.clearTarget();
				bot.setState(BotState.SEARCH_TARGET);
				bot.setNextSearchTime(now + 1000);
				break;
			}
			case DEFENDING:
			{
				if (bot.hasTarget())
				{
					// Attacker still alive — Brain re-queues AttackAction next tick.
					break;
				}
				// Attacker is dead — return to normal farming.
				bot.clearTarget();
				bot.setState(BotState.SEARCH_TARGET);
				bot.setNextSearchTime(now + 500);
				break;
			}
			default:
				break;
		}
	}

	// =========================================================================
	// State-transition helpers (public — called by tests / manager layer)
	// =========================================================================

	/**
	 * Transitions the bot to a travel phase toward the given destination.
	 * Queues city-walk waypoints + gate wait (when returning to farm), or a
	 * direct teleport to the city home location (when going to city).
	 *
	 * @param bot         the bot to transition
	 * @param destination {@link BotPhase#TRAVELING_OUT} or {@link BotPhase#TRAVELING_BACK}
	 * @param now         current time in ms
	 */
	public static void enterTravel(BotInstance bot, BotPhase destination, long now)
	{
		ShopService.closeShop(bot);
		bot.setPhase(destination);
		bot.setState(BotState.IDLE);
		bot.clearQueue();
		bot.clearTarget();
		bot.clearGlobalPath();

		if (destination == BotPhase.TRAVELING_OUT)
		{
			// Sell/equip/restock already done in onArrived — go straight to farm.
			final Location farm = bot.getProfile().getZone().getCenter();
			bot.queueAction(new TeleportAction(farm.getX(), farm.getY(), farm.getZ()));
			bot.queueAction(new WaitAction(TELEPORT_SETTLE_MS));
		}
		else
		{
			// Farm → city: teleport directly to home location.
			final Location home = bot.getProfile().getZone().getHomeLocation();
			bot.queueAction(new TeleportAction(home.getX(), home.getY(), home.getZ()));
			bot.queueAction(new WaitAction(TELEPORT_SETTLE_MS));
		}

		LOGGER.info("BotController: " + bot.getPlayer().getName() + " → " + destination);
	}

	// =========================================================================
	// Private helpers
	// =========================================================================

	/**
	 * Handles revive cycle — no BotState.DEAD; tracked via reviveTime.
	 *
	 * @param bot the dead bot
	 * @param now current time in ms
	 */
	private static void handleDead(BotInstance bot, long now)
	{
		if (bot.getReviveTime() == 0)
		{
			// First tick after death: schedule revive.
			bot.clearQueue();
			bot.clearTarget();
			final long delay = REVIVE_DELAY_MIN + ThreadLocalRandom.current().nextLong(REVIVE_DELAY_MAX - REVIVE_DELAY_MIN);
			bot.setReviveTime(now + delay);
			bot.setState(BotState.IDLE);
			LOGGER.info("BotController: " + bot.getPlayer().getName() + " died, reviving in " + (delay / 1000) + "s");
			return;
		}

		if (now >= bot.getReviveTime())
		{
			bot.setReviveTime(0);
			bot.getPlayer().doRevive();
			bot.getPlayer().setRunning();
			// Go straight back to the farm zone — HP is restored by doRevive().
			// If the bot is out of supplies the Brain will detect it (outOfAmmo /
			// inventoryFull) and trigger a city visit on its own.
			enterTravel(bot, BotPhase.TRAVELING_OUT, now);
			LOGGER.info("BotController: " + bot.getPlayer().getName() + " revived");
		}
	}

	private static void logTick(BotInstance bot, BotContext ctx, BotIntention intention)
	{
		final String target = (ctx.target != null)
			? ctx.target.getName() + "(d=" + (int) bot.getPlayer().calculateDistance3D(ctx.target) + (ctx.canAttackTarget ? ",ok" : ",far") + ")"
			: "none";
		final String flags = ((ctx.hpPercent < BotBrain.LOW_HP_THRESHOLD) ? " LOW_HP" : "") + ((ctx.potionReuseReady && (ctx.hpPercent < 60.0)) ? " POTION" : "") + (ctx.inventoryFull ? " INV_FULL" : "") + (ctx.outOfAmmo ? " NO_AMMO" : "") + (ctx.weightPenalty >= 2 ? " OVERWEIGHT" : "") + (ctx.attackerCount > 0 ? " ATK=" + ctx.attackerCount : "");
		LOGGER.info("[BOT] " + bot.getPlayer().getName()
			+ " | " + bot.getPhase() + "/" + bot.getState() + " → " + intention
			+ " | hp=" + (int) ctx.hpPercent + "%"
			+ " tgt=" + target
			+ " q=" + (bot.isQueueIdle() ? "idle" : "busy")
			+ (flags.isEmpty() ? "" : " |" + flags));
	}

	/**
	 * Forces the bot to stand up immediately, bypassing the 2.5s animation lock.
	 * Normal standUp() blocks on _sittingInProgress; this clears the flag first
	 * so the bot can react to combat without waiting for the animation task.
	 *
	 * @param bot the bot to stand up
	 */
	private static void forceStandUp(BotInstance bot)
	{
		final Player player = bot.getPlayer();
		player.setSittingProgress(false);
		player.setSitting(false);
		player.getAI().setIntention(Intention.IDLE);
	}

	private static boolean shouldMakeMistake()
	{
		final float chance = MISTAKE_CHANCE_MIN + ThreadLocalRandom.current().nextFloat() * (MISTAKE_CHANCE_MAX - MISTAKE_CHANCE_MIN);
		return ThreadLocalRandom.current().nextFloat() < (chance / 100f / TICKS_PER_MINUTE);
	}
}
