/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.pool;

import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * Object pool efficiency metrics.
 * <p>
 * Tracks performance of all object pools:
 * - Reuse rate
 * - GC pressure reduction
 * - Pool saturation
 */
public class PoolingMetrics
{
	private static final Logger LOGGER = Logger.getLogger(PoolingMetrics.class.getName());

	private static final AtomicLong totalAcquisitions = new AtomicLong(0);
	private static final AtomicLong pooledReuses = new AtomicLong(0);
	private static final AtomicLong newCreations = new AtomicLong(0);

	private PoolingMetrics()
	{
	}

	/** Record object acquisition from pool. */
	public static void recordAcquisition(boolean fromPool)
	{
		totalAcquisitions.incrementAndGet();
		if (fromPool)
		{
			pooledReuses.incrementAndGet();
		}
		else
		{
			newCreations.incrementAndGet();
		}
	}

	/** Get reuse rate. */
	public static double getReuseRate()
	{
		final long total = totalAcquisitions.get();
		if (total == 0)
		{
			return 0.0;
		}

		return (pooledReuses.get() * 100.0) / total;
	}

	/** Get GC pressure reduction (estimated). */
	public static long getGcPressureReduction()
	{
		// Assume each pooled reuse = 1 object not GC'd
		return pooledReuses.get();
	}

	/** Get pooling status. */
	public static String getStatus()
	{
		final long total = totalAcquisitions.get();
		if (total == 0)
		{
			return "Pooling: No data";
		}

		final double reuseRate = getReuseRate();
		return String.format("Pooling: acquisitions=%d, reuses=%d, new=%d, rate=%.1f%%", total, pooledReuses.get(), newCreations.get(), reuseRate);
	}

	/** Reset metrics. */
	public static void reset()
	{
		totalAcquisitions.set(0);
		pooledReuses.set(0);
		newCreations.set(0);
	}
}
