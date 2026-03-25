/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.model;

/**
 * The three top-level operating modes of a bot, plus DEAD.
 * <p>
 * <b>TRAVEL</b>  — bot is walking to a fixed destination (zone, city, etc.).<br>
 * <b>FARM_MOB</b> — bot is farming: searching for targets and attacking them.<br>
 * <b>CITY_IDLE</b> — bot is resting in a city after selling/buying.<br>
 * <b>DEAD</b>    — bot died and is waiting to revive.<br>
 * <p>
 * FARM_MOB uses two internal sub-phases tracked by the same field:
 * {@link #SEARCHING} and {@link #ATTACKING}.
 */
public enum BotState
{
	// -----------------------------------------------------------------------
	// Main modes
	// -----------------------------------------------------------------------

	/** Bot is moving to {@code _moveDestination}; on arrival executes {@code _travelAction}. */
	TRAVEL,

	/** Bot is in the farming loop — see sub-phases below. */
	FARM_MOB,

	/** Bot is resting in a city after a sell/buy trip. */
	CITY_IDLE,

	/** Bot died and is waiting for the revive timer. */
	DEAD,

	// -----------------------------------------------------------------------
	// FARM_MOB sub-phases (internal — ThinkService transitions between these)
	// -----------------------------------------------------------------------

	/** Bot is looking for the next target within its zone. */
	SEARCHING,

	/** Bot is actively attacking its current target. */
	ATTACKING,
}
