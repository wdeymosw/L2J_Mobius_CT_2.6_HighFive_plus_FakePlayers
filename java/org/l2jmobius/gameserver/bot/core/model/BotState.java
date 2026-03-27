/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.model;

/**
 * Operating modes of a bot.
 *
 * <pre>
 * IDLE  ──► SEARCHING ──► ATTACKING
 *  ▲              │             │
 *  │         (no target)   (target dead)
 *  │              ▼             ▼
 *  └──── RESTING ◄── TRAVELING ◄── (RETREAT decision)
 *                        │
 *                   DEAD (revive) ──► TRAVELING
 * </pre>
 */
public enum BotState
{
	/** No task — waiting. Initial state after spawn or brief pause. */
	IDLE,

	/** Scanning the farm zone for the next target. */
	SEARCHING,

	/** Actively fighting the current target. */
	ATTACKING,

	/**
	 * Complex navigation: walking to city, returning to farm, patrolling.
	 * Not a combat state — Bot uses {@link org.l2jmobius.gameserver.bot.core.brain.TravelReason}
	 * to know what to do on arrival.
	 */
	TRAVELING,

	/**
	 * In city or safe zone after a sell/restock trip.
	 * Bot is limited in actions and waits for the rest timer to expire.
	 */
	RESTING,

	/** Bot died and is waiting for the revive delay. */
	DEAD
}
