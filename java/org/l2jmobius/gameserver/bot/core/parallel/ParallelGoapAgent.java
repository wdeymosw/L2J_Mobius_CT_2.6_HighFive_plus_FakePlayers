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
 * Parallel GOAP agent executor.
 * <p>
 * Runs multiple bot ticks in parallel on different threads.
 * Scales linearly with CPU cores up to thread pool size.
 */
public class ParallelGoapAgent
{
	private static final Logger LOGGER = Logger.getLogger(ParallelGoapAgent.class.getName());

	private static final ExecutorService executor = Executors.newFixedThreadPool(PerformanceConfig.getExecutorThreads());
	private static volatile boolean enabled = false;

	private ParallelGoapAgent()
	{
	}

	/**
	 * Submits agent tick for parallel execution.
	 *
	 * @param task tick task
	 * @return future result
	 */
	public static Future<?> submitAgentTick(Runnable task)
	{
		if (!enabled)
		{
			// Execute synchronously
			task.run();
			return null;
		}

		ParallelizationMetrics.recordExecutionTask();
		return executor.submit(task);
	}

	/** Enable parallel execution. */
	public static void setEnabled(boolean enable)
	{
		enabled = enable;
		if (enable)
		{
			LOGGER.info("Parallel GOAP agent enabled with " + PerformanceConfig.getExecutorThreads() + " executor threads");
		}
	}

	/** Check if enabled. */
	public static boolean isEnabled()
	{
		return enabled;
	}

	/** Get executor service. */
	public static ExecutorService getExecutor()
	{
		return executor;
	}

	/** Shutdown executor. */
	public static void shutdown()
	{
		executor.shutdown();
	}

	/** Get status. */
	public static String getStatus()
	{
		return String.format("ParallelGoapAgent: enabled=%s, threads=%d", enabled, PerformanceConfig.getExecutorThreads());
	}
}
