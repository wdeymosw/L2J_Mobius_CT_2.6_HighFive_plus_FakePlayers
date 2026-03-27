/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core;

import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

import org.l2jmobius.gameserver.bot.core.action.MoveToAction;
import org.l2jmobius.gameserver.bot.core.action.TeleportAction;
import org.l2jmobius.gameserver.bot.core.action.WaitAction;
import org.l2jmobius.gameserver.bot.core.behavior.BotBehavior;
import org.l2jmobius.gameserver.bot.core.behavior.FarmBehavior;
import org.l2jmobius.gameserver.bot.core.behavior.PassiveBehavior;
import org.l2jmobius.gameserver.bot.core.brain.BotBrain;
import org.l2jmobius.gameserver.bot.core.brain.BotDecision;
import org.l2jmobius.gameserver.bot.core.model.BotContext;
import org.l2jmobius.gameserver.bot.core.model.BotInstance;
import org.l2jmobius.gameserver.bot.core.model.BotState;
import org.l2jmobius.gameserver.bot.core.model.BotType;
import org.l2jmobius.gameserver.bot.core.model.TravelReason;
import org.l2jmobius.gameserver.bot.core.service.EquipService;
import org.l2jmobius.gameserver.bot.core.service.SellService;
import org.l2jmobius.gameserver.bot.core.service.SkillService;
import org.l2jmobius.gameserver.bot.core.service.SupplyService;
import org.l2jmobius.gameserver.bot.core.zone.BotZoneData;
import org.l2jmobius.gameserver.config.custom.BotConfig;
import org.l2jmobius.gameserver.model.Location;

/**
 * Layer 2 entry point — orchestrates one tick for a bot.
 * <p>
 * Handles cross-cutting concerns (throttle, death, state transitions, random mistakes),
 * then delegates action queuing to the bot's {@link BotBehavior} strategy.
 * <p>
 * Owns all state transitions: RETREAT, onArrived, onQueueCompleted, enterTravel, enterResting.
 * Behavior implementations only queue actions — they never call state-transition helpers.
 */
public class BotController
{
	private static final Logger LOGGER = Logger.getLogger(BotController.class.getName());

	private static final long THINK_DELAY_MIN = 250;
	private static final long THINK_DELAY_MAX = 400;
	private static final long REVIVE_DELAY_MIN = 1000;
	private static final long REVIVE_DELAY_MAX = 5000;
	private static final long GATE_WAIT_MIN = 15_000;
	private static final long GATE_WAIT_MAX = 25_000;

	private static final float TICKS_PER_MINUTE = 185f;
	private static final float MISTAKE_CHANCE_MIN = 3f;
	private static final float MISTAKE_CHANCE_MAX = 7f;

	private static final BotBehavior FARM_BEHAVIOR = new FarmBehavior();
	private static final BotBehavior PASSIVE_BEHAVIOR = new PassiveBehavior();

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

		// REST timer expired → return to farm.
		if ((bot.getState() == BotState.REST) && (now >= bot.getStateEndTime()))
		{
			enterTravel(bot, TravelReason.RETURN_TO_FARM, now);
			return;
		}

		// Drifted out of zone while farming → return.
		if (!bot.isInZone() && ((bot.getState() == BotState.SEARCH_TARGET) || (bot.getState() == BotState.ATTACK)))
		{
			enterTravel(bot, TravelReason.RETURN_TO_FARM, now);
			return;
		}

		// Random distraction (human-like mistake).
		if (((bot.getState() == BotState.ATTACK) || (bot.getState() == BotState.SEARCH_TARGET)) && shouldMakeMistake())
		{
			bot.clearTarget();
			bot.clearQueue();
			bot.setState(BotState.IDLE);
			bot.queueAction(new WaitAction(2000 + ThreadLocalRandom.current().nextLong(3000)));
			return;
		}

		// Build context and decide.
		final BotContext ctx = BotContext.of(bot);
		final BotDecision decision = BotBrain.decide(ctx, bot.getState());

		// RETREAT — handled here, not by behavior.
		if (decision == BotDecision.RETREAT)
		{
			if (bot.getState() != BotState.MOVE_TO_TARGET)
			{
				if (!ctx.lowHp || !SkillService.tryHealSkill(bot))
				{
					final TravelReason reason = ctx.lowHp ? TravelReason.RETURN_TO_FARM : TravelReason.GO_TO_CITY;
					enterTravel(bot, reason, now);
				}
			}
			bot.tickExecutor(now);
			if (bot.isQueueIdle())
			{
				onQueueCompleted(bot, ctx, now);
			}
			return;
		}

		// Delegate action queuing to behavior strategy.
		final BotBehavior behavior = bot.getProfile().getType() == BotType.ACTIVE ? FARM_BEHAVIOR : PASSIVE_BEHAVIOR;
		behavior.think(bot, decision, now);

		// Tick executor and check for queue completion.
		bot.tickExecutor(now);
		if (bot.isQueueIdle())
		{
			onQueueCompleted(bot, ctx, now);
		}
	}

	// =========================================================================
	// Queue-completion callbacks
	// =========================================================================

	private static void onQueueCompleted(BotInstance bot, BotContext ctx, long now)
	{
		switch (bot.getState())
		{
			case ATTACK:
			{
				bot.clearTarget();
				bot.setState(BotState.SEARCH_TARGET);
				bot.setNextSearchTime(now + 1000);
				break;
			}
			case MOVE_TO_TARGET:
			{
				onArrived(bot, ctx, now);
				break;
			}
			default:
				break;
		}
	}

	private static void onArrived(BotInstance bot, BotContext ctx, long now)
	{
		if (bot.getTravelReason() == TravelReason.RETURN_TO_FARM)
		{
			bot.setTravelReason(null);
			bot.setState(BotState.SEARCH_TARGET);
		}
		else
		{
			SellService.sell(bot);
			EquipService.equip(bot);
			SupplyService.restock(bot);
			if (bot.hasLeveledUp())
			{
				SkillService.setup(bot);
			}
			enterResting(bot, now);
		}
	}

	// =========================================================================
	// State-transition helpers (public — called by tests / manager layer)
	// =========================================================================

	/**
	 * Transitions the bot to {@link BotState#MOVE_TO_TARGET} toward the given destination.
	 * Queues city-walk waypoints + gate wait (when returning to farm), or a
	 * direct teleport to the city home location (when going to city).
	 *
	 * @param bot    the bot to transition
	 * @param reason where and why the bot is travelling
	 * @param now    current time in ms
	 */
	public static void enterTravel(BotInstance bot, TravelReason reason, long now)
	{
		bot.setState(BotState.MOVE_TO_TARGET);
		bot.setTravelReason(reason);
		bot.clearQueue();
		bot.clearTarget();
		bot.clearGlobalPath();

		if (reason == TravelReason.RETURN_TO_FARM)
		{
			// City → gatekeeper (walk) → farm (teleport).
			final BotZoneData cityData = bot.getProfile().getZone().getCityData();
			if ((cityData != null) && !cityData.getCityPath().isEmpty())
			{
				for (Location wp : cityData.getCityPath())
				{
					bot.queueAction(new MoveToAction(wp));
				}
				final long wait = GATE_WAIT_MIN + ThreadLocalRandom.current().nextLong(GATE_WAIT_MAX - GATE_WAIT_MIN);
				bot.queueAction(new WaitAction(wait));
			}
			final Location farm = bot.getProfile().getZone().getCenter();
			bot.queueAction(new TeleportAction(farm.getX(), farm.getY(), farm.getZ()));
		}
		else
		{
			// Farm → city: teleport directly to home location.
			final Location home = bot.getProfile().getZone().getHomeLocation();
			bot.queueAction(new TeleportAction(home.getX(), home.getY(), home.getZ()));
		}

		LOGGER.info("BotController: " + bot.getPlayer().getName() + " → MOVE_TO_TARGET (" + reason + ")");
	}

	/**
	 * Transitions the bot to {@link BotState#REST} for a randomised city-idle duration.
	 *
	 * @param bot the bot to transition
	 * @param now current time in ms
	 */
	public static void enterResting(BotInstance bot, long now)
	{
		final long minMs = BotConfig.BOT_CITY_IDLE_MIN_SECONDS * 1000L;
		final long maxMs = BotConfig.BOT_CITY_IDLE_MAX_SECONDS * 1000L;
		final long idleMs = minMs + ThreadLocalRandom.current().nextLong(Math.max(1, maxMs - minMs));
		bot.setStateEndTime(now + idleMs);
		bot.setState(BotState.REST);
		LOGGER.info("BotController: " + bot.getPlayer().getName() + " REST for " + (idleMs / 60_000) + " min");
	}

	// =========================================================================
	// Private helpers
	// =========================================================================

	/** Handles revive cycle — no BotState.DEAD; tracked via reviveTime. */
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
			enterTravel(bot, TravelReason.RETURN_TO_FARM, now);
			LOGGER.info("BotController: " + bot.getPlayer().getName() + " revived");
		}
	}

	private static boolean shouldMakeMistake()
	{
		final float chance = MISTAKE_CHANCE_MIN + ThreadLocalRandom.current().nextFloat() * (MISTAKE_CHANCE_MAX - MISTAKE_CHANCE_MIN);
		return ThreadLocalRandom.current().nextFloat() < (chance / 100f / TICKS_PER_MINUTE);
	}
}
