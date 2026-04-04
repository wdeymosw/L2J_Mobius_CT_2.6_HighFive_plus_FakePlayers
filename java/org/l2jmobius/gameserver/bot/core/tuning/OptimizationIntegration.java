/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.tuning;

import java.util.logging.Logger;

import org.l2jmobius.gameserver.bot.core.cache.PlanCache;
import org.l2jmobius.gameserver.bot.core.cache.ValidationCache;
import org.l2jmobius.gameserver.bot.core.cache.WorldStateCache;
import org.l2jmobius.gameserver.bot.core.parallel.ParallelGoapAgent;
import org.l2jmobius.gameserver.bot.core.parallel.ParallelPlanner;
import org.l2jmobius.gameserver.bot.core.pool.BotContextPool;
import org.l2jmobius.gameserver.bot.core.pool.ListPool;
import org.l2jmobius.gameserver.bot.core.pool.PoolingMetrics;
import org.l2jmobius.gameserver.bot.core.pool.WorldStatePool;
import org.l2jmobius.gameserver.bot.core.search.EarlyTerminationPolicy;
import org.l2jmobius.gameserver.bot.core.search.SearchDepthLimit;
import org.l2jmobius.gameserver.bot.core.search.WeightedHeuristic;
import org.l2jmobius.gameserver.bot.core.parallel.ParallelizationMetrics;

/**
 * Integration point for all performance optimizations.
 * <p>
 * Enables/disables optimization features based on configuration.
 */
public class OptimizationIntegration
{
	private static final Logger LOGGER = Logger.getLogger(OptimizationIntegration.class.getName());

	private OptimizationIntegration()
	{
	}

	/**
	 * Initialize all optimizations with default settings.
	 */
	public static void initializeDefaults()
	{
		LOGGER.info("Initializing bot system optimizations...");

		// Caching
		WorldStateCache.clear();
		PlanCache.cleanup();
		ValidationCache.invalidateAll();

		// Pooling
		ListPool.acquireList();
		WorldStatePool.acquireObject();

		// Search optimizations
		SearchDepthLimit.reset();
		EarlyTerminationPolicy.setEnabled(true);
		WeightedHeuristic.setEnabled(true);
		WeightedHeuristic.setWeight(1.5);

		// Parallel execution
		ParallelPlanner.setEnabled(false); // Off by default, enable when needed
		ParallelGoapAgent.setEnabled(false);

		// Auto-tuning
		AutoTuningSystem.setEnabled(true);
		AutoTuningSystem.setTargetBotCount(50);

		LOGGER.info("Bot system optimizations initialized");
	}

	/**
	 * Enable all aggressive optimizations for high load.
	 * Reduces planning quality but maximizes throughput.
	 */
	public static void enableHighLoadMode(int estimatedBots)
	{
		LOGGER.info("Enabling high-load mode for " + estimatedBots + " bots");

		// Aggressive caching
		PerformanceConfig.setCachingEnabled(true);
		PerformanceConfig.setCacheExpirationMs(500); // Lower TTL

		// Reduce search depth
		SearchDepthLimit.setMaxDepth(SearchDepthLimit.getRecommendedDepth(estimatedBots, 50));
		EarlyTerminationPolicy.setMaxSearchTimeMs(2000); // 2 second max

		// Aggressive weighting
		WeightedHeuristic.autoAdjustWeight(estimatedBots);

		// Enable parallelization
		if (estimatedBots > 50)
		{
			ParallelPlanner.setEnabled(true);
			ParallelGoapAgent.setEnabled(true);
		}
	}

	/**
	 * Get comprehensive optimization status report.
	 */
	public static String getOptimizationStatus()
	{
		final StringBuilder sb = new StringBuilder();

		sb.append("╔════════════════════════════════════════════════════════════╗\n");
		sb.append("║          OPTIMIZATION STATUS REPORT                        ║\n");
		sb.append("╚════════════════════════════════════════════════════════════╝\n\n");

		sb.append("CACHING\n");
		sb.append("  Enabled: ").append(PerformanceConfig.isCachingEnabled()).append("\n");
		sb.append("  TTL: ").append(PerformanceConfig.getCacheExpirationMs()).append("ms\n");
		sb.append("  ").append(WorldStateCache.getStats()).append("\n");
		sb.append("  ").append(PlanCache.getStats()).append("\n\n");

		sb.append("POOLING\n");
		sb.append("  ").append(PoolingMetrics.getStatus()).append("\n");
		sb.append("  ").append(WorldStatePool.getPoolStatus()).append("\n\n");

		sb.append("SEARCH OPTIMIZATION\n");
		sb.append("  Max Depth: ").append(SearchDepthLimit.getMaxDepth()).append("\n");
		sb.append("  ").append(EarlyTerminationPolicy.getStatus()).append("\n");
		sb.append("  ").append(WeightedHeuristic.getStatus()).append("\n\n");

		sb.append("PARALLELIZATION\n");
		sb.append("  ").append(ParallelPlanner.getStatus()).append("\n");
		sb.append("  ").append(ParallelGoapAgent.getStatus()).append("\n");
		sb.append("  ").append(ParallelizationMetrics.getStatus()).append("\n\n");

		sb.append("AUTO-TUNING\n");
		sb.append("  ").append(AutoTuningSystem.getStatus()).append("\n");

		return sb.toString();
	}
}
