/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.action;

import org.l2jmobius.gameserver.bot.core.exception.FatalBotException;
import org.l2jmobius.gameserver.bot.core.exception.RecoverableBotException;
import org.l2jmobius.gameserver.bot.core.exception.ValidationBotException;
import org.l2jmobius.gameserver.bot.core.model.BotInstance;

/**
 * Base class for actions that fire once asynchronously and then wait for a
 * game-state confirmation (or fall back to a safety timeout).
 * <p>
 * Lifecycle:
 * <ol>
 *   <li>First {@link #execute} call: timestamps the start and calls {@link #onFirstExecute}.</li>
 *   <li>Subsequent calls: no-op — the executor keeps calling {@link #isDone} each tick.</li>
 *   <li>{@link #isDone} returns {@code true} as soon as {@link #isStateReached} is true,
 *       or the safety timeout returned by {@link #getTimeoutMs} has elapsed.</li>
 * </ol>
 */
public abstract class AbstractStateWaitAction implements BotAction
{
	private long _startTime = 0;

	/**
	 * Called exactly once, on the first {@link #execute} tick.
	 * Subclasses issue the async game command here (e.g. sitDown, standUp).
	 *
	 * @param bot the bot executing the action
	 * @param now current time in ms
	 * @throws FatalBotException if the action encounters a fatal error
	 * @throws ValidationBotException if the action state is invalid
	 * @throws RecoverableBotException if the action encounters a temporary error
	 */
	protected abstract void onFirstExecute(BotInstance bot, long now) throws FatalBotException, ValidationBotException, RecoverableBotException;

	/**
	 * Polled each tick to check whether the desired game state has been reached.
	 *
	 * @param bot the bot executing the action
	 * @param now current time in ms
	 * @return {@code true} when the state change has been confirmed
	 */
	protected abstract boolean isStateReached(BotInstance bot, long now);

	/**
	 * Safety timeout in milliseconds.
	 * If the state is not reached within this time, the action is considered
	 * done anyway so the executor does not hang indefinitely.
	 *
	 * @return timeout in ms
	 */
	protected abstract long getTimeoutMs();

	@Override
	public final void execute(BotInstance bot, long now) throws FatalBotException, ValidationBotException, RecoverableBotException
	{
		if (_startTime == 0)
		{
			_startTime = now;
			onFirstExecute(bot, now);
		}
	}

	@Override
	public final boolean isDone(BotInstance bot, long now)
	{
		if (isStateReached(bot, now))
		{
			return true;
		}
		return (_startTime > 0) && ((now - _startTime) > getTimeoutMs());
	}
}
