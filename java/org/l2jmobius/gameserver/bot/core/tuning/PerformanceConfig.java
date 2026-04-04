/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.tuning;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import org.l2jmobius.gameserver.bot.core.search.SearchDepthLimit;

/**
 * Centralized performance configuration.
 * <p>
 * Single source of truth for tuning parameters:
 * - Timeout durations
 * - Search depth
 * - Pool sizes
 * - Caching thresholds
 * - Threading parameters
 */
public class PerformanceConfig
{
	private static final Logger LOGGER = Logger.getLogger(PerformanceConfig.class.getName());

	// Timeout settings
	private static volatile long actionTimeoutMs = 30000L;
	private static volatile long planTimeoutMs = 5000L;

	// Search depth settings
	private static volatile int maxSearchDepth = 10;
	private static volatile int minSearchDepth = 1;

	// Pool sizes
	private static volatile int actionPoolSize = 100;
	private static volatile int worldStatePoolSize = 50;

	// Caching
	private static volatile boolean cachingEnabled = true;
	private static volatile long cacheExpirationMs = 1000L;

	// Threading
	private static volatile int planningThreads = 2;
	private static volatile int executorThreads = 4;

	// Auto-tuning
	private static volatile boolean autoTuningEnabled = true;
	private static volatile int autoTuneTargetBots = 50;

	private PerformanceConfig()
	{
	}

	// =========================================================================
	// Timeout Configuration
	// =========================================================================

	public static long getActionTimeoutMs()
	{
		return actionTimeoutMs;
	}

	public static void setActionTimeoutMs(long ms)
	{
		actionTimeoutMs = Math.max(1000, ms); // Minimum 1 second
	}

	public static long getPlanTimeoutMs()
	{
		return planTimeoutMs;
	}

	public static void setPlanTimeoutMs(long ms)
	{
		planTimeoutMs = Math.max(500, ms); // Minimum 500ms
	}

	// =========================================================================
	// Search Depth Configuration
	// =========================================================================

	public static int getMaxSearchDepth()
	{
		return maxSearchDepth;
	}

	public static void setMaxSearchDepth(int depth)
	{
		maxSearchDepth = Math.max(SearchDepthLimit.MIN_DEPTH, Math.min(depth, SearchDepthLimit.MAX_DEPTH));
	}

	// =========================================================================
	// Pool Configuration
	// =========================================================================

	public static int getActionPoolSize()
	{
		return actionPoolSize;
	}

	public static void setActionPoolSize(int size)
	{
		actionPoolSize = Math.max(10, size);
	}

	public static int getWorldStatePoolSize()
	{
		return worldStatePoolSize;
	}

	public static void setWorldStatePoolSize(int size)
	{
		worldStatePoolSize = Math.max(5, size);
	}

	// =========================================================================
	// Caching Configuration
	// =========================================================================

	public static boolean isCachingEnabled()
	{
		return cachingEnabled;
	}

	public static void setCachingEnabled(boolean enabled)
	{
		cachingEnabled = enabled;
	}

	public static long getCacheExpirationMs()
	{
		return cacheExpirationMs;
	}

	public static void setCacheExpirationMs(long ms)
	{
		cacheExpirationMs = Math.max(100, ms); // Minimum 100ms
	}

	// =========================================================================
	// Threading Configuration
	// =========================================================================

	public static int getPlanningThreads()
	{
		return planningThreads;
	}

	public static void setPlanningThreads(int count)
	{
		planningThreads = Math.max(1, count);
	}

	public static int getExecutorThreads()
	{
		return executorThreads;
	}

	public static void setExecutorThreads(int count)
	{
		executorThreads = Math.max(1, count);
	}

	// =========================================================================
	// Auto-Tuning Configuration
	// =========================================================================

	public static boolean isAutoTuningEnabled()
	{
		return autoTuningEnabled;
	}

	public static void setAutoTuningEnabled(boolean enabled)
	{
		autoTuningEnabled = enabled;
	}

	public static int getAutoTuneTargetBots()
	{
		return autoTuneTargetBots;
	}

	public static void setAutoTuneTargetBots(int count)
	{
		autoTuneTargetBots = Math.max(1, count);
	}

	// =========================================================================
	// Bulk Configuration & Reporting
	// =========================================================================

	/** Get all settings as a map. */
	public static Map<String, Object> getAllSettings()
	{
		final Map<String, Object> settings = new HashMap<>();
		settings.put("actionTimeoutMs", actionTimeoutMs);
		settings.put("planTimeoutMs", planTimeoutMs);
		settings.put("maxSearchDepth", maxSearchDepth);
		settings.put("actionPoolSize", actionPoolSize);
		settings.put("worldStatePoolSize", worldStatePoolSize);
		settings.put("cachingEnabled", cachingEnabled);
		settings.put("cacheExpirationMs", cacheExpirationMs);
		settings.put("planningThreads", planningThreads);
		settings.put("executorThreads", executorThreads);
		settings.put("autoTuningEnabled", autoTuningEnabled);
		settings.put("autoTuneTargetBots", autoTuneTargetBots);
		return settings;
	}

	/** Get formatted settings report. */
	public static String getReport()
	{
		final StringBuilder sb = new StringBuilder();
		sb.append("╔════════════════════════════════════════════════════════════╗\n");
		sb.append("║           PERFORMANCE CONFIGURATION                        ║\n");
		sb.append("╚════════════════════════════════════════════════════════════╝\n\n");

		sb.append("TIMEOUTS\n");
		sb.append("  Action Timeout: ").append(actionTimeoutMs).append("ms\n");
		sb.append("  Plan Timeout: ").append(planTimeoutMs).append("ms\n\n");

		sb.append("SEARCH\n");
		sb.append("  Max Depth: ").append(maxSearchDepth).append("\n\n");

		sb.append("POOLING\n");
		sb.append("  Action Pool: ").append(actionPoolSize).append("\n");
		sb.append("  World State Pool: ").append(worldStatePoolSize).append("\n\n");

		sb.append("CACHING\n");
		sb.append("  Enabled: ").append(cachingEnabled).append("\n");
		sb.append("  Expiration: ").append(cacheExpirationMs).append("ms\n\n");

		sb.append("THREADING\n");
		sb.append("  Planning Threads: ").append(planningThreads).append("\n");
		sb.append("  Executor Threads: ").append(executorThreads).append("\n\n");

		sb.append("AUTO-TUNING\n");
		sb.append("  Enabled: ").append(autoTuningEnabled).append("\n");
		sb.append("  Target Bots: ").append(autoTuneTargetBots).append("\n");

		return sb.toString();
	}

	/** Reset all settings to defaults. */
	public static void reset()
	{
		actionTimeoutMs = 30000L;
		planTimeoutMs = 5000L;
		maxSearchDepth = 10;
		minSearchDepth = 1;
		actionPoolSize = 100;
		worldStatePoolSize = 50;
		cachingEnabled = true;
		cacheExpirationMs = 1000L;
		planningThreads = 2;
		executorThreads = 4;
		autoTuningEnabled = true;
		autoTuneTargetBots = 50;
	}
}
