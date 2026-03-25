/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.model;

/**
 * Defines what a bot should do when it reaches its {@code _moveDestination}
 * in the {@link BotState#TRAVEL} state.
 */
public enum TravelAction
{
	/** Arrived at farm zone — switch to FARM_MOB. */
	RETURN_TO_ZONE,

	/** Arrived at city — sell loot, equip, restock, then enter CITY_IDLE. */
	SELL,

	/** Arrived at city — restock only, then enter CITY_IDLE. */
	BUY,
}
