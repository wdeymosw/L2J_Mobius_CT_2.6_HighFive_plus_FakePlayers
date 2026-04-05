/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.behaviour;

/**
 * Events that can trigger behaviour transitions.
 * Dispatched via {@link BehaviourController#dispatch(BotBehaviourEvent, org.l2jmobius.gameserver.bot.core.model.BotInstance, long, Object)}.
 */
public enum BotBehaviourEvent
{
	/** A player invited the bot to a party (payload: BotRole — the assigned role). */
	PARTY_INVITE,

	/** The party was dissolved. */
	PARTY_DISSOLVE,

	/** A flagged player attacked the bot. */
	PVP_ATTACKED,

	/** Bot PvP flag expired or was cleared. */
	PVP_FLAG_CLEARED,

	/** PvE session timer expired — bot should head to city. */
	FARM_SESSION_EXPIRED,

	/** City idle timer expired — bot should return to farm zone. */
	CITY_SESSION_EXPIRED,

	/** Private shop session ended — PASSIVE bot may switch mode. */
	SHOP_SESSION_EXPIRED,
}
