/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.brain;

import org.l2jmobius.gameserver.bot.core.model.BotInstance;

/**
 * Tick behavior for PASSIVE bots: walk around the city, create ambient population.
 * <p>
 * TODO: Шаг E — реализовать городскую прогулку (BotZoneData.cityPath, guildmaster, shop)
 */
public class PassiveBehavior implements BotBehavior
{
	@Override
	public void think(BotInstance bot, BotContext ctx, long now)
	{
		bot.getExecutor().tick(bot, now);
	}
}
