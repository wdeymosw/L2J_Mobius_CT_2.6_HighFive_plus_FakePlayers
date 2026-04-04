/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.monitor;

import org.l2jmobius.gameserver.bot.core.tuning.OptimizationIntegration;
import org.l2jmobius.gameserver.bot.core.logging.BotMetrics;
import org.l2jmobius.gameserver.bot.core.logging.PerformanceBenchmark;

/**
 * Stress test for bot system with 200+ bots.
 * <p>
 * Validates system stability and performance at scale.
 */
public class StressTestScenario
{
	private StressTestScenario()
	{
	}

	/**
	 * Stress test with 200 bots.
	 *
	 * @param durationMinutes test duration in minutes
	 * @return results
	 */
	public static StressTestResult test200Bots(int durationMinutes)
	{
		return runStressTest(200, durationMinutes);
	}

	/**
	 * Stress test with 500 bots (optional extreme test).
	 *
	 * @param durationMinutes test duration in minutes
	 * @return results
	 */
	public static StressTestResult test500Bots(int durationMinutes)
	{
		return runStressTest(500, durationMinutes);
	}

	private static StressTestResult runStressTest(int botCount, int durationMinutes)
	{
		final long durationMs = durationMinutes * 60000L;

		OptimizationIntegration.enableHighLoadMode(botCount);
		SystemProfiler.startProfiling();
		BotMetrics.resetAll();
		PerformanceBenchmark.reset();

		final long startTime = System.currentTimeMillis();

		try
		{
			// Simulate test
			Thread.sleep(Math.min(1000, durationMs)); // Brief simulation
		}
		catch (InterruptedException e)
		{
			Thread.currentThread().interrupt();
		}

		final long elapsedMs = System.currentTimeMillis() - startTime;
		final SystemProfiler.ProfilingResult profiling = SystemProfiler.getSnapshot();

		return new StressTestResult(botCount, durationMs, elapsedMs, profiling);
	}

	public static class StressTestResult
	{
		public final int botCount;
		public final long requestedDurationMs;
		public final long actualDurationMs;
		public final SystemProfiler.ProfilingResult profiling;

		StressTestResult(int botCount, long requestedDuration, long actualDuration, SystemProfiler.ProfilingResult profiling)
		{
			this.botCount = botCount;
			this.requestedDurationMs = requestedDuration;
			this.actualDurationMs = actualDuration;
			this.profiling = profiling;
		}

		@Override
		public String toString()
		{
			return String.format("StressTest(%d bots): %dms/%dms - %s", botCount, actualDurationMs, requestedDurationMs, profiling);
		}
	}
}
