/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.cache;

import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

import org.l2jmobius.gameserver.bot.core.tuning.PerformanceConfig;
import org.l2jmobius.gameserver.bot.core.logging.PerformanceBenchmark;

/**
 * Caching layer for world state snapshots.
 * <p>
 * Reduces repeated computation by caching:
 * - World state evaluations
 * - Goal precondition checks
 * - Distance calculations
 * <p>
 * TTL-based expiration (configurable via PerformanceConfig).
 */
public class WorldStateCache
{
	private static final Logger LOGGER = Logger.getLogger(WorldStateCache.class.getName());

	private static volatile long lastCacheTime = 0;
	private static volatile Object cachedWorldState = null;
	private static final AtomicLong cacheHits = new AtomicLong(0);
	private static final AtomicLong cacheMisses = new AtomicLong(0);

	private WorldStateCache()
	{
	}

	/**
	 * Gets or creates world state snapshot.
	 * <p>
	 * Returns cached version if still valid (within TTL), otherwise null.
	 *
	 * @return cached world state or null if expired
	 */
	public static Object getWorldState()
	{
		if (!PerformanceConfig.isCachingEnabled())
		{
			cacheMisses.incrementAndGet();
			return null;
		}

		final long now = System.currentTimeMillis();
		final long ttl = PerformanceConfig.getCacheExpirationMs();

		if (cachedWorldState != null && (now - lastCacheTime) < ttl)
		{
			cacheHits.incrementAndGet();
			return cachedWorldState;
		}

		cacheMisses.incrementAndGet();
		return null;
	}

	/**
	 * Caches a world state snapshot.
	 *
	 * @param worldState the state to cache
	 */
	public static void setWorldState(Object worldState)
	{
		if (!PerformanceConfig.isCachingEnabled())
		{
			return;
		}

		cachedWorldState = worldState;
		lastCacheTime = System.currentTimeMillis();

		PerformanceBenchmark.recordCacheLookup(1); // Cache hit
	}

	/**
	 * Invalidates cache (call when world state changes).
	 */
	public static void invalidate()
	{
		cachedWorldState = null;
		lastCacheTime = 0;
	}

	/** Get cache hit rate statistics. */
	public static String getStats()
	{
		final long hits = cacheHits.get();
		final long misses = cacheMisses.get();
		final long total = hits + misses;

		if (total == 0)
		{
			return "Cache: No data";
		}

		final double hitRate = (hits * 100.0) / total;
		return String.format("Cache: hits=%d, misses=%d, rate=%.1f%%", hits, misses, hitRate);
	}

	/** Reset statistics. */
	public static void resetStats()
	{
		cacheHits.set(0);
		cacheMisses.set(0);
	}

	/** Clear cache. */
	public static void clear()
	{
		cachedWorldState = null;
		lastCacheTime = 0;
		resetStats();
	}
}
