/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.model;

/**
 * Operating states of a bot.
 *
 * <pre>
 * IDLE ──► SEARCH_TARGET ──► MOVE_TO_TARGET ──► ATTACK
 *  ▲              │                  │               │
 *  └──── REST ◄───┴───────────────── ┴───(RETREAT)──┘
 * </pre>
 *
 * Dead is not a state — it is detected via {@code player.isDead()} in
 * {@link org.l2jmobius.gameserver.bot.core.BotController} before the normal pipeline runs.
 */
public enum BotState
{
	/** No task — brief pause between decisions. */
	IDLE,

	/** Scanning the farm zone for the next target or loot. */
	SEARCH_TARGET,

	/** Moving: walking to city waypoints, teleporting to farm, or approaching a target. */
	MOVE_TO_TARGET,

	/** Actively fighting the current target. */
	ATTACK,

	/** In city after a sell/restock trip — waiting for the rest timer. */
	REST
}
