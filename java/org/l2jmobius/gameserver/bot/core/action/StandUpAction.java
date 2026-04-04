/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.action;

import org.l2jmobius.gameserver.ai.Intention;
import org.l2jmobius.gameserver.bot.core.exception.FatalBotException;
import org.l2jmobius.gameserver.bot.core.exception.RecoverableBotException;
import org.l2jmobius.gameserver.bot.core.exception.ValidationBotException;
import org.l2jmobius.gameserver.bot.core.model.BotInstance;
import org.l2jmobius.gameserver.model.actor.Player;

/**
 * Tells the bot to stand up from a sitting position.
 * <p>
 * Uses the same manual state reset as {@code SitRestGoapAction} to avoid
 * triggering combat-state checks that {@code player.standUp()} may fire.
 */
public class StandUpAction implements BotAction
{
	private static final long TIMEOUT_MS = 3000;

	private long _startTime = 0;

	@Override
	public void execute(BotInstance bot, long now) throws FatalBotException, ValidationBotException, RecoverableBotException
	{
		if (_startTime == 0)
		{
			_startTime = now;
			final Player player = bot.getPlayer();
			if (player.isSitting())
			{
				player.setSittingProgress(false);
				player.setSitting(false);
				player.getAI().setIntention(Intention.IDLE);
			}
		}
	}

	@Override
	public boolean isDone(BotInstance bot, long now)
	{
		if (!bot.getPlayer().isSitting())
		{
			return true;
		}
		return (_startTime > 0) && ((now - _startTime) > TIMEOUT_MS);
	}
}
