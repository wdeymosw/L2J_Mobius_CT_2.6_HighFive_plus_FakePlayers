/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.logging;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.l2jmobius.gameserver.bot.core.model.BotInstance;

/**
 * Structured logging for bot events with JSON output.
 * <p>
 * Logs bot lifecycle, errors, and state changes in a standardized format:
 * <pre>
 * [TIMESTAMP] [BOT_NAME] [EVENT_TYPE] field1=value1 field2=value2
 * </pre>
 * <p>
 * Examples:
 * <pre>
 * 2026-04-03T17:55:00.123Z MyBot ACTION_START action=AttackGoapAction timeout=30000
 * 2026-04-03T17:55:00.456Z MyBot ACTION_TIMEOUT action=AttackGoapAction elapsed=32500
 * 2026-04-03T17:55:01.000Z MyBot STATE_VALIDATION_ERROR field=HP_CRITICAL reason=hp_not_in_range
 * </pre>
 */
public class StructuredBotLogger
{
	private static final Logger LOGGER = Logger.getLogger("BotOrchestrator");

	// Event type constants
	public static final String EVENT_TICK = "TICK";
	public static final String EVENT_ACTION_START = "ACTION_START";
	public static final String EVENT_ACTION_COMPLETE = "ACTION_COMPLETE";
	public static final String EVENT_ACTION_TIMEOUT = "ACTION_TIMEOUT";
	public static final String EVENT_PLAN_BUILD = "PLAN_BUILD";
	public static final String EVENT_PLAN_CLEAR = "PLAN_CLEAR";
	public static final String EVENT_PLAN_INTERRUPT = "PLAN_INTERRUPT";
	public static final String EVENT_STATE_VALIDATION_ERROR = "STATE_VALIDATION_ERROR";
	public static final String EVENT_PRECONDITION_ERROR = "PRECONDITION_ERROR";
	public static final String EVENT_EXCEPTION_CAUGHT = "EXCEPTION_CAUGHT";
	public static final String EVENT_RECOVERY_TRIGGERED = "RECOVERY_TRIGGERED";
	public static final String EVENT_BOT_DISABLED = "BOT_DISABLED";
	public static final String EVENT_BOT_REVIVED = "BOT_REVIVED";

	private StructuredBotLogger()
	{
	}

	/**
	 * Logs a structured bot event.
	 *
	 * @param level log level (INFO, WARNING, SEVERE, FINE, FINEST)
	 * @param bot the bot involved (or null for system events)
	 * @param eventType event type (use EVENT_* constants)
	 * @param fields variable number of key=value pairs
	 */
	public static void log(Level level, BotInstance bot, String eventType, Object... fields)
	{
		final String botName = (bot != null) ? bot.getPlayer().getName() : "SYSTEM";
		final String timestamp = Instant.now().toString();

		final Map<String, String> fieldMap = new HashMap<>();
		for (int i = 0; i < fields.length; i += 2)
		{
			if ((i + 1) < fields.length)
			{
				fieldMap.put(String.valueOf(fields[i]), String.valueOf(fields[i + 1]));
			}
		}

		final String message = formatMessage(timestamp, botName, eventType, fieldMap);
		LOGGER.log(level, message);
	}

	/**
	 * Logs at INFO level (for important events).
	 */
	public static void info(BotInstance bot, String eventType, Object... fields)
	{
		log(Level.INFO, bot, eventType, fields);
	}

	/**
	 * Logs at WARNING level (for potential issues).
	 */
	public static void warning(BotInstance bot, String eventType, Object... fields)
	{
		log(Level.WARNING, bot, eventType, fields);
	}

	/**
	 * Logs at SEVERE level (for critical errors).
	 */
	public static void severe(BotInstance bot, String eventType, Object... fields)
	{
		log(Level.SEVERE, bot, eventType, fields);
	}

	/**
	 * Logs at FINE level (for debugging, shown only in DEBUG mode).
	 */
	public static void fine(BotInstance bot, String eventType, Object... fields)
	{
		log(Level.FINE, bot, eventType, fields);
	}

	/**
	 * Logs at FINEST level (for detailed tracing, shown only in verbose DEBUG mode).
	 */
	public static void finest(BotInstance bot, String eventType, Object... fields)
	{
		log(Level.FINEST, bot, eventType, fields);
	}

	/**
	 * Formats the structured log message.
	 * <p>
	 * Format: [TIMESTAMP] [BOT_NAME] [EVENT_TYPE] field1=value1 field2=value2 ...
	 */
	private static String formatMessage(String timestamp, String botName, String eventType, Map<String, String> fields)
	{
		final StringBuilder sb = new StringBuilder();
		sb.append("[").append(timestamp).append("] ");
		sb.append("[").append(botName).append("] ");
		sb.append("[").append(eventType).append("]");

		for (final Map.Entry<String, String> entry : fields.entrySet())
		{
			sb.append(" ").append(entry.getKey()).append("=").append(entry.getValue());
		}

		return sb.toString();
	}

	/**
	 * Logs action start with timeout info.
	 */
	public static void logActionStart(BotInstance bot, String actionName, long timeoutMs)
	{
		info(bot, EVENT_ACTION_START, "action", actionName, "timeout_ms", timeoutMs);
	}

	/**
	 * Logs action completion.
	 */
	public static void logActionComplete(BotInstance bot, String actionName, long elapsedMs)
	{
		info(bot, EVENT_ACTION_COMPLETE, "action", actionName, "elapsed_ms", elapsedMs);
	}

	/**
	 * Logs action timeout.
	 */
	public static void logActionTimeout(BotInstance bot, String actionName, long elapsedMs, long limitMs)
	{
		warning(bot, EVENT_ACTION_TIMEOUT, "action", actionName, "elapsed_ms", elapsedMs, "limit_ms", limitMs);
	}

	/**
	 * Logs plan build.
	 */
	public static void logPlanBuild(BotInstance bot, String goalName, int actionCount, long planTimeMs)
	{
		info(bot, EVENT_PLAN_BUILD, "goal", goalName, "actions", actionCount, "time_ms", planTimeMs);
	}

	/**
	 * Logs plan interruption.
	 */
	public static void logPlanInterrupt(BotInstance bot, String reason)
	{
		info(bot, EVENT_PLAN_INTERRUPT, "reason", reason);
	}

	/**
	 * Logs validation error.
	 */
	public static void logValidationError(BotInstance bot, String fieldName, String expected, String actual)
	{
		warning(bot, EVENT_STATE_VALIDATION_ERROR, "field", fieldName, "expected", expected, "actual", actual);
	}

	/**
	 * Logs exception caught and recovery strategy applied.
	 */
	public static void logExceptionCaught(BotInstance bot, String exceptionType, String strategy)
	{
		warning(bot, EVENT_EXCEPTION_CAUGHT, "exception", exceptionType, "recovery_strategy", strategy);
	}

	/**
	 * Logs recovery action taken.
	 */
	public static void logRecoveryTriggered(BotInstance bot, String strategy)
	{
		info(bot, EVENT_RECOVERY_TRIGGERED, "strategy", strategy);
	}

	/**
	 * Logs bot disabled.
	 */
	public static void logBotDisabled(BotInstance bot, String reason)
	{
		severe(bot, EVENT_BOT_DISABLED, "reason", reason);
	}

	/**
	 * Logs bot revived.
	 */
	public static void logBotRevived(BotInstance bot)
	{
		info(bot, EVENT_BOT_REVIVED);
	}
}
