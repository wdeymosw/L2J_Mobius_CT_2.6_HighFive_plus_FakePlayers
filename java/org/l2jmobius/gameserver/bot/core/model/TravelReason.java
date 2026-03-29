/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.model;

/**
 * @deprecated Superseded by {@link BotPhase}. Travel direction is now encoded in the phase:
 *             {@link BotPhase#TRAVELING_OUT} (→ farm) and {@link BotPhase#TRAVELING_BACK} (→ city).
 */
@Deprecated
public enum TravelReason
{
}
