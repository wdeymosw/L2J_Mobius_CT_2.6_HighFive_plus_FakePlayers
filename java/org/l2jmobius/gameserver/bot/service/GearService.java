/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.service;

import java.util.logging.Logger;

import org.l2jmobius.gameserver.bot.model.BotInstance;
import org.l2jmobius.gameserver.bot.model.BotRole;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.item.enums.ItemGrade;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;

/**
 * Provides grade-appropriate gear sets for bots by role.
 * <p>
 * Gear roles:
 *   TANK    — heavy armor + sword + shield
 *   FIGHTER — heavy armor + sword or dagger (MELEE, CRAFTER)
 *   ARCHER  — light armor + bow
 *   CASTER  — robe + staff/blunt (MAGE, HEALER, BUFFER, SUMMONER)
 * <p>
 * Grades: D → C → B → A. S-grade is never given to bots.
 * <p>
 * After giving items, call {@link EquipService#equip(BotInstance)} to put them on.
 * Item IDs are mid-tier — intentionally NOT top/masterwork gear.
 */
public class GearService
{
	private static final Logger LOGGER = Logger.getLogger(GearService.class.getName());

	// -------------------------------------------------------------------------
	// Gear set slots: [weapon, offhand, chest, legs, gloves, boots, helmet]
	// offhand = 0  → no offhand
	// chest   = -1 → onepiece armor (legs slot unused)
	// -------------------------------------------------------------------------
	private static final int WEAPON  = 0;
	private static final int OFFHAND = 1;
	private static final int CHEST   = 2;
	private static final int LEGS    = 3;
	private static final int GLOVES  = 4;
	private static final int BOOTS   = 5;
	private static final int HELMET  = 6;

	// -------------------------------------------------------------------------
	// TANK  (heavy armor + 1H sword + shield)
	// -------------------------------------------------------------------------
	private static final int[][] TANK_SETS =
	{
		// D-grade
		{ 123,  626,  432,  413,  604,   40,   45 }, // Saber / Bronze Shield / mid D set
		// C-grade
		{  49,  107,   60,    0,   61,   64,  517 }, // (C sword TBD) / Composite Shield / Composite Armor (onepiece→0 legs)
		// B-grade
		{ 218,  673, 2376, 2379,    0, 2439, 2415 }, // (B sword TBD) / Avadon Shield / Avadon heavy set
		// A-grade
		{2500,  673,    0,    0,    0, 2440, 2418 }, // Dark Legion's Edge / Avadon Shield / (A heavy TBD)
	};

	// -------------------------------------------------------------------------
	// FIGHTER  (heavy/light armor + sword or dagger)
	// -------------------------------------------------------------------------
	private static final int[][] FIGHTER_SETS =
	{
		// D-grade
		{ 123,    0,  432,  413,  604,   40,   45 }, // Saber / no offhand / mid D set
		// C-grade
		{  49,    0,   60,    0,   61,   64,  517 }, // Composite Armor onepiece
		// B-grade
		{ 218,    0, 2391,    0,    0, 2439, 2416 }, // Blue Wolf Leather Armor onepiece
		// A-grade
		{2500,    0,    0,    0,    0, 2441, 2418 }, // Dark Legion's Edge / (A light TBD)
	};

	// -------------------------------------------------------------------------
	// ARCHER  (light armor + bow — NO offhand, bow is lrhand)
	// -------------------------------------------------------------------------
	private static final int[][] ARCHER_SETS =
	{
		// D-grade
		{ 274,    0,  432,  413,  604,   40,   45 }, // Reinforced Bow / mid D light set
		// C-grade
		{ 273,    0,  398,  418,   61, 2431,  499 }, // Composite Bow / Plated Leather set
		// B-grade
		{   0,    0, 2391,    0,    0, 2439, 2416 }, // (B bow TBD) / Blue Wolf Leather
		// A-grade
		{   0,    0,    0,    0,    0, 2443, 2419 }, // (A bow TBD) / Dragon Leather Boots
	};

	// -------------------------------------------------------------------------
	// CASTER  (robe + 2H staff/blunt — mage, healer, buffer, summoner)
	// -------------------------------------------------------------------------
	private static final int[][] CASTER_SETS =
	{
		// D-grade
		{ 178,    0,  432,  413,  604,   40,   45 }, // Bone Staff / mid D set (robes TBD at D)
		// C-grade
		{2503,    0,   -1,    0,   61, 2430,  549 }, // Yaksa Mace / Karmian Boots / Helm of Avadon (-1 = robe onepiece TBD)
		// B-grade
		{2503,    0,2406,    0,    0, 2439, 2415 }, // Yaksa Mace / Avadon Robe (onepiece)
		// A-grade
		{2504,    0,2408,    0,    0, 2440, 2419 }, // Meteor Shower / Robe of Nightmare
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
			return; // 0 = no item for this slot, -1 = onepiece (legs not needed)
		}
		player.getInventory().addItem(ItemProcessType.REWARD, itemId, 1, player, null);
	}
}
