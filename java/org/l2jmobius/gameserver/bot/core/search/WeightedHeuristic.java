/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.search;

import java.util.logging.Logger;

/**
 * Weighted heuristic for A* search optimization.
 * <p>
 * Uses epsilon-weighted A*: f(n) = g(n) + w*h(n)
 * where w > 1 prioritizes exploration toward goal.
 * <p>
 * Trades optimality for speed when needed.
 */
public class WeightedHeuristic
{
	private static final Logger LOGGER = Logger.getLogger(WeightedHeuristic.class.getName());

	private static volatile double weight = 1.5; // Epsilon = 1.5
	private static volatile boolean enabled = true;

	private WeightedHeuristic()
	{
	}

	/**
	 * Calculates weighted f-value for A*.
	 *
	 * @param g cost from start
	 * @param h heuristic estimate to goal
	 * @return f-value: g + w*h
	 */
	public static double calculateFValue(double g, double h)
	{
		if (!enabled)
		{
			return g + h;
		}

		return g + (weight * h);
	}

	/**
	 * Sets weight factor.
	 * 
	 * w=1.0: optimal A*
	 * w>1.0: faster but may not find optimal
	 * w<1.0: worse, not recommended
	 *
	 * @param w weight factor (should be >= 1.0)
	 */
	public static void setWeight(double w)
	{
		weight = Math.max(1.0, w); // Enforce w >= 1.0
	}

	/** Get current weight. */
	public static double getWeight()
	{
		return weight;
	}

	/** Enable/disable weighting. */
	public static void setEnabled(boolean enable)
	{
		enabled = enable;
	}

	/**
	 * Adjusts weight based on bot count.
	 * More bots = higher weight = faster planning.
	 */
	public static void autoAdjustWeight(int activeBots)
	{
		if (activeBots < 20)
		{
			setWeight(1.0); // Optimal planning
		}
		else if (activeBots < 50)
		{
			setWeight(1.5); // Balanced
		}
		else if (activeBots < 100)
		{
			setWeight(2.0); // Fast
		}
		else
		{
			setWeight(3.0); // Very fast
		}
	}

	/** Get status. */
	public static String getStatus()
	{
		return String.format("WeightedHeuristic: enabled=%s, weight=%.1f", enabled, weight);
	}
}
