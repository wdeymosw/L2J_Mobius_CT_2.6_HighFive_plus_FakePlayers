/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.monitor;

import java.util.logging.Logger;

import org.l2jmobius.gameserver.bot.core.logging.PerformanceBenchmark;
import org.l2jmobius.gameserver.bot.core.logging.BotMetrics;

/**
 * Performance test infrastructure.
 * <p>
 * Provides utilities for benchmarking bot system under various loads.
 */
public class PerformanceTestRunner
{
	private static final Logger LOGGER = Logger.getLogger(PerformanceTestRunner.class.getName());

	private PerformanceTestRunner()
	{
	}

	/**
	 * Runs a performance test with specified bot count.
	 *
	 * @param botCount number of bots to simulate
	 * @param durationMs test duration
	 * @return test results
	 */
	public static PerformanceTestResult runTest(int botCount, long durationMs)
	{
		LOGGER.info("Starting performance test: " + botCount + " bots for " + durationMs + "ms");

		SystemProfiler.startProfiling();
		PerformanceBenchmark.reset();
		BotMetrics.resetAll();

		try
		{
			Thread.sleep(durationMs);
		}
		catch (InterruptedException e)
		{
			Thread.currentThread().interrupt();
		}

		final SystemProfiler.ProfilingResult profiling = SystemProfiler.getSnapshot();

		return new PerformanceTestResult(botCount, durationMs, profiling);
	}

	public static class PerformanceTestResult
	{
		public final int botCount;
		public final long durationMs;
		public final SystemProfiler.ProfilingResult profiling;

		PerformanceTestResult(int botCount, long durationMs, SystemProfiler.ProfilingResult profiling)
		{
			this.botCount = botCount;
			this.durationMs = durationMs;
			this.profiling = profiling;
		}

		@Override
		public String toString()
		{
			return String.format("Test(%d bots): %dms - %s", botCount, durationMs, profiling);
		}
	}
}
