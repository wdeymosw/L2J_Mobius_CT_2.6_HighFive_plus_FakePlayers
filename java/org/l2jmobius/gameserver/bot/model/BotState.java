/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.model;

/**
 * Finite state machine states for a BotInstance.
 * ThinkService drives transitions between these states each tick.
 */
public enum BotState
{
	/** Bot is idle, waiting for the next think cycle. */
	IDLE,

	/** Bot is searching for a target within its zone. */
	SEARCHING,

	/** Bot is actively attacking a target. */
	ATTACKING,

	/** Bot died and is waiting for revive. */
	DEAD,

	/** Bot is moving back to its zone center after wandering out. */
	RETURNING,

	/** Bot is buying supplies (shots, arrows, potions) — virtual instant action. */
	BUYING,

	/** Bot is standing idle in a city after buying, before heading to the farming zone. */
	CITY_IDLE
}
