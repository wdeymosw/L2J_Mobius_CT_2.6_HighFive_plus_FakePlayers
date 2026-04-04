/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.logging;

import java.util.logging.Logger;

/**
 * Performance benchmarking suite for bot system.
 * <p>
 * Measures execution times for critical paths:
 * - GOAP planning (A* search)
 * - World state caching
 * - Action queue operations
 * - Object pooling efficiency
 */
public class PerformanceBenchmark
{
	private static final Logger LOGGER = Logger.getLogger(PerformanceBenchmark.class.getName());

	// Benchmark counters
	private static volatile long astarSearches = 0;
	private static volatile long astarTotalTime = 0;
	private static volatile long cacheLookups = 0;
	private static volatile long cacheTotalTime = 0;
	private static volatile long poolAcquires = 0;
	private static volatile long poolTotalTime = 0;
	private static volatile long dataStructureOps = 0;
	private static volatile long dataStructureTotalTime = 0;

	private PerformanceBenchmark()
	{
	}

	/** Record A* search benchmark. */
	public static void recordAstarSearch(long durationMs)
	{
		astarSearches++;
		astarTotalTime += durationMs;
	}

	/** Record cache lookup benchmark. */
	public static void recordCacheLookup(long durationMs)
	{
		cacheLookups++;
		cacheTotalTime += durationMs;
	}

	/** Record pool operation benchmark. */
	public static void recordPoolOperation(long durationMs)
	{
		poolAcquires++;
		poolTotalTime += durationMs;
	}

	/** Record data structure operation benchmark. */
	public static void recordDataStructureOp(long durationMs)
	{
		dataStructureOps++;
		dataStructureTotalTime += durationMs;
	}

	/** Get A* search statistics. */
	public static String getAstarStats()
	{
		if (astarSearches == 0)
			return "A* Search: No data";

		final double avgTime = (double) astarTotalTime / astarSearches;
		return String.format("A* Search: searches=%d, total=%dms, avg=%.2fms", astarSearches, astarTotalTime, avgTime);
	}

	/** Get cache statistics. */
	public static String getCacheStats()
	{
		if (cacheLookups == 0)
			return "Cache Lookup: No data";

		final double avgTime = (double) cacheTotalTime / cacheLookups;
		return String.format("Cache Lookup: lookups=%d, total=%dms, avg=%.3fms", cacheLookups, cacheTotalTime, avgTime);
	}

	/** Get pool statistics. */
	public static String getPoolStats()
	{
		if (poolAcquires == 0)
			return "Pool Operations: No data";

		final double avgTime = (double) poolTotalTime / poolAcquires;
		return String.format("Pool Operations: ops=%d, total=%dms, avg=%.3fms", poolAcquires, poolTotalTime, avgTime);
	}

	/** Get data structure statistics. */
	public static String getDataStructureStats()
	{
		if (dataStructureOps == 0)
			return "Data Structure Ops: No data";

		final double avgTime = (double) dataStructureTotalTime / dataStructureOps;
		return String.format("Data Structure Ops: ops=%d, total=%dms, avg=%.3fms", dataStructureOps, dataStructureTotalTime, avgTime);
	}

	/** Get full benchmark report. */
	public static String getFullReport()
	{
		final StringBuilder sb = new StringBuilder();
		sb.append("╔════════════════════════════════════════════╗\n");
		sb.append("║    PERFORMANCE BENCHMARK REPORT            ║\n");
		sb.append("╚════════════════════════════════════════════╝\n\n");

		sb.append(getAstarStats()).append("\n");
		sb.append(getCacheStats()).append("\n");
		sb.append(getPoolStats()).append("\n");
		sb.append(getDataStructureStats()).append("\n");

		return sb.toString();
	}

	/** Reset all benchmarks. */
	public static void reset()
	{
		astarSearches = 0;
		astarTotalTime = 0;
		cacheLookups = 0;
		cacheTotalTime = 0;
		poolAcquires = 0;
		poolTotalTime = 0;
		dataStructureOps = 0;
		dataStructureTotalTime = 0;
	}
}
