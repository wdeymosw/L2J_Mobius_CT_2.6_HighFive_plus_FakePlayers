/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.action;

import org.l2jmobius.gameserver.bot.core.exception.FatalBotException;
import org.l2jmobius.gameserver.bot.core.exception.RecoverableBotException;
import org.l2jmobius.gameserver.bot.core.exception.ValidationBotException;
import org.l2jmobius.gameserver.bot.core.goap.GoapTuning;
import org.l2jmobius.gameserver.bot.core.model.BotInstance;

/**
 * Tells the bot to sit down and waits until the sitting state is confirmed.
 * <p>
 * {@code player.sitDown()} is asynchronous — the animation plays on the client
 * and the server flag flips after a short delay, so we poll {@code isSitting()}
 * rather than treating this as a one-shot.
 */
public class SitDownAction extends AbstractStateWaitAction
{
	@Override
	protected void onFirstExecute(BotInstance bot, long now) throws FatalBotException, ValidationBotException, RecoverableBotException
	{
		bot.getPlayer().sitDown();
	}

	@Override
	protected boolean isStateReached(BotInstance bot, long now)
	{
		return bot.getPlayer().isSitting();
	}

	@Override
	protected long getTimeoutMs()
	{
		return GoapTuning.ACTION_SIT_TIMEOUT_MS;
	}
}

