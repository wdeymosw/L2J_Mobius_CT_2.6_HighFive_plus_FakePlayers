/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.parallel;

import java.util.logging.Logger;

import org.l2jmobius.gameserver.bot.core.tuning.PerformanceConfig;

/**
 * Parallelism tuning for bot executor threads.
 * <p>
 * Automatically determines optimal thread count based on CPU cores and load.
 */
public class ParallelismTuning
{
	private static final Logger LOGGER = Logger.getLogger(ParallelismTuning.class.getName());

	private ParallelismTuning()
	{
	}

	/**
	 * Recommends thread count based on CPU cores.
	 * <p>
	 * Heuristic: N = (CPU_cores - 1) for executor threads
	 *
	 * @return recommended thread count
	 */
	public static int recommendThreadCount()
	{
		final int cpus = Runtime.getRuntime().availableProcessors();
		return Math.max(2, cpus - 1);
	}

	/**
	 * Tunes parallelism settings for bot count.
	 *
	 * @param botCount number of active bots
	 */
	public static void tuneForBotCount(int botCount)
	{
		if (botCount < 10)
		{
			// Single-threaded is fine
			PerformanceConfig.setExecutorThreads(1);
			ParallelPlanner.setEnabled(false);
		}
		else if (botCount < 50)
		{
			// 2-4 threads
			PerformanceConfig.setExecutorThreads(2);
			ParallelPlanner.setEnabled(false);
		}
		else if (botCount < 100)
		{
			// 4-8 threads
			final int threads = Math.min(4, Runtime.getRuntime().availableProcessors());
			PerformanceConfig.setExecutorThreads(threads);
			ParallelPlanner.setEnabled(true);
		}
		else
		{
			// Fully parallel
			PerformanceConfig.setExecutorThreads(recommendThreadCount());
			PerformanceConfig.setPlanningThreads(recommendThreadCount());
			ParallelPlanner.setEnabled(true);
			ParallelGoapAgent.setEnabled(true);
		}

		LOGGER.info("Tuned for " + botCount + " bots: " + PerformanceConfig.getExecutorThreads() + " executor threads");
	}

	/** Get tuning status. */
	public static String getStatus()
	{
		return String.format("Parallelism: CPU_cores=%d, executor=%d, planning=%d", Runtime.getRuntime().availableProcessors(), PerformanceConfig.getExecutorThreads(), PerformanceConfig.getPlanningThreads());
	}
}
