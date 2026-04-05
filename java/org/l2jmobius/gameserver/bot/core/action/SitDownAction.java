/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.action;

import org.l2jmobius.gameserver.bot.core.exception.FatalBotException;
import org.l2jmobius.gameserver.bot.core.goap.GoapTuning;
import org.l2jmobius.gameserver.bot.core.exception.RecoverableBotException;
import org.l2jmobius.gameserver.bot.core.exception.ValidationBotException;
import org.l2jmobius.gameserver.bot.core.model.BotInstance;

/**
 * Tells the bot to sit down and waits until the sitting state is confirmed.
 * <p>
 * {@code player.sitDown()} is asynchronous — the animation plays on the client
 * and the server flag flips after a short delay, so we poll {@code isSitting()}
 * rather than treating this as a one-shot.
 */
public class SitDownAction implements BotAction
{
	/** Safety timeout: if sitting state never confirms, give up and continue. */
	private static final long TIMEOUT_MS = GoapTuning.ACTION_SIT_TIMEOUT_MS;

	private long _startTime = 0;

	@Override
	public void execute(BotInstance bot, long now) throws FatalBotException, ValidationBotException, RecoverableBotException
	{
		if (_startTime == 0)
		{
			_startTime = now;
			bot.getPlayer().sitDown();
		}
	}

	@Override
	public boolean isDone(BotInstance bot, long now)
	{
		if (bot.getPlayer().isSitting())
		{
			return true;
		}
		return (_startTime > 0) && ((now - _startTime) > TIMEOUT_MS);
	}
}
