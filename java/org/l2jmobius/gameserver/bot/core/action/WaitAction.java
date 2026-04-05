/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.action;

import org.l2jmobius.gameserver.ai.Intention;
import org.l2jmobius.gameserver.bot.core.exception.FatalBotException;
import org.l2jmobius.gameserver.bot.core.exception.RecoverableBotException;
import org.l2jmobius.gameserver.bot.core.exception.ValidationBotException;
import org.l2jmobius.gameserver.bot.core.model.BotInstance;

/**
 * Pauses the bot for a fixed duration.
 * <p>
 * {@code _endTime} is computed lazily on the first {@link #execute} call so that
 * scheduler queuing delays do not silently shorten the pause.
 */
public class WaitAction implements BotAction
{
	private final long _durationMs;
	private long _endTime = -1;

	public WaitAction(long durationMs)
	{
		_durationMs = durationMs;
	}

	@Override
	public void execute(BotInstance bot, long now) throws FatalBotException, ValidationBotException, RecoverableBotException
	{
		if (_endTime < 0)
		{
			_endTime = now + _durationMs;
		}
		if (!bot.getPlayer().isMoving())
		{
			bot.getPlayer().getAI().setIntention(Intention.IDLE);
		}
	}

	@Override
	public boolean isDone(BotInstance bot, long now)
	{
		return (_endTime >= 0) && (now >= _endTime);
	}
}
