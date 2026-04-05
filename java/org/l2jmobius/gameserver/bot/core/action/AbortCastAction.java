/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.action;

import org.l2jmobius.gameserver.bot.core.exception.FatalBotException;
import org.l2jmobius.gameserver.bot.core.exception.RecoverableBotException;
import org.l2jmobius.gameserver.bot.core.exception.ValidationBotException;
import org.l2jmobius.gameserver.bot.core.model.BotInstance;

/**
 * Immediately cancels the bot's current skill cast.
 * One-shot: completes after the first execute call.
 */
public class AbortCastAction extends AbstractOneTimeAction
{
	@Override
	protected void doExecute(BotInstance bot, long now) throws FatalBotException, ValidationBotException, RecoverableBotException
	{
		bot.getPlayer().abortCast();
	}
}

