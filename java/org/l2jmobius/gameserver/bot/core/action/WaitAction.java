/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.action;

import org.l2jmobius.gameserver.ai.Intention;
import org.l2jmobius.gameserver.bot.core.model.BotInstance;

/**
 * Pauses the bot for a fixed duration.
 */
public class WaitAction implements BotAction
{
	private final long _endTime;

	public WaitAction(long durationMs)
	{
		_endTime = System.currentTimeMillis() + durationMs;
	}

	@Override
	public void execute(BotInstance bot, long now)
	{
		if (!bot.getPlayer().isMoving())
		{
			bot.getPlayer().getAI().setIntention(Intention.IDLE);
		}
	}

	@Override
	public boolean isDone(BotInstance bot, long now)
	{
		return now >= _endTime;
	}
}
