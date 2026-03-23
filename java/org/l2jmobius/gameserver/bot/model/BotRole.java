/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.model;

/**
 * Combat role of a bot character, derived from its active class.
 * Used by ThinkService, SkillService, and future party/support logic
 * to pick the right actions for the class.
 */
public enum BotRole
{
	/** Melee DPS — warriors, gladiators, destroyers, etc. */
	MELEE,

	/** Ranged physical — archers, rogues, assassins, etc. */
	ARCHER,

	/** Magic DPS — sorcerers, spellhowlers, necromancers, etc. */
	MAGE,

	/** Primary healers — bishop, cardinal, elven/dark elders, etc. */
	HEALER,

	/** Buffers / support — prophet, overlord, warcryer, dancers, singers, etc. */
	BUFFER,

	/** Pet-based classes — warlock, summoners, etc. */
	SUMMONER,

	/** Tanks / heavy knights — paladin, dark avenger, temple knight, etc. */
	TANK,

	/** Dwarf crafters / spoilers. */
	CRAFTER,

	/** Fallback for unrecognised class IDs. */
	UNKNOWN
}
