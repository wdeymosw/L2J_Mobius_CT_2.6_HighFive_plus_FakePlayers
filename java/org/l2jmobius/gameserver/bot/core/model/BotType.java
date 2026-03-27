/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.model;

/**
 * Type of bot behaviour.
 * CORE — active farmer, hunts mobs, gains XP.
 * NOISE — ambient bot, walks around, creates population feel.
 */
public enum BotType
{
	CORE,
	NOISE
}
