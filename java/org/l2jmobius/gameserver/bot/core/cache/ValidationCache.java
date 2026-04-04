/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.cache;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import org.l2jmobius.gameserver.bot.core.tuning.PerformanceConfig;

/**
 * Caches validation results to avoid redundant precondition checks.
 * <p>
 * Key: action name + world state hash
 * Value: validation result (pass/fail)
 * <p>
 * Invalidated when world state changes.
 */
public class ValidationCache
{
	private static final Logger LOGGER = Logger.getLogger(ValidationCache.class.getName());

	private static final Map<String, ValidationResult> cache = new HashMap<>();

	private ValidationCache()
	{
	}

	/**
	 * Gets cached validation result.
	 *
	 * @param actionName action identifier
	 * @param worldStateHash world state hash
	 * @return cached result or null if not cached
	 */
	public static Boolean getValidation(String actionName, int worldStateHash)
	{
		if (!PerformanceConfig.isCachingEnabled())
		{
			return null;
		}

		final String key = actionName + ":" + worldStateHash;

		synchronized (cache)
		{
			final ValidationResult result = cache.get(key);

			if (result == null)
			{
				return null;
			}

			if (System.currentTimeMillis() - result.timestamp > PerformanceConfig.getCacheExpirationMs())
			{
				cache.remove(key);
				return null;
			}

			return result.isValid;
		}
	}

	/**
	 * Caches validation result.
	 *
	 * @param actionName action identifier
	 * @param worldStateHash world state hash
	 * @param isValid result
	 */
	public static void setValidation(String actionName, int worldStateHash, boolean isValid)
	{
		if (!PerformanceConfig.isCachingEnabled())
		{
			return;
		}

		final String key = actionName + ":" + worldStateHash;

		synchronized (cache)
		{
			cache.put(key, new ValidationResult(isValid, System.currentTimeMillis()));
		}
	}

	/** Invalidate all validation results. */
	public static void invalidateAll()
	{
		synchronized (cache)
		{
			cache.clear();
		}
	}

	/** Get cache statistics. */
	public static String getStats()
	{
		synchronized (cache)
		{
			return String.format("ValidationCache: entries=%d", cache.size());
		}
	}

	private static class ValidationResult
	{
		final boolean isValid;
		final long timestamp;

		ValidationResult(boolean isValid, long timestamp)
		{
			this.isValid = isValid;
			this.timestamp = timestamp;
		}
	}
}
