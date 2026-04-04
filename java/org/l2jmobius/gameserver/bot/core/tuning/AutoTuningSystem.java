/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.tuning;

import java.util.logging.Logger;

import org.l2jmobius.gameserver.bot.core.logging.BotMetrics;
import org.l2jmobius.gameserver.bot.core.logging.StructuredBotLogger;
import org.l2jmobius.gameserver.bot.core.search.SearchDepthLimit;

/**
 * Automatic tuning system for bot performance.
 * <p>
 * Dynamically adjusts timeout, depth, and planning frequency based on:
 * - Number of active bots
 * - Exception rates
 * - Action completion rates
 */
public class AutoTuningSystem
{
	private static final Logger LOGGER = Logger.getLogger(AutoTuningSystem.class.getName());

	private static volatile int targetBotCount = 50;
	private static volatile boolean autoTuneEnabled = true;
	private static long lastTuneTime = 0;
	private static final long TUNE_INTERVAL_MS = 10000; // Retune every 10 seconds

	private AutoTuningSystem()
	{
	}

	/**
	 * Runs automatic tuning based on current system state.
	 * <p>
	 * Should be called periodically (e.g., every 10 seconds) by scheduler.
	 */
	public static void tune()
	{
		if (!autoTuneEnabled)
		{
			return;
		}

		final long now = System.currentTimeMillis();
		if (now - lastTuneTime < TUNE_INTERVAL_MS)
		{
			return;
		}

		lastTuneTime = now;

		// Get current metrics
		final long actionCount = BotMetrics.getActionExecutedCount();
		final long exceptionCount = BotMetrics.getExceptionRecoverableCount() + BotMetrics.getExceptionValidationCount() + BotMetrics.getExceptionFatalCount();
		final long timeoutCount = BotMetrics.getActionTimeoutCount();

		// Estimate active bots from action rate
		final long estimatedBots = actionCount / 10; // Rough estimate

		// Adjust search depth based on bot count
		if (estimatedBots > targetBotCount)
		{
			final int reducedDepth = SearchDepthLimit.getRecommendedDepth((int) estimatedBots, targetBotCount);
			SearchDepthLimit.setMaxDepth(reducedDepth);
			StructuredBotLogger.finest(null, "AUTO_TUNING", "depth", reducedDepth, "bot_count", estimatedBots);
		}

		// Adjust timeout if timeout rate is high
		if (timeoutCount > (exceptionCount / 10))
		{
			// Too many timeouts: increase timeout duration slightly
			LOGGER.fine("Auto-tuning: Timeout rate high, consider increasing action timeout");
		}

		// Adjust based on exception rates
		if (exceptionCount > 0)
		{
			final double exceptionRate = exceptionCount / Math.max(1.0, actionCount);
			if (exceptionRate > 0.1) // > 10% exceptions
			{
				LOGGER.warning("High exception rate: " + (exceptionRate * 100) + "%");
			}
		}
	}

	/**
	 * Sets target bot count for auto-tuning threshold.
	 */
	public static void setTargetBotCount(int count)
	{
		targetBotCount = Math.max(1, count);
	}

	/** Enable or disable auto-tuning. */
	public static void setEnabled(boolean enabled)
	{
		autoTuneEnabled = enabled;
	}

	/** Check if auto-tuning is active. */
	public static boolean isEnabled()
	{
		return autoTuneEnabled;
	}

	/** Get current tuning parameters. */
	public static String getStatus()
	{
		return String.format("AutoTuning: enabled=%s, targetBots=%d, searchDepth=%d", autoTuneEnabled, targetBotCount, SearchDepthLimit.getMaxDepth());
	}
}
