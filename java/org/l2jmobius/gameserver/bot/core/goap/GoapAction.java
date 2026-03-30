/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.goap;

import org.l2jmobius.gameserver.bot.core.model.BotContext;
import org.l2jmobius.gameserver.bot.core.model.BotInstance;

/**
 * A strategic action in the GOAP plan.
 * <p>
 * Each action declares what world facts it requires ({@link #getPreconditions()})
 * and what it establishes ({@link #getEffects()}) when complete. The planner
 * uses these to build action chains automatically.
 * <p>
 * {@link #activate} populates the {@code BotExecutor} queue with one or more
 * {@link org.l2jmobius.gameserver.bot.core.action.BotAction} instances.
 * It does NOT execute directly — the executor drives them over subsequent ticks.
 */
public interface GoapAction
{
	/**
	 * Facts that must be true in the world for this action to be applicable.
	 * Used by the planner during backward-chaining search.
	 *
	 * @return precondition WorldState (partial)
	 */
	WorldState getPreconditions();

	/**
	 * Facts this action establishes when it completes.
	 * Used by the planner to match actions against goal requirements.
	 *
	 * @return effect WorldState (partial)
	 */
	WorldState getEffects();

	/**
	 * Planner cost — lower is preferred.
	 * May vary with world state (e.g. teleport is cheaper when far away).
	 *
	 * @param worldState current world state
	 * @return cost ≥ 0
	 */
	float getCost(WorldState worldState);

	/**
	 * Runtime check: can this action actually execute right now?
	 * Called before planning to prune unavailable actions.
	 * Example: DrinkPotionGoapAction returns false if no potion in inventory.
	 *
	 * @param ctx current perception snapshot
	 * @param bot the bot
	 * @return true if the action can be used in this tick's plan
	 */
	boolean isValid(BotContext ctx, BotInstance bot);

	/**
	 * Populates the bot's executor queue with BotActions needed for this step.
	 * Called by GoapAgent when this action becomes the current plan step.
	 * Must NOT execute game logic directly — only queue BotActions.
	 *
	 * @param bot the bot
	 * @param now current time in ms
	 */
	void activate(BotInstance bot, long now);

	/**
	 * Returns true when this action's task is done (effects achieved or timed out).
	 * GoapAgent polls this each tick to advance the plan.
	 *
	 * @param bot the bot
	 * @param ctx current perception snapshot
	 * @param now current time in ms
	 * @return true if complete
	 */
	boolean isComplete(BotInstance bot, BotContext ctx, long now);

	/** Human-readable name for logging. */
	String getName();
}
