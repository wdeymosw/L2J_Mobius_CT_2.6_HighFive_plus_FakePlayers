/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.exception;

import org.l2jmobius.gameserver.bot.core.model.BotInstance;

/**
 * Exception for bot state validation failures.
 * 
 * Recovery strategy: ADJUST_AND_REPLAN
 */
public class ValidationBotException extends BotException
{
	private static final long serialVersionUID = 1L;
	
	public ValidationBotException(String message, BotInstance bot)
	{
		super(message, bot);
	}
	
	public ValidationBotException(String message, BotInstance bot, Throwable cause)
	{
		super(message, bot, cause);
	}
	
	@Override
	public BotRecoveryStrategy getRecoveryStrategy()
	{
		return BotRecoveryStrategy.ADJUST_AND_REPLAN;
	}
}
