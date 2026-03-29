/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.model;

/**
 * Tactical operating states of a bot within the {@link BotPhase#FARMING} phase.
 * <p>
 * State encodes what the bot perceives about its immediate environment and what it
 * is currently doing at the tactical level.
 * {@link org.l2jmobius.gameserver.bot.core.brain.BotBrain} maps each state to a
 * {@link org.l2jmobius.gameserver.bot.core.brain.BotIntention}.
 * {@link org.l2jmobius.gameserver.bot.core.BotController} owns all state transitions.
 * <p>
 * Strategic phases (travelling to farm/city, resting in city) are tracked via
 * {@link BotPhase}, not here.
 *
 * <pre>
 *                    ┌──────────────────────────────────────┐
 *                    │  (critical: HP/inv/ammo/overweight)  │
 *                    ▼                                      │
 * IDLE ──► SEARCH_TARGET ──► ATTACK ──► (target dead) ──► SEARCH_TARGET
 *               │
 *               │  (attacked)
 *               ▼
 *           DEFENDING ──► (attacker dead) ──► SEARCH_TARGET
 *               │
 *               └── (critical) ──► RETREATING
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

	/** Actively fighting the chosen target (includes approaching it). */
	ATTACK,

	/**
	 * Reacting to an unprovoked attack.
	 * BotController sets this when a mob targets the bot while it is not already in ATTACK.
	 * On entry: queue is cleared, attacker is locked as new target.
	 * After the attacker dies → SEARCH_TARGET.
	 */
	DEFENDING,

	/**
	 * Critical condition detected (low HP / full inventory / out of ammo / overweight / outnumbered).
	 * BotController sets this based on the current {@link BotPerception}.
	 * Brain returns RETREAT → BotController calls enterTravel(TRAVELING_BACK).
	 */
	RETREATING
}
