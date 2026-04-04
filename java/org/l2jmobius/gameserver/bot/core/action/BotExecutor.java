/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.action;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.logging.Logger;


import org.l2jmobius.gameserver.bot.core.exception.RecoverableBotException;
import org.l2jmobius.gameserver.bot.core.exception.ValidationBotException;
import org.l2jmobius.gameserver.bot.core.exception.FatalBotException;
import org.l2jmobius.gameserver.bot.core.logging.StructuredBotLogger;
import org.l2jmobius.gameserver.bot.core.model.BotInstance;

/**
 * Executes the bot's action queue — one action at a time.
 * <p>
 * Each tick: if the current (head) action is done, it is removed and the
 * next one starts. Otherwise the current action is ticked.
 * <p>
 * All action.execute() calls are wrapped in exception handlers for reliability.
 */
public class BotExecutor
{
	private static final Logger LOGGER = Logger.getLogger(BotExecutor.class.getName());
	private final Deque<BotAction> _queue = new ArrayDeque<>();

	/** Adds an action to the end of the queue. */
	public void add(BotAction action)
	{
		_queue.addLast(action);
	}

	/** Clears all pending actions (e.g. on state change or RETREAT). */
	public void clear()
	{
		_queue.clear();
	}

	/** @return {@code true} if there are no actions pending or executing. */
	public boolean isIdle()
	{
		return _queue.isEmpty();
	}

	/**
	 * Advances the action queue by one tick.
	 * Removes completed actions and executes the next one.
	 *
	 * @param bot the bot whose actions to execute
	 * @param now current time in ms
	 */
	public void tick(BotInstance bot, long now)
	{
		BotAction current = _queue.peekFirst();
		if (current == null)
		{
			return;
		}

		if (current.isDone(bot, now))
		{
			_queue.pollFirst();
			return;
		}

		try
		{
			current.execute(bot, now);
		}
		catch (FatalBotException e)
		{
			// Critical error in action — log and clear queue
			StructuredBotLogger.logBotDisabled(bot, "Fatal error in action " + current.getClass().getSimpleName() + ": " + e.getMessage());
			_queue.clear();
		}
		catch (ValidationBotException e)
		{
			// State validation failed — clear this action and continue
			StructuredBotLogger.warning(bot, StructuredBotLogger.EVENT_STATE_VALIDATION_ERROR, "action", current.getClass().getSimpleName(), "reason", e.getMessage());
			_queue.pollFirst();
		}
		catch (RecoverableBotException e)
		{
			// Temporary error — retry next tick
			StructuredBotLogger.info(bot, StructuredBotLogger.EVENT_EXCEPTION_CAUGHT, "exception", "RecoverableBotException", "action", current.getClass().getSimpleName());
		}
		catch (Exception e)
		{
			// Unexpected error — log and skip action
			StructuredBotLogger.severe(bot, StructuredBotLogger.EVENT_EXCEPTION_CAUGHT, "exception", e.getClass().getSimpleName(), "action", current.getClass().getSimpleName());
			e.printStackTrace();
			_queue.pollFirst();
		}
	}
}
