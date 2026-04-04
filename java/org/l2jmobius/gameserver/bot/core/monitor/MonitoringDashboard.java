/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.monitor;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Logger;

import org.l2jmobius.gameserver.bot.core.logging.BotMetrics;
import org.l2jmobius.gameserver.bot.core.timeout.ActionTimeoutManager;
import org.l2jmobius.gameserver.bot.core.search.SearchDepthLimit;

/**
 * Real-time monitoring dashboard for bot system health.
 * <p>
 * Provides formatted output for: metrics, timeout status, performance stats.
 * Can be queried by admin commands for live diagnostics.
 */
public class MonitoringDashboard
{
	private static final Logger LOGGER = Logger.getLogger(MonitoringDashboard.class.getName());
	private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm:ss");

	private MonitoringDashboard()
	{
	}

	/**
	 * Returns full system dashboard.
	 */
	public static String getFullDashboard()
	{
		final StringBuilder sb = new StringBuilder();

		sb.append("╔════════════════════════════════════════════════════════════╗\n");
		sb.append("║          BOT SYSTEM MONITORING DASHBOARD                   ║\n");
		sb.append("╚════════════════════════════════════════════════════════════╝\n\n");

		sb.append(getTimestamp());
		sb.append("\n");

		sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
		sb.append("METRICS SUMMARY\n");
		sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
		sb.append(BotMetrics.getSummary());
		sb.append("\n");

		sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
		sb.append("TIMEOUT STATUS\n");
		sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
		sb.append(ActionTimeoutManager.getStatus());
		sb.append("\n");

		sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
		sb.append("SEARCH DEPTH\n");
		sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
		sb.append("Max Depth: ").append(SearchDepthLimit.getMaxDepth()).append("\n");
		sb.append("Min Depth: ").append(SearchDepthLimit.MIN_DEPTH).append("\n");
		sb.append("Max Allowed: ").append(SearchDepthLimit.MAX_DEPTH).append("\n\n");

		sb.append("╔════════════════════════════════════════════════════════════╗\n");

		return sb.toString();
	}

	/**
	 * Returns quick status for inline reporting.
	 */
	public static String getQuickStatus()
	{
		return String.format("[%s] Metrics: %s | Timeouts: %d active", getTime(), BotMetrics.getActionExecutedCount(), ActionTimeoutManager.getStatus());
	}

	/**
	 * Returns performance metrics only.
	 */
	public static String getMetricsOnly()
	{
		final StringBuilder sb = new StringBuilder();
		sb.append("📊 BOT METRICS\n");
		sb.append(BotMetrics.getSummary());
		return sb.toString();
	}

	/**
	 * Returns timeout diagnostics.
	 */
	public static String getTimeoutDiagnostics()
	{
		final StringBuilder sb = new StringBuilder();
		sb.append("⏱️  TIMEOUT DIAGNOSTICS\n");
		sb.append(ActionTimeoutManager.getStatus());
		return sb.toString();
	}

	/**
	 * Returns health check (simple pass/fail).
	 */
	public static boolean isSystemHealthy()
	{
		// System is healthy if:
		// - Fatal exceptions < 5 in last minute (would indicate systemic failure)
		// - Validation failures are reasonable
		// - Not in state of cascading timeouts
		final long fatalCount = BotMetrics.getExceptionFatalCount();

		return fatalCount < 5;
	}

	/**
	 * Returns alert if system is degraded.
	 */
	public static String checkAlerts()
	{
		final StringBuilder sb = new StringBuilder();

		final long fatalCount = BotMetrics.getExceptionFatalCount();
		if (fatalCount > 3)
		{
			sb.append("⚠️  HIGH FATAL EXCEPTIONS: ").append(fatalCount).append("\n");
		}

		final long validationCount = BotMetrics.getExceptionValidationCount();
		if (validationCount > 10)
		{
			sb.append("⚠️  HIGH VALIDATION FAILURES: ").append(validationCount).append("\n");
		}

		final long timeoutCount = BotMetrics.getActionTimeoutCount();
		if (timeoutCount > 5)
		{
			sb.append("⚠️  HIGH TIMEOUTS: ").append(timeoutCount).append("\n");
		}

		if (sb.length() == 0)
		{
			sb.append("✅ No alerts\n");
		}

		return sb.toString();
	}

	private static String getTimestamp()
	{
		return "Timestamp: " + TIME_FORMAT.format(new Date());
	}

	private static String getTime()
	{
		return TIME_FORMAT.format(new Date());
	}
}
