/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.brain;

/**
 * Intention produced by {@link BotBrain} each tick.
 * Expresses what the bot wants to do — Brain only decides, it does not act.
 */
public enum BotIntention
{
	/** No target found — keep looking. */
	SEARCH_TARGET,

	/** Valid target exists — engage (in attack range). */
	ATTACK_TARGET,

	/** Valid target exists but out of attack range — close the distance. */
	APPROACH_TARGET,

	/** Leave combat: low HP, inventory full, or out of ammo. */
	RETREAT,

	/** HP is dropping — use a healing potion before it gets critical. */
	USE_POTION,

	/** Nothing to do — wait. */
	IDLE,

	/** HP or MP is critically low — pause and wait for regeneration. */
	RECOVER
}
