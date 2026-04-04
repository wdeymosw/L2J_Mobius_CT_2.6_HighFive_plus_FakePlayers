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
 * Pauses the bot until its HP reaches or exceeds the given threshold.
 * Used before returning to the farm zone so the bot is never sent back at low HP.
 */
public class WaitForHpAction implements BotAction
{
	private final double _targetHpPercent;

	/**
	 * @param targetHpPercent HP percentage (0–100) to wait for before proceeding
	 */
	public WaitForHpAction(double targetHpPercent)
	{
		_targetHpPercent = targetHpPercent;
	}

	@Override
	public void execute(BotInstance bot, long now) throws FatalBotException, ValidationBotException, RecoverableBotException
	{
		if (!bot.getPlayer().isMoving())
		{
			bot.getPlayer().getAI().setIntention(Intention.IDLE);
		}
	}

	@Override
	public boolean isDone(BotInstance bot, long now)
	{
		final double maxHp = bot.getPlayer().getMaxHp();
		if (maxHp <= 0)
		{
			return true;
		}
		return (bot.getPlayer().getCurrentHp() / maxHp * 100.0) >= _targetHpPercent;
	}
}
