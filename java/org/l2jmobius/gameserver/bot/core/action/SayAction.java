/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.action;

import org.l2jmobius.gameserver.bot.core.exception.FatalBotException;
import org.l2jmobius.gameserver.bot.core.exception.RecoverableBotException;
import org.l2jmobius.gameserver.bot.core.exception.ValidationBotException;
import org.l2jmobius.gameserver.bot.core.model.BotInstance;
import org.l2jmobius.gameserver.network.enums.ChatType;
import org.l2jmobius.gameserver.network.serverpackets.CreatureSay;

/**
 * Makes the bot say a message in general chat (visible to nearby players).
 * Completes instantly after broadcasting the packet.
 */
public class SayAction extends AbstractOneTimeAction
{
	private final String _message;

	public SayAction(String message)
	{
		_message = message;
	}

	@Override
	protected void doExecute(BotInstance bot, long now) throws FatalBotException, ValidationBotException, RecoverableBotException
	{
		bot.getPlayer().broadcastPacket(
			new CreatureSay(bot.getPlayer(), ChatType.GENERAL, bot.getPlayer().getName(), _message));
	}
}
