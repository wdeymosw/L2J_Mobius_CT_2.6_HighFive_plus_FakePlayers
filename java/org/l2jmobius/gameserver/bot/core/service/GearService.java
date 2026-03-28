/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.service;

import java.util.logging.Logger;

import org.l2jmobius.gameserver.bot.core.model.BotInstance;
import org.l2jmobius.gameserver.bot.core.model.BotRole;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.item.enums.ItemGrade;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;

/**
 * Provides grade-appropriate gear sets for bots by role.
 * <p>
 * Gear roles:
 *   TANK    — heavy armor + 1H sword + shield
 *   FIGHTER — light/heavy armor + sword (no offhand)
 *   ARCHER  — light armor + bow
 *   CASTER  — robe + staff/blunt (MAGE, HEALER, BUFFER, SUMMONER)
 * <p>
 * Grades: D → C → B → A. S-grade is never given to bots.
 * <p>
 * After giving items, call {@link EquipService#equip(BotInstance)} to put them on.
 * Item IDs are mid-tier — intentionally NOT top/masterwork gear.
 * All IDs verified against dist/game/data/stats/items/*.xml.
 */
public class GearService
{
	private static final Logger LOGGER = Logger.getLogger(GearService.class.getName());

	// -------------------------------------------------------------------------
	// Gear set slots: [weapon, offhand, chest, legs, gloves, boots, helmet]
	// offhand = 0  → no offhand
	// legs    = 0  → onepiece armor (chest covers both)
	// -------------------------------------------------------------------------
	private static final int WEAPON  = 0;
	private static final int OFFHAND = 1;
	private static final int CHEST   = 2;
	private static final int LEGS    = 3;
	private static final int GLOVES  = 4;
	private static final int BOOTS   = 5;
	private static final int HELMET  = 6;

	// -------------------------------------------------------------------------
	// TANK  (HEAVY armor + 1H sword + shield)
	// -------------------------------------------------------------------------
	private static final int[][] TANK_SETS =
	{
		// D-grade
		{  123,  626,  352,  377,  604,   40,   45 }, // Saber / Bronze Shield / Brigandine Tunic(HEAVY) / Scale Gaiters(HEAVY) / Crafted Leather Gloves / Leather Boots / Bone Helmet
		// C-grade
		{   74,  107,   60,    0,   61,   64,  517 }, // Katana(1H) / Composite Shield / Composite Armor(HEAVY onepiece) / 0 / Mithril Plate Gloves / Composite Boots / Composite Helmet
		// B-grade
		{   79,  673, 2376, 2379,    0, 2439, 2415 }, // Sword of Damascus(1H) / Avadon Shield / Avadon Breastplate(HEAVY) / Avadon Gaiters(HEAVY) / 0 / Sealed Blue Wolf Boots / Avadon Circlet
		// A-grade
		{ 2500,  673,  365,  388,    0, 2440, 2418 }, // Dark Legion's Edge(1H) / Avadon Shield / Dark Crystal Breastplate(HEAVY) / Dark Crystal Gaiters(HEAVY) / 0 / Boots of Nightmare / Helm of Nightmare
	};

	// -------------------------------------------------------------------------
	// FIGHTER  (LIGHT/HEAVY armor + sword, no offhand)
	// -------------------------------------------------------------------------
	private static final int[][] FIGHTER_SETS =
	{
		// D-grade
		{  123,    0,  394,  416,  604,   40,   45 }, // Saber / / Reinforced Leather Shirt(LIGHT) / Reinforced Leather Gaiters(LIGHT) / Crafted Leather Gloves / Leather Boots / Bone Helmet
		// C-grade
		{   75,    0,   60,    0,   61,   64,  517 }, // Caliburs(1H) / / Composite Armor(HEAVY onepiece) / 0 / Mithril Plate Gloves / Composite Boots / Composite Helmet
		// B-grade
		{   79,    0, 2391,    0,    0, 2439, 2416 }, // Sword of Damascus(1H) / / Blue Wolf Leather Armor(LIGHT onepiece) / 0 / 0 / Sealed Blue Wolf Boots / Blue Wolf Helmet
		// A-grade
		{ 2500,    0, 2385, 2389,    0, 2441, 2418 }, // Dark Legion's Edge(1H) / / Dark Crystal Leather Armor(LIGHT) / Dark Crystal Leggings(LIGHT) / 0 / Dark Legion Boots / Helm of Nightmare
	};

	// -------------------------------------------------------------------------
	// ARCHER  (LIGHT armor + bow — NO offhand, bow is lrhand)
	// -------------------------------------------------------------------------
	private static final int[][] ARCHER_SETS =
	{
		// D-grade
		{  274,    0,  394,  416,  604,   40,   45 }, // Reinforced Bow / / Reinforced Leather Shirt(LIGHT) / Reinforced Leather Gaiters(LIGHT) / Crafted Leather Gloves / Leather Boots / Bone Helmet
		// C-grade
		{  273,    0,  398,  418,   61, 2431,  499 }, // Composite Bow / / Plated Leather(LIGHT) / Plated Leather Gaiters(LIGHT) / Mithril Plate Gloves / Plated Leather Boots / Mithril Helmet
		// B-grade
		{  284,    0, 2391,    0,    0, 2439, 2416 }, // Dark Elven Long Bow / / Blue Wolf Leather Armor(LIGHT onepiece) / 0 / 0 / Sealed Blue Wolf Boots / Blue Wolf Helmet
		// A-grade
		{  288,    0, 2385, 2389,    0,  563, 2419 }, // Carnage Bow / / Dark Crystal Leather Armor(LIGHT) / Dark Crystal Leggings(LIGHT) / 0 / Dark Crystal Boots / Majestic Circlet
	};

	// -------------------------------------------------------------------------
	// CASTER  (MAGIC robe + staff/blunt — mage, healer, buffer, summoner)
	// -------------------------------------------------------------------------
	private static final int[][] CASTER_SETS =
	{
		// D-grade
		{  178,    0,  432,  465,  604,   40,   45 }, // Bone Staff / / Cursed Tunic(MAGIC) / Cursed Stockings(MAGIC) / Crafted Leather Gloves / Leather Boots / Bone Helmet
		// C-grade
		{ 2503,    0,  439,  471, 2454, 2430,  549 }, // Yaksa Mace / / Karmian Tunic(MAGIC) / Karmian Stockings(MAGIC) / Karmian Gloves / Karmian Boots / Helm of Avadon
		// B-grade (weapon is C-grade Yaksa Mace — TODO: replace with B-grade blunt)
		{ 2503,    0, 2406,    0,    0, 2439, 2415 }, // Yaksa Mace / / Avadon Robe(MAGIC onepiece) / 0 / 0 / Sealed Blue Wolf Boots / Avadon Circlet
		// A-grade
		{ 2504,    0, 2408,    0,    0, 2440, 2419 }, // Meteor Shower / / Robe of Nightmare(MAGIC onepiece) / 0 / 0 / Boots of Nightmare / Majestic Circlet
	};

	// -------------------------------------------------------------------------
	// Grade index helper
	// -------------------------------------------------------------------------
	private static int gradeIndex(ItemGrade grade)
	{
		if (grade == null)
		{
			return 0;
		}
		switch (grade)
		{
			case C: return 1;
			case B: return 2;
			case A: return 3;
			default: return 0; // D or NONE
		}
	}

	private GearService()
	{
	}

	// -------------------------------------------------------------------------
	// Public API
	// -------------------------------------------------------------------------

	/**
	 * Gives a complete grade-appropriate gear set to the bot based on its role.
	 * Items are added to inventory; call {@link EquipService#equip(BotInstance)} afterwards.
	 * S-grade is never given regardless of the requested grade.
	 *
	 * @param bot   the bot to equip
	 * @param grade desired gear grade (D/C/B/A)
	 */
	public static void giveGearSet(BotInstance bot, ItemGrade grade)
	{
		final int[] set = selectSet(bot.getRole(), grade);
		if (set == null)
		{
			LOGGER.warning("GearService: no gear set defined for role=" + bot.getRole() + " grade=" + grade);
			return;
		}

		final Player player = bot.getPlayer();
		giveIfValid(player, set[WEAPON],  "weapon");
		giveIfValid(player, set[OFFHAND], "offhand");
		giveIfValid(player, set[CHEST],   "chest");
		giveIfValid(player, set[LEGS],    "legs");
		giveIfValid(player, set[GLOVES],  "gloves");
		giveIfValid(player, set[BOOTS],   "boots");
		giveIfValid(player, set[HELMET],  "helmet");

		LOGGER.info("GearService: gave " + grade + "-grade gear set to " + player.getName() + " (" + bot.getRole() + ")");
	}

	/** Shortcut for D-grade starter gear at bot creation. */
	public static void giveStarterGear(BotInstance bot)
	{
		giveGearSet(bot, ItemGrade.D);
	}

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	private static int[] selectSet(BotRole role, ItemGrade grade)
	{
		final int[][] sets;
		switch (role)
		{
			case TANK:
				sets = TANK_SETS;
				break;
			case ARCHER:
				sets = ARCHER_SETS;
				break;
			case MAGE:
			case HEALER:
			case BUFFER:
			case SUMMONER:
				sets = CASTER_SETS;
				break;
			case MELEE:
			case CRAFTER:
			default:
				sets = FIGHTER_SETS;
				break;
		}
		final int idx = gradeIndex(grade);
		return (idx < sets.length) ? sets[idx] : sets[sets.length - 1];
	}

	private static void giveIfValid(Player player, int itemId, String slot)
	{
		if (itemId <= 0)
		{
			return; // 0 = no item for this slot
		}
		player.getInventory().addItem(ItemProcessType.REWARD, itemId, 1, player, null);
	}
}
