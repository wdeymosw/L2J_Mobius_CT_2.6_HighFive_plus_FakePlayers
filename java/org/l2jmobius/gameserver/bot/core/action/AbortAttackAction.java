/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.action;

import org.l2jmobius.gameserver.bot.core.exception.FatalBotException;
import org.l2jmobius.gameserver.bot.core.exception.RecoverableBotException;
import org.l2jmobius.gameserver.bot.core.exception.ValidationBotException;
import org.l2jmobius.gameserver.bot.core.model.BotInstance;

/**
 * Immediately cancels the bot's current auto-attack swing.
 * One-shot: completes after the first execute call.
 */
public class AbortAttackAction implements BotAction
{
	private boolean _done = false;

	@Override
	public void execute(BotInstance bot, long now) throws FatalBotException, ValidationBotException, RecoverableBotException
	{
		bot.getPlayer().abortAttack();
		_done = true;
	}

	@Override
	public boolean isDone(BotInstance bot, long now)
	{
		return _done;
	}
}
