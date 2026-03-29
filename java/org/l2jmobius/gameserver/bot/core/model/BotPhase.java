/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.model;

/**
 * Strategic phase of a bot's lifecycle.
 * <p>
 * Phase tracks the high-level macro-task the bot is currently executing.
 * Tactical decisions within {@link BotPhase#FARMING} are driven by
 * {@link BotState} + {@link org.l2jmobius.gameserver.bot.core.brain.BotBrain}.
 * All phase transitions are owned by {@link org.l2jmobius.gameserver.bot.core.BotController}.
 *
 * <pre>
 * FARMING ──► TRAVELING_BACK ──► RESTING ──► TRAVELING_OUT ──► FARMING
 * </pre>
 */
public enum BotPhase
{
	/** In the farm zone — active tactical loop (IDLE / SEARCH_TARGET / ATTACK / etc). */
	FARMING,

	/** Travelling to the farm zone; action queue is pre-loaded with travel steps. */
	TRAVELING_OUT,

	/** Travelling to the city; action queue is pre-loaded with a teleport step. */
	TRAVELING_BACK,

	/** In city — waiting for the rest timer before returning to farm. */
	RESTING
}
