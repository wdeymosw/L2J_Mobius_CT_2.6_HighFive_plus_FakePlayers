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
import org.l2jmobius.gameserver.bot.core.goap.action.RestockGoapAction;
import org.l2jmobius.gameserver.bot.core.goap.action.SearchTargetGoapAction;
import org.l2jmobius.gameserver.bot.core.goap.action.SellItemsGoapAction;
import org.l2jmobius.gameserver.bot.core.goap.action.SitRestGoapAction;
import org.l2jmobius.gameserver.bot.core.goap.action.TeleportToCityGoapAction;
import org.l2jmobius.gameserver.bot.core.goap.action.TeleportToFarmGoapAction;
import org.l2jmobius.gameserver.bot.core.goap.action.UseHealSkillGoapAction;
import org.l2jmobius.gameserver.bot.core.goap.goal.DefendGoal;
import org.l2jmobius.gameserver.bot.core.goap.goal.FarmGoal;
import org.l2jmobius.gameserver.bot.core.goap.goal.HuntGoal;
import org.l2jmobius.gameserver.bot.core.goap.goal.RestockGoal;
import org.l2jmobius.gameserver.bot.core.goap.goal.RestoreGoal;
import org.l2jmobius.gameserver.bot.core.goap.goal.SurviveGoal;
import org.l2jmobius.gameserver.bot.core.goap.goal.WanderGoal;
import org.l2jmobius.gameserver.bot.core.model.BotContext;
import org.l2jmobius.gameserver.bot.core.model.BotInstance;

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
 */
public class GoapAgent
{
	private static final Logger LOGGER = Logger.getLogger(GoapAgent.class.getName());

	/** Set to {@code true} to log goal selection, plan builds, and action transitions. */
	public static volatile boolean DEBUG = true;

	private static final long THINK_MS_MIN = 250;
	private static final long THINK_MS_MAX = 400;
	private static final long REVIVE_DELAY_MIN = 1_000;
	private static final long REVIVE_DELAY_MAX = 5_000;

	/** All available GOAP actions. Shared, stateless singletons. */
	private static final List<GoapAction> ACTIONS = List.of(
		new AttackGoapAction(),
		new MoveToTargetGoapAction(),
		new SearchTargetGoapAction(),
		new DrinkPotionGoapAction(),
		new UseHealSkillGoapAction(),
		new SitRestGoapAction(),
		new TeleportToFarmGoapAction(),
		new TeleportToCityGoapAction(),
		new SellItemsGoapAction(),
		new RestockGoapAction());

	/** Goal selector evaluated each replan. Goals are checked in priority order. */
	private static final GoalSelector GOAL_SELECTOR = new GoalSelector(List.of(
		new SurviveGoal(),  // 100 — HP critical
		new DefendGoal(),   // 90  — under attack
		new FarmGoal(),     // 50  — kill existing target
		new HuntGoal(),     // 45  — find a target (in zone, no target)
		new RestoreGoal(),  // 40  — HP/MP low
		new RestockGoal(),  // 30  — out of supplies
		new WanderGoal())); // 10  — fallback: get to farm zone

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

		// 5. Advance plan if current action is complete.
		GoapAction current = bot.getCurrentGoapAction();
		if ((current != null) && current.isComplete(bot, ctx, now))
		{
			if (DEBUG)
			{
				LOGGER.info("[GOAP] " + bot.getPlayer().getName() + " ✓ " + current.getName());
			}
			bot.advanceGoapPlan();
			current = bot.getCurrentGoapAction();
			if (current != null)
			{
				if (DEBUG)
				{
					LOGGER.info("[GOAP] " + bot.getPlayer().getName() + " → " + current.getName());
				}
				current.activate(bot, now);
			}
		}
		// 5.5 Action is not done but the executor has nothing left to run
		// (e.g. mob moved after stuck-teleport) — re-activate to issue fresh commands.
		else if ((current != null) && bot.isQueueIdle())
		{
			current.activate(bot, now);
		}

		// 6. Plan exhausted → build a new one.
		if (bot.getCurrentGoapAction() == null)
		{
			replan(bot, ctx, ws, now);
			return;
		}

		// 7. Interrupt on HP_CRITICAL or unhandled UNDER_ATTACK.
		if (shouldInterrupt(ws, bot))
		{
			if (DEBUG)
			{
				LOGGER.info("[GOAP] " + bot.getPlayer().getName() + " INTERRUPT " + bot.getCurrentGoapAction().getName() + " ws=" + ws);
			}
			bot.clearGoapPlan();
			replan(bot, ctx, ws, now);
		}
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
			LOGGER.info("GoapAgent: " + bot.getPlayer().getName() + " died, reviving in " + (delay / 1000) + "s");
			return;
		}

		if (now >= bot.getReviveTime())
		{
			bot.setReviveTime(0);
			bot.getPlayer().doRevive();
			bot.getPlayer().setRunning();
			// After revive the world state naturally drives the bot back to farm
			// (TeleportToFarmGoapAction will be selected on next replan).
			LOGGER.info("GoapAgent: " + bot.getPlayer().getName() + " revived");
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
			return; // all goals satisfied — nothing to do
		}

		final List<GoapAction> plan = GoapPlanner.plan(ws, goal, valid);
		if (plan.isEmpty())
		{
			LOGGER.warning("[GOAP] " + bot.getPlayer().getName() + " no plan for goal=" + goal.getName() + " ws=" + ws);
			return;
		}

		if (DEBUG)
		{
			final StringBuilder sb = new StringBuilder("[GOAP] ").append(bot.getPlayer().getName()).append(" PLAN [").append(goal.getName()).append("] ");
			for (GoapAction a : plan)
			{
				sb.append(a.getName()).append(' ');
			}
			LOGGER.info(sb.toString());
		}

		bot.setGoapPlan(plan);
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
	 *   <li>{@code UNDER_ATTACK} while doing a non-combat action — DefendGoal must engage.</li>
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

		if (ws.get(Fact.UNDER_ATTACK))
		{
			final GoapAction current = bot.getCurrentGoapAction();
			if (current != null)
			{
				final WorldState effects = current.getEffects();
				// Combat actions produce TARGET_DEAD or TARGET_IN_RANGE — no interrupt needed.
				if (!effects.isExplicitlySet(Fact.TARGET_DEAD) && !effects.isExplicitlySet(Fact.TARGET_IN_RANGE))
				{
					return true;
				}
			}
		}

		return false;
	}
}
