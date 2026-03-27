/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.model;

/**
 * Режим бота — назначается BotManager'ом и может меняться.
 * ACTIVE  — бот активно фармит мобов, набирает опыт.
 * PASSIVE — бот в пассивном режиме: ходит по городу, создаёт население.
 */
public enum BotType
{
	ACTIVE,
	PASSIVE
}
