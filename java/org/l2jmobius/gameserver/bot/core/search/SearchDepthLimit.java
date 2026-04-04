/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.search;

/**
 * GOAP search depth limit configuration and utilities.
 * <p>
 * Prevents infinite loops in GOAP planner by limiting backward-chaining depth.
 * <p>
 * Search depth = how many actions back from goal can planner go.
 * Example: goal_depth=3 means plan can have max 3 actions.
 */
public class SearchDepthLimit
{
	/** Default maximum search depth. Prevents long chains that cause CPU spikes. */
	public static final int DEFAULT_MAX_DEPTH = 10;

	/** Minimum allowed depth (must allow at least 1 action). */
	public static final int MIN_DEPTH = 1;

	/** Maximum allowed depth (prevent runaway searches). */
	public static final int MAX_DEPTH = 50;

	/** Current global search depth limit. */
	private static volatile int currentDepthLimit = DEFAULT_MAX_DEPTH;

	private SearchDepthLimit()
	{
	}

	/**
	 * Sets the maximum search depth for GOAP planning.
	 * <p>
	 * Higher = longer plans possible but slower planning.
	 * Lower = faster planning but may miss optimal solutions.
	 *
	 * @param depth depth limit (will be clamped to MIN_DEPTH - MAX_DEPTH)
	 */
	public static void setMaxDepth(int depth)
	{
		if (depth < MIN_DEPTH)
		{
			currentDepthLimit = MIN_DEPTH;
		}
		else if (depth > MAX_DEPTH)
		{
			currentDepthLimit = MAX_DEPTH;
		}
		else
		{
			currentDepthLimit = depth;
		}
	}

	/** Get current maximum search depth. */
	public static int getMaxDepth()
	{
		return currentDepthLimit;
	}

	/** Reset to default depth. */
	public static void reset()
	{
		currentDepthLimit = DEFAULT_MAX_DEPTH;
	}

	/**
	 * Returns recommended depth based on system load.
	 * <p>
	 * Auto-tune: reduce depth during high load to keep planning fast.
	 *
	 * @param botCount current number of active bots
	 * @param maxBotsBeforeLimitReduction number of bots before reducing depth
	 * @return recommended depth
	 */
	public static int getRecommendedDepth(int botCount, int maxBotsBeforeLimitReduction)
	{
		if (botCount > maxBotsBeforeLimitReduction)
		{
			// High load: reduce depth by 25% per threshold exceeded
			final int reduction = (botCount / maxBotsBeforeLimitReduction) - 1;
			final int reduced = Math.max(MIN_DEPTH, DEFAULT_MAX_DEPTH - (reduction * 2));
			return Math.min(reduced, MAX_DEPTH);
		}

		return DEFAULT_MAX_DEPTH;
	}

	/**
	 * Configuration object for depth limiting.
	 */
	public static class Config
	{
		public final int maxDepth;
		public final boolean enableAutoTuning;
		public final int autoTuneThreshold;

		public Config(int maxDepth, boolean enableAutoTuning, int autoTuneThreshold)
		{
			this.maxDepth = Math.max(MIN_DEPTH, Math.min(maxDepth, MAX_DEPTH));
			this.enableAutoTuning = enableAutoTuning;
			this.autoTuneThreshold = autoTuneThreshold;
		}
	}
}
