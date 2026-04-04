/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.goap;

import java.util.List;

/**
 * Selects the highest-priority unsatisfied goal for the current world state.
 * <p>
 * Goals are checked in priority order. A goal is skipped if its desired state
 * is already satisfied — there is nothing left to plan for it.
 */
public class GoalSelector
{
	private final List<GoapGoal> _goals;

	/**
	 * @param goals all available goals, will be evaluated in iteration order
	 */
	public GoalSelector(List<GoapGoal> goals)
	{
		_goals = goals;
	}

	/**
	 * Returns the highest-priority goal that is not yet satisfied,
	 * or {@code null} if all goals are already satisfied.
	 * <p>
	 * Goals returning priority 0 are considered inactive and are never selected.
	 *
	 * @param worldState current world state
	 * @return active goal, or null
	 */
	public GoapGoal select(WorldState worldState)
	{
		GoapGoal best = null;
		int bestPriority = 0; // goals with priority=0 are inactive — never selected

		for (GoapGoal goal : _goals)
		{
			// Skip goals whose desired state is already satisfied.
			if (worldState.satisfies(goal.getDesiredState()))
			{
				continue;
			}

			final int priority = goal.getPriority(worldState);
			if (priority > bestPriority)
			{
				bestPriority = priority;
				best = goal;
			}
		}

		return best;
	}
}
