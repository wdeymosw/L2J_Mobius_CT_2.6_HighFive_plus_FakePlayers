/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.brain;

/**
 * Decision produced by {@link BotBrain} each tick.
 * Brain only decides — it does not act.
 */
public enum BotDecision
{
	/** No target found — keep looking. */
	SEARCH_TARGET,

	/** Valid target exists — engage. */
	ATTACK_TARGET,

	/** Leave combat: low HP, inventory full, or out of ammo. */
	RETREAT,

	/** Nothing to do — wait. */
	IDLE
}
