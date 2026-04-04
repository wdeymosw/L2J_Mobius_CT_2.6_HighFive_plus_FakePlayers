/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.action;

import org.l2jmobius.gameserver.ai.Intention;
import org.l2jmobius.gameserver.bot.core.exception.FatalBotException;
import org.l2jmobius.gameserver.bot.core.exception.RecoverableBotException;
import org.l2jmobius.gameserver.bot.core.exception.ValidationBotException;
import org.l2jmobius.gameserver.bot.core.model.BotInstance;
import org.l2jmobius.gameserver.model.actor.Creature;

/**
 * Makes the bot follow a target creature for a fixed duration.
 * <p>
 * Finished early if the target dies or the time limit expires.
 * Useful for party-follow behaviour or escorting sequences.
 */
public class FollowAction implements BotAction
{
	private final Creature _target;
	private final long _durationMs;
	private long _endTime = 0;

	public FollowAction(Creature target, long durationMs)
	{
		_target = target;
		_durationMs = durationMs;
	}

	@Override
	public void execute(BotInstance bot, long now) throws FatalBotException, ValidationBotException, RecoverableBotException
	{
		if (_endTime == 0)
		{
			_endTime = now + _durationMs;
			bot.getPlayer().getAI().setIntention(Intention.FOLLOW, _target);
		}
	}

	@Override
	public boolean isDone(BotInstance bot, long now)
	{
		if ((_target == null) || _target.isDead())
		{
			return true;
		}
		return (_endTime > 0) && (now >= _endTime);
	}
}
