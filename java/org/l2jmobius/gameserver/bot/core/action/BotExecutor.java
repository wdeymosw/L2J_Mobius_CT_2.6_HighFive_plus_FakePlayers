/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.action;

import java.util.ArrayDeque;
import java.util.Deque;

import org.l2jmobius.gameserver.bot.core.action.BotAction;
import org.l2jmobius.gameserver.bot.core.model.BotInstance;

/**
 * Executes the bot's action queue — one action at a time.
 * <p>
 * Each tick: if the current (head) action is done, it is removed and the
 * next one starts. Otherwise the current action is ticked.
 */
public class BotExecutor
{
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

		current.execute(bot, now);
	}
}
