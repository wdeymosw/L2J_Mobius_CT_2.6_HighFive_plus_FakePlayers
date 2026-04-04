/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.parallel;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Logger;

import org.l2jmobius.gameserver.bot.core.tuning.PerformanceConfig;

/**
 * Parallel GOAP planner.
 * <p>
 * Distributes planning tasks across multiple threads.
 * Enables simultaneous planning for multiple bots.
 */
public class ParallelPlanner
{
	private static final Logger LOGGER = Logger.getLogger(ParallelPlanner.class.getName());

	private static final ExecutorService executor = Executors.newFixedThreadPool(PerformanceConfig.getPlanningThreads());
	private static volatile boolean enabled = false;

	private ParallelPlanner()
	{
	}

	/**
	 * Submits planning task for execution on thread pool.
	 *
	 * @param task planning task
	 * @return future result
	 */
	public static Future<?> submitPlanningTask(Runnable task)
	{
		if (!enabled)
		{
			// Execute synchronously
			task.run();
			return null;
		}

		ParallelizationMetrics.recordPlanningTask();
		return executor.submit(task);
	}

	/** Enable parallel planning. */
	public static void setEnabled(boolean enable)
	{
		enabled = enable;
		if (enable)
		{
			LOGGER.info("Parallel planning enabled with " + PerformanceConfig.getPlanningThreads() + " threads");
		}
	}

	/** Check if enabled. */
	public static boolean isEnabled()
	{
		return enabled;
	}

	/** Get thread pool. */
	public static ExecutorService getExecutor()
	{
		return executor;
	}

	/** Shutdown planner. */
	public static void shutdown()
	{
		executor.shutdown();
	}

	/** Get status. */
	public static String getStatus()
	{
		return String.format("ParallelPlanner: enabled=%s, threads=%d", enabled, PerformanceConfig.getPlanningThreads());
	}
}
