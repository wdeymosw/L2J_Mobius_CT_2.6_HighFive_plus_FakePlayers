/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.search;

import java.util.logging.Logger;
import org.l2jmobius.gameserver.bot.core.tuning.PerformanceConfig;

/**
 * Early termination for GOAP search.
 * <p>
 * Stops A* search early if:
 * - Goal found (obviously)
 * - Cost exceeds threshold
 * - Time limit exceeded
 * - Search depth limit reached
 * <p>
 * Reduces CPU usage for non-critical searches.
 */
public class EarlyTerminationPolicy
{
	private static final Logger LOGGER = Logger.getLogger(EarlyTerminationPolicy.class.getName());

	private static volatile long maxSearchTimeMs = PerformanceConfig.getPlanTimeoutMs();
	private static volatile int maxSearchDepth = PerformanceConfig.getMaxSearchDepth();
	private static volatile double maxCostThreshold = 1000.0;
	private static volatile boolean enabled = true;

	private EarlyTerminationPolicy()
	{
	}

	/**
	 * Checks if search should terminate early.
	 *
	 * @param elapsedMs elapsed search time
	 * @param currentDepth current search depth
	 * @param currentCost current cost
	 * @return true if should terminate
	 */
	public static boolean shouldTerminate(long elapsedMs, int currentDepth, double currentCost)
	{
		if (!enabled)
		{
			return false;
		}

		// Time limit exceeded
		if (elapsedMs > maxSearchTimeMs)
		{
			return true;
		}

		// Depth limit exceeded
		if (currentDepth > maxSearchDepth)
		{
			return true;
		}

		// Cost threshold exceeded
		if (currentCost > maxCostThreshold)
		{
			return true;
		}

		return false;
	}

	/** Set maximum search time. */
	public static void setMaxSearchTimeMs(long ms)
	{
		maxSearchTimeMs = Math.max(100, ms);
	}

	/** Set maximum search depth. */
	public static void setMaxSearchDepth(int depth)
	{
		maxSearchDepth = Math.max(1, depth);
	}

	/** Set cost threshold. */
	public static void setMaxCostThreshold(double threshold)
	{
		maxCostThreshold = Math.max(0.1, threshold);
	}

	/** Enable/disable early termination. */
	public static void setEnabled(boolean enable)
	{
		enabled = enable;
	}

	/** Get status. */
	public static String getStatus()
	{
		return String.format("EarlyTermination: enabled=%s, maxTime=%dms, maxDepth=%d, maxCost=%.1f", enabled, maxSearchTimeMs, maxSearchDepth, maxCostThreshold);
	}
}
