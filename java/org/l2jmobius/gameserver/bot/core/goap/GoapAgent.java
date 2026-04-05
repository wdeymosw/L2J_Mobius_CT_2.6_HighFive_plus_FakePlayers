/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.goap;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

import org.l2jmobius.gameserver.bot.core.goap.action.AttackGoapAction;
import org.l2jmobius.gameserver.bot.core.goap.action.DrinkPotionGoapAction;
import org.l2jmobius.gameserver.bot.core.goap.action.MoveToTargetGoapAction;
import org.l2jmobius.gameserver.bot.core.goap.action.PickupLootGoapAction;
import org.l2jmobius.gameserver.bot.core.goap.action.RestockGoapAction;
import org.l2jmobius.gameserver.bot.core.goap.action.SearchTargetGoapAction;
import org.l2jmobius.gameserver.bot.core.goap.action.SellItemsGoapAction;
import org.l2jmobius.gameserver.bot.core.goap.action.SitRestGoapAction;
import org.l2jmobius.gameserver.bot.core.goap.action.TeleportToCityGoapAction;
import org.l2jmobius.gameserver.bot.core.goap.action.TeleportToFarmGoapAction;
import org.l2jmobius.gameserver.bot.core.goap.action.UseBuffSkillGoapAction;
import org.l2jmobius.gameserver.bot.core.goap.action.UseDamageSkillGoapAction;
import org.l2jmobius.gameserver.bot.core.goap.action.UseHealSkillGoapAction;
import org.l2jmobius.gameserver.bot.core.exception.BotException;
import org.l2jmobius.gameserver.bot.core.exception.RecoverableBotException;
import org.l2jmobius.gameserver.bot.core.exception.ValidationBotException;
import org.l2jmobius.gameserver.bot.core.exception.FatalBotException;
import org.l2jmobius.gameserver.bot.core.exception.BotRecoveryStrategy;
import org.l2jmobius.gameserver.bot.core.validation.BotStateValidator;
import org.l2jmobius.gameserver.bot.core.validation.BotSystemStateValidator;
import org.l2jmobius.gameserver.bot.core.logging.StructuredBotLogger;
import org.l2jmobius.gameserver.bot.core.goap.goal.BuffGoal;
import org.l2jmobius.gameserver.bot.core.goap.goal.DefendGoal;
import org.l2jmobius.gameserver.bot.core.goap.goal.DrinkPotionGoal;
import org.l2jmobius.gameserver.bot.core.goap.goal.FarmGoal;
import org.l2jmobius.gameserver.bot.core.goap.goal.HuntGoal;
import org.l2jmobius.gameserver.bot.core.goap.goal.PickupGoal;
import org.l2jmobius.gameserver.bot.core.goap.goal.RestockGoal;
import org.l2jmobius.gameserver.bot.core.goap.goal.RestoreGoal;
import org.l2jmobius.gameserver.bot.core.goap.goal.SurviveGoal;
import org.l2jmobius.gameserver.bot.core.goap.goal.WanderGoal;
import org.l2jmobius.gameserver.bot.core.model.BotContext;
import org.l2jmobius.gameserver.bot.core.model.BotInstance;
import org.l2jmobius.gameserver.bot.core.service.TargetService;

/**
 * GOAP tick orchestrator. Sole entry point called by {@code BotInstance.update()}.
 * <p>
 * Cycle per tick:
 * <ol>
 *   <li>Throttle (250–400 ms, +random mistake pause)</li>
 *   <li>Dead → handleDead → return</li>
 *   <li>tickExecutor</li>
 *   <li>Build BotContext + WorldState</li>
 *   <li>If current action complete → advance plan, activate next</li>
 *   <li>If plan empty → replan</li>
 *   <li>If shouldInterrupt → clear plan + replan</li>
 * </ol>
 * <p>
 * <b>Not thread-safe.</b> {@link #tick} must be called exclusively from the BotManager
 * scheduler thread. All static state (ACTIONS, GOAL_SELECTOR) is read-only after class init.
 */
public class GoapAgent
{
	private static final Logger LOGGER = Logger.getLogger(GoapAgent.class.getName());

	/** Set to {@code true} to log goal selection, plan builds, and action transitions. */
	public static volatile boolean DEBUG = true;

	private static final long THINK_MS_MIN = GoapTuning.THINK_MS_MIN;
	private static final long THINK_MS_MAX = GoapTuning.THINK_MS_MAX;
	private static final long REVIVE_DELAY_MIN = GoapTuning.REVIVE_DELAY_MIN;
	private static final long REVIVE_DELAY_MAX = GoapTuning.REVIVE_DELAY_MAX;

	/** All available GOAP actions. Shared, stateless singletons. */
	private static final List<GoapAction> ACTIONS = List.of(
		new UseDamageSkillGoapAction(), // 0.8 — skill attack (preferred over autoattack)
		new AttackGoapAction(),         // 1.0 — autoattack fallback
		new MoveToTargetGoapAction(),
		new SearchTargetGoapAction(),
		new UseBuffSkillGoapAction(),
		new PickupLootGoapAction(),
		new DrinkPotionGoapAction(),
		new UseHealSkillGoapAction(),
		new SitRestGoapAction(),
		new TeleportToFarmGoapAction(),
		new TeleportToCityGoapAction(),
		new SellItemsGoapAction(),
		new RestockGoapAction());

	/** Goal selector evaluated each replan. Goals are checked in priority order. */
	private static final GoalSelector GOAL_SELECTOR = new GoalSelector(List.of(
		new SurviveGoal(),      // 100 — HP critical
		new DefendGoal(),       //  90 — under attack
		new DrinkPotionGoal(),  //  52 — HP low + potion ready (in and out of combat)
		new FarmGoal(),         //  50 — kill existing target
		new BuffGoal(),         //  47 — apply pending self-buffs
		new PickupGoal(),       //  46 — collect nearby loot
		new HuntGoal(),         //  45 — find a target (in zone, no target)
		new RestoreGoal(),      //  40 — HP/MP low (sit rest / heal skill)
		new RestockGoal(),      //  30 — out of supplies
		new WanderGoal()));     //  10 — fallback: get to farm zone

	private GoapAgent()
	{
	}

	// =========================================================================
	// Main entry point
	// =========================================================================

	/**
	 * Called every scheduler tick (via {@code bot.update(now)} when {@link #ENABLED}).
	 *
	 * @param bot the bot to tick
	 * @param now current time in ms
	 */
	public static void tick(BotInstance bot, long now)
	{
		try
		{
			tickInternal(bot, now);
		}
		catch (FatalBotException e)
		{
			// Unrecoverable: disable bot
			StructuredBotLogger.logBotDisabled(bot, e.getMessage());
			// Bot is already logged; BotManager will remove it on next check
		}
		catch (ValidationBotException e)
		{
			// State corrupted: attempt to fix and replan
			StructuredBotLogger.logExceptionCaught(bot, "ValidationBotException", "ADJUST_AND_REPLAN");
			bot.clearGoapPlan();
			bot.clearQueue();
			BotStateValidator.repair(bot);
		}
		catch (RecoverableBotException e)
		{
			// Temporary error: replan and continue
			StructuredBotLogger.logExceptionCaught(bot, "RecoverableBotException", "REPLAN");
			bot.clearGoapPlan();
		}
		catch (Exception e)
		{
			// Unexpected error: log and disable to prevent cascading failures
			StructuredBotLogger.severe(bot, StructuredBotLogger.EVENT_EXCEPTION_CAUGHT, "exception", e.getClass().getSimpleName(), "message", e.getMessage());
			e.printStackTrace();
			// Also write to a dedicated crash file so it is not lost in console scroll
			try
			{
				final java.io.File f = new java.io.File("log/bot-crash.log");
				f.getParentFile().mkdirs();
				try (java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.FileWriter(f, true)))
				{
					pw.println("=== " + new java.util.Date() + " bot=" + bot.getPlayer().getName() + " ===");
					e.printStackTrace(pw);
				}
			}
			catch (Exception ignored)
			{
			}
			bot.clearQueue();
			bot.clearGoapPlan();
		}
	}

	/**
	 * Internal tick logic (the actual GOAP cycle).
	 * Exceptions from this method propagate to tick() which applies recovery strategies.
	 *
	 * @param bot the bot to tick
	 * @param now current time in ms
	 * @throws FatalBotException if bot encounters fatal error
	 * @throws ValidationBotException if bot state becomes invalid
	 * @throws RecoverableBotException if bot encounters temporary error
	 */
	private static void tickInternal(BotInstance bot, long now) throws FatalBotException, ValidationBotException, RecoverableBotException
	{
		// 1. Throttle: 250–400 ms between ticks.
		if (now < bot.getNextThinkTime())
		{
			return;
		}

		// Random mistake: occasionally pause to simulate human imperfection.
		final float mistakeRate = bot.getMistakeRate();
		if ((mistakeRate > 0) && (ThreadLocalRandom.current().nextFloat() < mistakeRate))
		{
			bot.setNextThinkTime(now + 400 + ThreadLocalRandom.current().nextLong(600));
			return;
		}

		bot.setNextThinkTime(now + THINK_MS_MIN + ThreadLocalRandom.current().nextLong(THINK_MS_MAX - THINK_MS_MIN));

		// 2. Dead — separate pipeline.
		if (bot.isDead())
		{
			handleDead(bot, now);
			return;
		}

		// 3. Tick the action executor (moves, waits, attacks, etc.).
		bot.tickExecutor(now);

		// 4. Build context + world state snapshots.
		final BotContext ctx = BotContext.of(bot, now);
		final WorldState ws = WorldState.fromContext(ctx, bot);

		// 4.1 Track last combat time — used by SitRestGoapAction to prevent premature sitting.
		// HP-delta check catches mobs that aggroed but haven't registered in attacker lists yet.
		if (ctx.hasTarget() || (ctx.attackerCount > 0) || bot.hasTakenDamageSinceLastTick())
		{
			bot.updateLastCombatTime();
		}

		// 4.5 Validate system state consistency
		BotSystemStateValidator.validateFull(bot, ws);

		// 5. Advance plan if current action is complete.
		GoapAction current = bot.getCurrentGoapAction();
		if ((current != null) && current.isComplete(bot, ctx, now))
		{
			// Validate that effects are now present in world state
			BotStateValidator.validateEffects(current, ws, bot);

			if (DEBUG)
			{
				StructuredBotLogger.fine(bot, StructuredBotLogger.EVENT_ACTION_COMPLETE, "action", current.getName());
			}
			bot.advanceGoapPlan();
			current = bot.getCurrentGoapAction();
			if (current != null)
			{
				if (DEBUG)
				{
					StructuredBotLogger.fine(bot, StructuredBotLogger.EVENT_ACTION_START, "action", current.getName());
				}

				// Validate preconditions before activating
				BotStateValidator.validatePreconditions(current, ws, bot);

				current.activate(bot, now);
				// Start timeout tracking for this action
				bot.setCurrentActionStartTime(now);
				bot.setCurrentActionTimeoutMs(current.getActionTimeoutMs());
				StructuredBotLogger.logActionStart(bot, current.getName(), current.getActionTimeoutMs());
			}
		}
		// 5.5 Action is not done but the executor has nothing left to run
		// (e.g. mob moved after stuck-teleport) — re-activate to issue fresh commands.
		else if ((current != null) && bot.isQueueIdle())
		{
			current.activate(bot, now);
		}

		// 5.7 Check if current action has exceeded its timeout.
		if (checkActionTimeout(bot, now))
		{
			final GoapAction timedOut = bot.getCurrentGoapAction();
			if (DEBUG && (timedOut != null))
			{
				StructuredBotLogger.warning(bot, StructuredBotLogger.EVENT_ACTION_TIMEOUT, "action", timedOut.getName());
			}
			// If we timed out while moving toward a target, that target is likely
			// unreachable (GEO blocked, mob fled, etc.). Clear it so the next
			// replan picks a different mob instead of looping on the same one.
			if ((timedOut instanceof MoveToTargetGoapAction) && bot.hasTarget())
			{
				final org.l2jmobius.gameserver.model.actor.Creature unreachableTarget = bot.getTarget();
				StructuredBotLogger.warning(bot, "TARGET_UNREACHABLE", "target", unreachableTarget != null ? unreachableTarget.getName() : "?");
				bot.clearTarget();
			}
			// Let the action clean up any persistent game state (e.g. SitRest → stand up).
			if (timedOut != null)
			{
				timedOut.onAbort(bot);
			}
			bot.clearGoapPlan();
		}

		// 6. Plan exhausted → build a new one.
		// Rebuild ctx+ws here so replan always sees the freshest attacker/target state,
		// not the snapshot from step 4 which may predate a mob's first attack this tick.
		if (bot.getCurrentGoapAction() == null)
		{
			// Before replan, switch to nearest attacker if under attack.
			final BotContext replanCtx = BotContext.of(bot, now);
			final WorldState replanWs = WorldState.fromContext(replanCtx, bot);
			if (replanWs.get(Fact.UNDER_ATTACK))
			{
				TargetService.switchToNearestAttacker(bot);
				// Rebuild once more with updated target
				final BotContext attackCtx = BotContext.of(bot, now);
				final WorldState attackWs = WorldState.fromContext(attackCtx, bot);
				replan(bot, attackCtx, attackWs, now);
			}
			else
			{
				replan(bot, replanCtx, replanWs, now);
			}
			return;
		}

		// 7. Interrupt on HP_CRITICAL or unhandled UNDER_ATTACK.
		if (shouldInterrupt(ws, bot))
		{
			// Before actually clearing the plan, peek at what goal would win now.
			// If the new goal has lower or equal priority than the current plan's goal,
			// the interrupt would be a downgrade (e.g. WanderGoal=10 interrupting FarmGoal=50
			// when HP recovered via potions mid-tick). Skip in that case.
			if (ws.get(Fact.UNDER_ATTACK) || bot.hasTakenDamageSinceLastTick())
			{
				TargetService.switchToNearestAttacker(bot);
			}
			final BotContext freshCtx = BotContext.of(bot, now);
			final WorldState freshWs = WorldState.fromContext(freshCtx, bot);
			final GoapGoal newGoal = GOAL_SELECTOR.select(freshWs);
			final int newPriority = (newGoal != null) ? newGoal.getPriority(freshWs) : 0;
			if (newPriority <= bot.getActiveGoalPriority())
			{
				// No upgrade available — don't interrupt, let the current plan continue.
				bot.updateHpSnapshot();
				return;
			}

			if (DEBUG)
			{
				StructuredBotLogger.fine(bot, StructuredBotLogger.EVENT_PLAN_INTERRUPT, "action", bot.getCurrentGoapAction() != null ? bot.getCurrentGoapAction().getName() : "none");
			}
			StructuredBotLogger.logPlanInterrupt(bot, "HP_CRITICAL or UNDER_ATTACK");
			// Let the interrupted action clean up its game state before plan is cleared.
			final GoapAction interrupted = bot.getCurrentGoapAction();
			if (interrupted != null)
			{
				interrupted.onAbort(bot);
			}
			bot.clearGoapPlan();
			replan(bot, freshCtx, freshWs, now);
		}

		// End of tick — snapshot HP for next-tick damage detection.
		bot.updateHpSnapshot();
	}

	// =========================================================================
	// Action timeout check
	// =========================================================================

	/**
	 * Checks if the current action has exceeded its timeout.
	 * Called each tick to detect stuck or slow-running actions.
	 *
	 * @param bot the bot being evaluated
	 * @param now current time in ms
	 * @return true if the action has timed out and the plan should be cleared
	 */
	private static boolean checkActionTimeout(BotInstance bot, long now)
	{
		final long actionStartTime = bot.getCurrentActionStartTime();
		final long actionTimeoutMs = bot.getCurrentActionTimeoutMs();

		// No active action or no timeout set
		if ((actionStartTime == 0) || (actionTimeoutMs == 0))
		{
			return false;
		}

		final long elapsed = now - actionStartTime;
		if (elapsed > actionTimeoutMs)
		{
			final GoapAction current = bot.getCurrentGoapAction();
			if (current != null)
			{
				StructuredBotLogger.logActionTimeout(bot, current.getName(), elapsed, actionTimeoutMs);
			}
			// Reset the timing fields for next action
			bot.setCurrentActionStartTime(0);
			bot.setCurrentActionTimeoutMs(0);
			return true;
		}

		return false;
	}

	// =========================================================================
	// Dead handling
	// =========================================================================

	private static void handleDead(BotInstance bot, long now)
	{
		if (bot.getReviveTime() == 0)
		{
			// First tick after death: clear everything and schedule revive.
			bot.clearQueue();
			bot.clearTarget();
			bot.clearGoapPlan();
			final long delay = REVIVE_DELAY_MIN + ThreadLocalRandom.current().nextLong(REVIVE_DELAY_MAX - REVIVE_DELAY_MIN);
			bot.setReviveTime(now + delay);
			StructuredBotLogger.info(bot, "BOT_DEAD", "revive_delay_ms", delay);
			return;
		}

		if (now >= bot.getReviveTime())
		{
			bot.setReviveTime(0);
			bot.getPlayer().doRevive();
			bot.getPlayer().setRunning();
			// Reset zone/search expansion so the city home location is never mistaken
			// for "inside the farm zone" due to a previously expanded effective radius.
			bot.onSearchSuccess();
			// Teleport to city as a proper GOAP plan so the agent tracks completion
			// and replans (WanderGoal → TeleportToFarm) once the city queue finishes.
			final TeleportToCityGoapAction teleportToCity = new TeleportToCityGoapAction();
			bot.setGoapPlan(List.of(teleportToCity));
			teleportToCity.activate(bot, now);
			StructuredBotLogger.logBotRevived(bot);
		}
	}

	// =========================================================================
	// Planning
	// =========================================================================

	private static void replan(BotInstance bot, BotContext ctx, WorldState ws, long now)
	{
		// Collect actions that are currently executable.
		final List<GoapAction> valid = new ArrayList<>();
		for (GoapAction action : ACTIONS)
		{
			if (action.isValid(ctx, bot))
			{
				valid.add(action);
			}
		}

		final GoapGoal goal = GOAL_SELECTOR.select(ws);
		if (goal == null)
		{
			// Log at warning level so it always appears in logs regardless of DEBUG mode.
			StructuredBotLogger.warning(bot, "REPLAN_NO_GOAL",
				"TARGET_EXISTS", ws.get(Fact.TARGET_EXISTS),
				"IN_FARM_ZONE", ws.get(Fact.IN_FARM_ZONE),
				"HP_LOW", ws.get(Fact.HP_LOW),
				"INVENTORY_OK", ws.get(Fact.INVENTORY_OK),
				"HAS_AMMO", ws.get(Fact.HAS_AMMO),
				"UNDER_ATTACK", ws.get(Fact.UNDER_ATTACK),
				"LOOT_NEARBY", ws.get(Fact.LOOT_NEARBY));
			return; // all goals satisfied — nothing to do
		}

		final long planStartTime = System.currentTimeMillis();
		final List<GoapAction> plan = GoapPlanner.plan(ws, goal, valid);
		final long planDuration = System.currentTimeMillis() - planStartTime;

		if (plan.isEmpty())
		{
			StructuredBotLogger.warning(bot, StructuredBotLogger.EVENT_PLAN_BUILD, "goal", goal.getName(), "result", "no_plan");
			return;
		}

		StructuredBotLogger.logPlanBuild(bot, goal.getName(), plan.size(), planDuration);

		bot.setGoapPlan(plan);
		bot.setActiveGoalPriority(goal.getPriority(ws));
		final GoapAction first = bot.getCurrentGoapAction();
		if (first != null)
		{
			first.activate(bot, now);
		}
	}

	// =========================================================================
	// Interrupt check
	// =========================================================================

	/**
	 * Returns {@code true} when the current plan should be discarded and rebuilt.
	 * <p>
	 * Triggers on:
	 * <ul>
	 *   <li>{@code HP_CRITICAL} — SurviveGoal must take over immediately.</li>
	 *   <li>{@code UNDER_ATTACK} while executing a non-combat action — DefendGoal must engage.</li>
	 *   <li>{@code UNDER_ATTACK} while in kill sequence or moving toward a target, but a DIFFERENT
	 *       mob is attacking — the bot should stop, switch target, and fight the actual threat.</li>
	 *   <li>{@code OVERWEIGHT} — must sell immediately; skip if already heading to safe place.</li>
	 * </ul>
	 *
	 * @param ws  current world state
	 * @param bot the bot being evaluated
	 * @return true if the plan should be abandoned and rebuilt this tick
	 */
	private static boolean shouldInterrupt(WorldState ws, BotInstance bot)
	{
		if (ws.get(Fact.HP_CRITICAL))
		{
			return true;
		}

		// Overloaded — can't move at all, must sell immediately.
		if (ws.get(Fact.OVERWEIGHT))
		{
			final GoapAction current = bot.getCurrentGoapAction();
			// Don't interrupt if already heading to sell.
			if (current != null)
			{
				final WorldState effects = current.getEffects();
				if (effects.isExplicitlySet(Fact.INVENTORY_OK) || effects.isExplicitlySet(Fact.IN_SAFE_PLACE))
				{
					return false;
				}
			}
			return true;
		}

		if (ws.get(Fact.UNDER_ATTACK))
		{
			final GoapAction current = bot.getCurrentGoapAction();
			if (current != null)
			{
				final WorldState effects = current.getEffects();
				// Already in kill sequence or moving toward target — only interrupt
				// if a DIFFERENT mob is attacking. If the attacker IS the current
				// target, continue the plan unchanged.
				if (effects.isExplicitlySet(Fact.TARGET_DEAD) || effects.isExplicitlySet(Fact.TARGET_IN_RANGE))
				{
					return ws.get(Fact.ATTACKED_BY_DIFFERENT);
				}
				// Any other non-combat action (rest, search, teleport, etc.) → interrupt immediately.
				return true;
			}
		}

		// HP dropped this tick — we're taking damage even if attacker lists haven't updated yet.
		// Covers the race: mob aggroed + started moving toward bot but target not set yet.
		if (bot.hasTakenDamageSinceLastTick())
		{
			final GoapAction current = bot.getCurrentGoapAction();
			if (current != null)
			{
				final WorldState effects = current.getEffects();
				// Don't interrupt an active attack/move-to sequence — bot is already fighting.
				if (!effects.isExplicitlySet(Fact.TARGET_DEAD) && !effects.isExplicitlySet(Fact.TARGET_IN_RANGE) && !effects.isExplicitlySet(Fact.THREAT_NEUTRALIZED))
				{
					return true; // Taking damage outside of a combat action — interrupt immediately.
				}
			}
		}

		// HP is low and a potion is available — interrupt the current plan to drink immediately.
		// DrinkPotionGoal (priority 52) will win over FarmGoal (50) on the next replan.
		if (ws.get(Fact.HP_LOW) && ws.get(Fact.POTION_READY) && ws.get(Fact.HAS_POTIONS) && !ws.get(Fact.HP_CRITICAL))
		{
			final GoapAction current = bot.getCurrentGoapAction();
			if ((current != null) && !(current instanceof DrinkPotionGoapAction))
			{
				return true;
			}
		}

		return false;
	}
}
