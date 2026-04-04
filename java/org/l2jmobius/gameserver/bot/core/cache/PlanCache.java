/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.cache;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import org.l2jmobius.gameserver.bot.core.tuning.PerformanceConfig;

/**
 * Caches GOAP plans to avoid replanning identical world states.
 * <p>
 * Key: world state hash
 * Value: cached plan (list of actions)
 * <p>
 * TTL-based expiration with hit/miss tracking.
 */
public class PlanCache
{
	private static final Logger LOGGER = Logger.getLogger(PlanCache.class.getName());

	private static final Map<Integer, CachedPlan> cache = new HashMap<>();
	private static volatile long lastCleanup = 0;
	private static final long CLEANUP_INTERVAL_MS = 60000; // 1 minute

	private PlanCache()
	{
	}

	/**
	 * Gets cached plan for world state.
	 *
	 * @param worldStateHash hash of world state
	 * @return cached plan or null if expired/missing
	 */
	public static Object getPlan(int worldStateHash)
	{
		if (!PerformanceConfig.isCachingEnabled())
		{
			return null;
		}

		synchronized (cache)
		{
			final CachedPlan cached = cache.get(worldStateHash);

			if (cached == null)
			{
				return null;
			}

			if (System.currentTimeMillis() - cached.timestamp > PerformanceConfig.getCacheExpirationMs())
			{
				cache.remove(worldStateHash);
				return null;
			}

			cached.hits++;
			return cached.plan;
		}
	}

	/**
	 * Caches a plan.
	 *
	 * @param worldStateHash hash of world state
	 * @param plan the plan to cache
	 */
	public static void setPlan(int worldStateHash, Object plan)
	{
		if (!PerformanceConfig.isCachingEnabled() || plan == null)
		{
			return;
		}

		synchronized (cache)
		{
			cache.put(worldStateHash, new CachedPlan(plan, System.currentTimeMillis()));
		}
	}

	/** Invalidate all cached plans. */
	public static void invalidateAll()
	{
		synchronized (cache)
		{
			cache.clear();
		}
	}

	/** Periodic cleanup of expired entries. */
	public static void cleanup()
	{
		final long now = System.currentTimeMillis();
		if (now - lastCleanup < CLEANUP_INTERVAL_MS)
		{
			return;
		}

		lastCleanup = now;

		synchronized (cache)
		{
			cache.entrySet().removeIf(e -> now - e.getValue().timestamp > PerformanceConfig.getCacheExpirationMs());
		}
	}

	/** Get cache statistics. */
	public static String getStats()
	{
		synchronized (cache)
		{
			final long totalHits = cache.values().stream().mapToLong(c -> c.hits).sum();
			return String.format("PlanCache: size=%d, hits=%d", cache.size(), totalHits);
		}
	}

	private static class CachedPlan
	{
		final Object plan;
		final long timestamp;
		long hits = 0;

		CachedPlan(Object plan, long timestamp)
		{
			this.plan = plan;
			this.timestamp = timestamp;
		}
	}
}
