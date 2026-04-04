/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.exception;

import java.util.logging.Logger;

import org.l2jmobius.gameserver.bot.core.model.BotInstance;

/**
 * Base exception for all bot-related errors.
 * 
 * Each bot exception carries:
 * - The message describing what went wrong
 * - Reference to the affected bot (for context)
 * - Recovery strategy (how to handle this error)
 * 
 * Subclasses define specific error types and their recovery approach.
 */
public abstract class BotException extends Exception
{
	private static final Logger LOGGER = Logger.getLogger(BotException.class.getName());
	private static final long serialVersionUID = 1L;
	
	/** Reference to the affected bot */
	protected BotInstance _bot;
	
	/**
	 * Creates a new bot exception.
	 * 
	 * @param message describes the error
	 * @param bot the bot that encountered the error (can be null for testing)
	 */
	public BotException(String message, BotInstance bot)
	{
		super(message);
		_bot = bot;
		logWarning(message, bot);
	}
	
	/**
	 * Creates a new bot exception with a cause.
	 * 
	 * @param message describes the error
	 * @param bot the bot that encountered the error (can be null for testing)
	 * @param cause the underlying exception
	 */
	public BotException(String message, BotInstance bot, Throwable cause)
	{
		super(message, cause);
		_bot = bot;
		logWarning(message, bot);
	}
	
	private void logWarning(String message, BotInstance bot)
	{
		if (bot != null)
		{
			try
			{
				LOGGER.warning("BotException for bot " + bot.getPlayer().getName() + ": " + message);
			}
			catch (Exception e)
			{
				LOGGER.warning("BotException for bot: " + message);
			}
		}
		else
		{
			LOGGER.warning("BotException (no bot context): " + message);
		}
	}
	
	/**
	 * Determines how this exception should be handled.
	 * 
	 * @return the recovery strategy for this exception type
	 */
	public abstract BotRecoveryStrategy getRecoveryStrategy();
	
	/**
	 * Gets the bot instance associated with this exception.
	 * 
	 * @return the bot, or null if not available
	 */
	public BotInstance getBot()
	{
		return _bot;
	}
	
	/**
	 * Convenience method to check if this exception is recoverable.
	 * 
	 * @return true if recovery is possible (not DISABLE)
	 */
	public boolean isRecoverable()
	{
		BotRecoveryStrategy strategy = getRecoveryStrategy();
		return strategy != BotRecoveryStrategy.DISABLE;
	}
}