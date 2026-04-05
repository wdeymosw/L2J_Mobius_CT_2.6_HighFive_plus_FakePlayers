/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.log.filter;

import java.util.logging.Filter;
import java.util.logging.LogRecord;

/**
 * Routes all bot-layer log records to the dedicated bot log file.
 * Matches any logger whose name starts with the bot package prefix,
 * plus the StructuredBotLogger which uses the name "BotOrchestrator".
 */
public class BotFilter implements Filter
{
	private static final String BOT_PACKAGE = "org.l2jmobius.gameserver.bot";
	private static final String BOT_ORCHESTRATOR = "BotOrchestrator";

	@Override
	public boolean isLoggable(LogRecord record)
	{
		final String name = record.getLoggerName();
		return (name != null) && (name.startsWith(BOT_PACKAGE) || BOT_ORCHESTRATOR.equals(name));
	}
}
