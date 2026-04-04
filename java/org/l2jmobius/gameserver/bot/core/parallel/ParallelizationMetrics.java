/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.parallel;

import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

import org.l2jmobius.gameserver.bot.core.tuning.PerformanceConfig;

/**
 * Parallelization metrics and configuration.
 * <p>
 * Tracks parallel task execution:
 * - Planning threads
 * - Action executor threads
 * - Load distribution
 */
public class ParallelizationMetrics
{
	private static final Logger LOGGER = Logger.getLogger(ParallelizationMetrics.class.getName());

	private static final AtomicLong parallelPlanningTasks = new AtomicLong(0);
	private static final AtomicLong parallelExecutionTasks = new AtomicLong(0);
	private static final AtomicLong taskQueueDepth = new AtomicLong(0);

	private ParallelizationMetrics()
	{
	}

	/** Record parallel planning task. */
	public static void recordPlanningTask()
	{
		parallelPlanningTasks.incrementAndGet();
	}

	/** Record parallel execution task. */
	public static void recordExecutionTask()
	{
		parallelExecutionTasks.incrementAndGet();
	}

	/** Set current queue depth. */
	public static void setQueueDepth(long depth)
	{
		taskQueueDepth.set(depth);
	}

	/** Get planning task count. */
	public static long getPlanningTaskCount()
	{
		return parallelPlanningTasks.get();
	}

	/** Get execution task count. */
	public static long getExecutionTaskCount()
	{
		return parallelExecutionTasks.get();
	}

	/** Get current queue depth. */
	public static long getQueueDepth()
	{
		return taskQueueDepth.get();
	}

	/** Get recommended thread count based on CPU cores. */
	public static int getRecommendedThreadCount()
	{
		final int cpuCount = Runtime.getRuntime().availableProcessors();
		return Math.max(2, cpuCount - 1); // Leave one core free
	}

	/** Get parallelization status. */
	public static String getStatus()
	{
		return String.format("Parallelization: planning_tasks=%d, execution_tasks=%d, queue_depth=%d, threads=%d", parallelPlanningTasks.get(), parallelExecutionTasks.get(), taskQueueDepth.get(), PerformanceConfig.getPlanningThreads());
	}

	/** Reset metrics. */
	public static void reset()
	{
		parallelPlanningTasks.set(0);
		parallelExecutionTasks.set(0);
		taskQueueDepth.set(0);
	}
}
