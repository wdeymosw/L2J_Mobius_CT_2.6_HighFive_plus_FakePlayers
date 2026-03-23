/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.model;

/**
 * Maps a Lineage II class ID to a {@link BotRole}.
 * <p>
 * Class IDs come from classList.xml. Only the active (highest) class matters;
 * base/first-class IDs map to the same role as their promoted equivalents.
 */
public class RoleResolver
{
	private RoleResolver()
	{
	}

	/**
	 * Resolves the bot role for the given active class ID.
	 *
	 * @param classId value of {@code Player.getActiveClass()}
	 * @return the role, never null — unknown IDs return {@link BotRole#UNKNOWN}
	 */
	public static BotRole resolve(int classId)
	{
		switch (classId)
		{
			// ----------------------------------------------------------------
			// MELEE — warriors, fighters, orc melee, Kamael melee
			// ----------------------------------------------------------------
			case 0:   // Human Fighter
			case 1:   // Warrior
			case 2:   // Gladiator
			case 3:   // Warlord
			case 18:  // Elven Fighter
			case 31:  // Dark Fighter
			case 44:  // Orc Fighter
			case 45:  // Orc Raider
			case 46:  // Destroyer
			case 47:  // Monk
			case 48:  // Tyrant
			case 88:  // Duelist
			case 89:  // Dreadnought
			case 113: // Titan
			case 114: // Grand Khavatari
			case 123: // Male Kamael Soldier
			case 124: // Female Kamael Soldier
			case 125: // Trooper
			case 126: // Warder
			case 127: // Berserker
			case 131: // Doombringer
				return BotRole.MELEE;

			// ----------------------------------------------------------------
			// ARCHER — scouts, rangers, assassins
			// ----------------------------------------------------------------
			case 7:   // Rogue
			case 8:   // Treasure Hunter
			case 9:   // Hawkeye
			case 22:  // Elven Scout
			case 23:  // Plains Walker
			case 24:  // Silver Ranger
			case 35:  // Assassin
			case 36:  // Abyss Walker
			case 37:  // Phantom Ranger
			case 92:  // Sagittarius
			case 93:  // Adventurer
			case 101: // Wind Rider
			case 102: // Moonlight Sentinel
			case 108: // Ghost Hunter
			case 109: // Ghost Sentinel
			case 130: // Arbalester
			case 134: // Trickster
				return BotRole.ARCHER;

			// ----------------------------------------------------------------
			// MAGE — wizards, spellhowlers, necromancers
			// ----------------------------------------------------------------
			case 10:  // Human Mystic
			case 11:  // Human Wizard
			case 12:  // Sorcerer
			case 13:  // Necromancer
			case 25:  // Elven Mystic
			case 26:  // Elven Wizard
			case 27:  // Spellsinger
			case 38:  // Dark Mystic
			case 39:  // Dark Wizard
			case 40:  // Spellhowler
			case 94:  // Archmage
			case 95:  // Soultaker
			case 103: // Mystic Muse
			case 110: // Storm Screamer
				return BotRole.MAGE;

			// ----------------------------------------------------------------
			// HEALER — clerics, bishops, elders
			// ----------------------------------------------------------------
			case 15:  // Cleric
			case 16:  // Bishop
			case 29:  // Elven Oracle
			case 30:  // Elven Elder
			case 42:  // Shillien Oracle
			case 43:  // Shillien Elder
			case 97:  // Cardinal
			case 105: // Eva's Saint
			case 112: // Shillien Saint
				return BotRole.HEALER;

			// ----------------------------------------------------------------
			// BUFFER — prophets, overlords, dancers, singers, Soul Hounds
			// ----------------------------------------------------------------
			case 17:  // Prophet
			case 49:  // Orc Mystic
			case 50:  // Orc Shaman
			case 51:  // Overlord
			case 52:  // Warcryer
			case 21:  // Sword Singer
			case 34:  // Bladedancer
			case 98:  // Hierophant
			case 100: // Sword Muse
			case 107: // Spectral Dancer
			case 115: // Dominator
			case 116: // Doom Cryer
			case 128: // Male Soul Breaker
			case 129: // Female Soul Breaker
			case 132: // Male Soul Hound
			case 133: // Female Soul Hound
			case 135: // Inspector
			case 136: // Judicator
				return BotRole.BUFFER;

			// ----------------------------------------------------------------
			// SUMMONER — pet-based classes
			// ----------------------------------------------------------------
			case 14:  // Warlock
			case 28:  // Elemental Summoner
			case 41:  // Phantom Summoner
			case 96:  // Arcana Lord
			case 104: // Elemental Master
			case 111: // Spectral Master
				return BotRole.SUMMONER;

			// ----------------------------------------------------------------
			// TANK — knights, heavy armour tanks
			// ----------------------------------------------------------------
			case 4:   // Human Knight
			case 5:   // Paladin
			case 6:   // Dark Avenger
			case 19:  // Elven Knight
			case 20:  // Temple Knight
			case 32:  // Palus Knight
			case 33:  // Shillien Knight
			case 90:  // Phoenix Knight
			case 91:  // Hell Knight
			case 99:  // Eva's Templar
			case 106: // Shillien Templar
				return BotRole.TANK;

			// ----------------------------------------------------------------
			// CRAFTER — dwarves
			// ----------------------------------------------------------------
			case 53:  // Dwarf Fighter
			case 54:  // Scavenger
			case 55:  // Bounty Hunter
			case 56:  // Artisan
			case 57:  // Warsmith
			case 117: // Fortune Seeker
			case 118: // Maestro
				return BotRole.CRAFTER;

			default:
				return BotRole.UNKNOWN;
		}
	}
}
