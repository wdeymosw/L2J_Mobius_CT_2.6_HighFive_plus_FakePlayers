/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.action;

import org.l2jmobius.gameserver.bot.core.exception.FatalBotException;
import org.l2jmobius.gameserver.bot.core.exception.RecoverableBotException;
import org.l2jmobius.gameserver.bot.core.exception.ValidationBotException;
import org.l2jmobius.gameserver.bot.core.model.BotInstance;

/**
 * Base class for one-shot actions that perform a single operation and complete immediately.
 * <p>
 * Subclasses implement {@link #doExecute} which is guaranteed to be called exactly once.
 * {@link #isDone} returns {@code true} as soon as {@link #doExecute} has been called.
 */
public abstract class AbstractOneTimeAction implements BotAction
{
	private boolean _executed = false;

	/**
	 * Performs the one-time work for this action.
	 *
	 * @param bot the bot executing the action
	 * @param now current time in ms
	 * @throws FatalBotException if action encounters a fatal error
	 * @throws ValidationBotException if action state is invalid
	 * @throws RecoverableBotException if action encounters a temporary error
	 */
	protected abstract void doExecute(BotInstance bot, long now) throws FatalBotException, ValidationBotException, RecoverableBotException;

	@Override
	public final void execute(BotInstance bot, long now) throws FatalBotException, ValidationBotException, RecoverableBotException
	{
		if (!_executed)
		{
			doExecute(bot, now);
			_executed = true;
		}
	}

	@Override
	public final boolean isDone(BotInstance bot, long now)
	{
		return _executed;
	}
}
