/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.action;

import org.l2jmobius.gameserver.bot.core.exception.FatalBotException;
import org.l2jmobius.gameserver.bot.core.exception.RecoverableBotException;
import org.l2jmobius.gameserver.bot.core.exception.ValidationBotException;
import org.l2jmobius.gameserver.bot.core.model.BotInstance;

/**
 * Revives the bot in place and switches it back to running mode.
 * One-shot: completes after the first execute call.
 * <p>
 * Note: {@link org.l2jmobius.gameserver.bot.core.goap.GoapAgent} handles death
 * procedurally (with a random delay) and calls {@code doRevive()} directly.
 * This action exists as a reusable atomic building block for any future
 * revive sequences that need explicit queue ordering.
 */
public class ReviveAction extends AbstractOneTimeAction
{
	@Override
	protected void doExecute(BotInstance bot, long now) throws FatalBotException, ValidationBotException, RecoverableBotException
	{
		bot.getPlayer().doRevive();
		bot.getPlayer().setRunning();
	}
}

