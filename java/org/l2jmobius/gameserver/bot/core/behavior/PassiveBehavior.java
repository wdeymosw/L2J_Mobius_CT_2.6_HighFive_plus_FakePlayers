/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.behavior;

import org.l2jmobius.gameserver.bot.core.brain.BotDecision;
import org.l2jmobius.gameserver.bot.core.model.BotInstance;

/**
 * Tick behavior for PASSIVE bots: walk around the city, create ambient population.
 * <p>
 * TODO: Шаг E — реализовать городскую прогулку (BotZoneData.cityPath, guildmaster, shop)
 */
public class PassiveBehavior implements BotBehavior
{
	@Override
	public void think(BotInstance bot, BotDecision decision, long now)
	{
		// TODO: Шаг E — city walk
	}
}
