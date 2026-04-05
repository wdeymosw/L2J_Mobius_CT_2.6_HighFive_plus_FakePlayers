/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.timeout;

import java.util.Map;
import org.l2jmobius.gameserver.bot.core.goap.GoapTuning;
import java.util.WeakHashMap;
import java.util.logging.Logger;

import org.l2jmobius.gameserver.bot.core.logging.BotMetrics;
import org.l2jmobius.gameserver.bot.core.logging.StructuredBotLogger;
import org.l2jmobius.gameserver.bot.core.model.BotInstance;

/**
 * Centralized timeout management for bot actions.
 * <p>
 * Tracks active action timeouts and triggers replans when actions exceed time limits.
 * Alternative to inline timeout checks in GoapAgent.tick().
 * <p>
 * Uses WeakHashMap so bot references don't prevent garbage collection.
 */
public class ActionTimeoutManager
{
	private static final Logger LOGGER = Logger.getLogger(ActionTimeoutManager.class.getName());

	private static final Map<BotInstance, ActionTimeoutEntry> ACTIVE_TIMEOUTS = new WeakHashMap<>();
	private static final long DEFAULT_TIMEOUT_MS = GoapTuning.ACTION_DEFAULT_TIMEOUT_MS;

	private ActionTimeoutManager()
	{
	}

	/**
	 * Starts timeout tracking for an action.
	 * <p>
	 * Called when a new action is activated.
	 *
	 * @param bot the bot executing the action
	 * @param actionName name of the action (for logging)
	 * @param timeoutMs timeout in milliseconds (0 = no timeout)
	 */
	public static void startTracking(BotInstance bot, String actionName, long timeoutMs)
	{
		if (timeoutMs <= 0)
		{
			ACTIVE_TIMEOUTS.remove(bot);
			return;
		}

		final long startTime = System.currentTimeMillis();
		final ActionTimeoutEntry entry = new ActionTimeoutEntry(actionName, startTime, timeoutMs);
		ACTIVE_TIMEOUTS.put(bot, entry);

		StructuredBotLogger.logActionStart(bot, actionName, timeoutMs);
	}

	/**
	 * Stops timeout tracking for a bot.
	 * <p>
	 * Called when an action completes successfully.
	 *
	 * @param bot the bot
	 */
	public static void stopTracking(BotInstance bot)
	{
		ACTIVE_TIMEOUTS.remove(bot);
	}

	/**
	 * Checks all active timeouts and triggers recovery for exceeded actions.
	 * <p>
	 * Should be called every tick by the scheduler.
	 */
	public static void checkTimeouts()
	{
		final long now = System.currentTimeMillis();

		ACTIVE_TIMEOUTS.entrySet().removeIf(entry ->
		{
			final BotInstance bot = entry.getKey();
			final ActionTimeoutEntry timeout = entry.getValue();

			if (now > timeout.getDeadline())
			{
				StructuredBotLogger.logActionTimeout(bot, timeout.getActionName(), now - timeout.getStartTime(), timeout.getTimeoutMs());
				BotMetrics.recordActionTimeout();

				// Clear plan and let next tick replan
				bot.clearGoapPlan();
				return true; // Remove from map
			}

			return false;
		});
	}

	/**
	 * Returns timeout info for diagnostics.
	 */
	public static String getStatus()
	{
		final StringBuilder sb = new StringBuilder();
		sb.append("Active timeouts: ").append(ACTIVE_TIMEOUTS.size()).append("\n");

		for (final Map.Entry<BotInstance, ActionTimeoutEntry> entry : ACTIVE_TIMEOUTS.entrySet())
		{
			final BotInstance bot = entry.getKey();
			final ActionTimeoutEntry timeout = entry.getValue();
			final long elapsed = System.currentTimeMillis() - timeout.getStartTime();
			final long remaining = timeout.getDeadline() - System.currentTimeMillis();

			sb.append("  - ").append(bot.getPlayer().getName());
			sb.append(" [").append(timeout.getActionName()).append("]");
			sb.append(" elapsed=").append(elapsed).append("ms");
			sb.append(" remaining=").append(remaining).append("ms\n");
		}

		return sb.toString();
	}

	/** Reset all tracking. */
	public static void reset()
	{
		ACTIVE_TIMEOUTS.clear();
	}

	// =========================================================================
	// Internal Entry Class
	// =========================================================================

	private static class ActionTimeoutEntry
	{
		private final String actionName;
		private final long startTime;
		private final long timeoutMs;
		private final long deadline;

		ActionTimeoutEntry(String actionName, long startTime, long timeoutMs)
		{
			this.actionName = actionName;
			this.startTime = startTime;
			this.timeoutMs = timeoutMs;
			this.deadline = startTime + timeoutMs;
		}

		String getActionName()
		{
			return actionName;
		}

		long getStartTime()
		{
			return startTime;
		}

		long getTimeoutMs()
		{
			return timeoutMs;
		}

		long getDeadline()
		{
			return deadline;
		}
	}
}
