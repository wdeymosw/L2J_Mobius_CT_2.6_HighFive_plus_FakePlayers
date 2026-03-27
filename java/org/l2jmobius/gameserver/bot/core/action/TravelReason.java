/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.action;

/**
 * Reason for entering {@link org.l2jmobius.gameserver.bot.core.model.BotState#TRAVELING}.
 * Used by the bot's update loop to determine behaviour on arrival.
 */
public enum TravelReason
{
	/** Returning to farm zone after city visit or fleeing. */
	RETURN_TO_FARM,

	/** Going to city: sell loot, restock consumables. */
	GO_TO_CITY
}
