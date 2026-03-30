/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.goap;

/**
 * Declares what the bot wants to achieve.
 * <p>
 * A goal is satisfied when the current {@link WorldState} satisfies
 * the desired state returned by {@link #getDesiredState()}.
 * The planner ignores satisfied goals.
 */
public interface GoapGoal
{
	/**
	 * The partial world state this goal wants to achieve.
	 * Only facts explicitly set here are checked by the planner.
	 *
	 * @return desired WorldState (partial)
	 */
	WorldState getDesiredState();

	/**
	 * Dynamic priority based on current world state.
	 * Higher = more urgent. Called each tick by GoalSelector.
	 *
	 * @param worldState current world state
	 * @return priority (0 = lowest)
	 */
	int getPriority(WorldState worldState);

	/** Human-readable name for logging. */
	String getName();
}
