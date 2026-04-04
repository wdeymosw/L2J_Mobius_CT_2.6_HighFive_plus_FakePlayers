/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.pool;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import org.l2jmobius.gameserver.bot.core.tuning.PerformanceConfig;
import org.l2jmobius.gameserver.bot.core.pool.PoolingMetrics;

/**
 * Cache pool for WorldState objects.
 * <p>
 * Reuses frequently created world state snapshots.
 */
public class WorldStatePoolImpl
{
	private static final Logger LOGGER = Logger.getLogger(WorldStatePoolImpl.class.getName());

	private static final Map<String, WorldStateEntry> pool = new HashMap<>();
	private static final int MAX_POOL_SIZE = 50;

	private WorldStatePoolImpl()
	{
	}

	/**
	 * Gets or creates a world state from pool.
	 *
	 * @param key world state identifier
	 * @return pooled or new world state
	 */
	public static Object acquireWorldState(String key)
	{
		synchronized (pool)
		{
			final WorldStateEntry entry = pool.get(key);

			if (entry != null && System.currentTimeMillis() - entry.timestamp < PerformanceConfig.getCacheExpirationMs())
			{
				entry.hits++;
				return entry.state;
			}

			// Create new
			final Object newState = new Object(); // Placeholder
			if (pool.size() < MAX_POOL_SIZE)
			{
				pool.put(key, new WorldStateEntry(newState, System.currentTimeMillis()));
			}

			PoolingMetrics.recordAcquisition(false);
			return newState;
		}
	}

	/**
	 * Returns world state to pool.
	 */
	public static void releaseWorldState(String key, Object state)
	{
		if (state != null)
		{
			PoolingMetrics.recordAcquisition(true);
		}
	}

	/** Get pool statistics. */
	public static String getStats()
	{
		synchronized (pool)
		{
			final long totalHits = pool.values().stream().mapToLong(e -> e.hits).sum();
			return String.format("WorldStatePool: size=%d, hits=%d", pool.size(), totalHits);
		}
	}

	/** Clear pool. */
	public static void clear()
	{
		synchronized (pool)
		{
			pool.clear();
		}
	}

	private static class WorldStateEntry
	{
		final Object state;
		final long timestamp;
		long hits = 0;

		WorldStateEntry(Object state, long timestamp)
		{
			this.state = state;
			this.timestamp = timestamp;
		}
	}
}
