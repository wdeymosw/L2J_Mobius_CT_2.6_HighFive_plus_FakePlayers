/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.model;

/**
 * Reason for entering {@link BotState#MOVE_TO_TARGET}.
 * Used by BotController to determine behaviour on arrival.
 */
public enum TravelReason
{
	/** Returning to farm zone after city visit or fleeing. */
	RETURN_TO_FARM,

	/** Going to city: sell loot, restock consumables. */
	GO_TO_CITY
}
