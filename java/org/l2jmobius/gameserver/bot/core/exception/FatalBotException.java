/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.exception;

import org.l2jmobius.gameserver.bot.core.model.BotInstance;

/**
 * Exception for fatal/unrecoverable bot errors.
 * 
 * Recovery strategy: DISABLE
 */
public class FatalBotException extends BotException
{
	private static final long serialVersionUID = 1L;
	
	public FatalBotException(String message, BotInstance bot)
	{
		super(message, bot);
	}
	
	public FatalBotException(String message, BotInstance bot, Throwable cause)
	{
		super(message, bot, cause);
	}
	
	@Override
	public BotRecoveryStrategy getRecoveryStrategy()
	{
		return BotRecoveryStrategy.DISABLE;
	}
}
