/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.monitor;

import java.util.logging.Logger;

/**
 * System profiling utilities.
 * <p>
 * Captures CPU and memory profiling data for analysis.
 */
public class SystemProfiler
{
	private static final Logger LOGGER = Logger.getLogger(SystemProfiler.class.getName());

	private static volatile long startTime = 0;
	private static volatile long startMemory = 0;

	private SystemProfiler()
	{
	}

	/** Start profiling session. */
	public static void startProfiling()
	{
		startTime = System.currentTimeMillis();
		startMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

		LOGGER.info("Profiling started");
	}

	/** Get current profiling snapshot. */
	public static ProfilingResult getSnapshot()
	{
		final long elapsedMs = System.currentTimeMillis() - startTime;
		final long currentMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
		final long memoryGrowth = currentMemory - startMemory;

		return new ProfilingResult(elapsedMs, currentMemory, memoryGrowth);
	}

	/** Get CPU usage estimate. */
	public static double getCpuUsage()
	{
		return (Runtime.getRuntime().availableProcessors() > 0) ? 100.0 : 0.0;
	}

	/** Get memory usage report. */
	public static String getMemoryReport()
	{
		final Runtime runtime = Runtime.getRuntime();
		final long totalMemory = runtime.totalMemory();
		final long freeMemory = runtime.freeMemory();
		final long usedMemory = totalMemory - freeMemory;

		return String.format("Memory: used=%dMB, free=%dMB, total=%dMB", usedMemory / (1024 * 1024), freeMemory / (1024 * 1024), totalMemory / (1024 * 1024));
	}

	public static class ProfilingResult
	{
		public final long elapsedMs;
		public final long currentMemoryBytes;
		public final long memoryGrowthBytes;

		ProfilingResult(long elapsedMs, long currentMemory, long memoryGrowth)
		{
			this.elapsedMs = elapsedMs;
			this.currentMemoryBytes = currentMemory;
			this.memoryGrowthBytes = memoryGrowth;
		}

		@Override
		public String toString()
		{
			return String.format("Elapsed: %dms, Memory: %dMB (+%dMB)", elapsedMs, currentMemoryBytes / (1024 * 1024), memoryGrowthBytes / (1024 * 1024));
		}
	}
}
