/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.goap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Backward-chaining A* planner.
 * <p>
 * Starts from the desired goal state and searches backward through action chains
 * until the current world state satisfies all remaining requirements.
 * <p>
 * With ~10 actions and ~25 facts the search space is tiny.
 * {@code MAX_NODES = 128} is a safety cap against degenerate cycles.
 */
public class GoapPlanner
{
	private static final int MAX_NODES = 128;

	private GoapPlanner()
	{
	}

	/**
	 * Finds the cheapest sequence of actions that transforms {@code currentState}
	 * into a state satisfying {@code goal.getDesiredState()}.
	 *
	 * @param currentState    live world state this tick
	 * @param goal            the goal to achieve
	 * @param availableActions actions whose {@code isValid()} returned true
	 * @return ordered plan (first action to execute first), or empty list if no plan found
	 */
	public static List<GoapAction> plan(WorldState currentState, GoapGoal goal, List<GoapAction> availableActions)
	{
		final PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingDouble(n -> n.cost));
		open.add(new Node(goal.getDesiredState(), Collections.emptyList(), 0f));

		int iterations = 0;
		while (!open.isEmpty() && (iterations++ < MAX_NODES))
		{
			final Node current = open.poll();

			// Base case: current world already satisfies all remaining needs.
			if (currentState.satisfies(current.neededState))
			{
				return current.plan;
			}

			for (GoapAction action : availableActions)
			{
				// Skip actions that don't contribute toward what's still needed.
				if (!WorldState.contributesTo(action.getEffects(), current.neededState))
				{
					continue;
				}

				// Regression: remove satisfied needs, add action's preconditions.
				final WorldState newNeed = regress(current.neededState, action);

				final List<GoapAction> newPlan = new ArrayList<>(current.plan.size() + 1);
				newPlan.add(action); // this action runs first
				newPlan.addAll(current.plan);

				open.add(new Node(newNeed, newPlan, current.cost + action.getCost(currentState)));
			}
		}

		return Collections.emptyList(); // no plan found
	}

	/**
	 * Regression step: given what's still needed and an action that partially satisfies it,
	 * return a new needed state = (unsatisfied needs from before) + (action's preconditions).
	 * <p>
	 * Facts already covered by this action's effects are dropped from the needed set.
	 * The action's preconditions become new requirements.
	 *
	 * @param neededState what still needs to be true
	 * @param action      the action being considered
	 * @return new needed state after accounting for this action
	 */
	private static WorldState regress(WorldState neededState, GoapAction action)
	{
		final WorldState result = new WorldState();
		final WorldState effects = action.getEffects();
		final WorldState preconditions = action.getPreconditions();

		// Carry forward needs that this action does NOT satisfy.
		for (Fact f : Fact.values())
		{
			if (neededState.isExplicitlySet(f) && !(effects.isExplicitlySet(f) && (effects.get(f) == neededState.get(f))))
			{
				result.set(f, neededState.get(f));
			}
		}

		// Add the action's preconditions as new requirements.
		for (Fact f : Fact.values())
		{
			if (preconditions.isExplicitlySet(f))
			{
				result.set(f, preconditions.get(f));
			}
		}

		return result;
	}

	// -------------------------------------------------------------------------

	private static class Node
	{
		final WorldState neededState;
		final List<GoapAction> plan;
		final float cost;

		Node(WorldState neededState, List<GoapAction> plan, float cost)
		{
			this.neededState = neededState;
			this.plan = plan;
			this.cost = cost;
		}
	}
}
