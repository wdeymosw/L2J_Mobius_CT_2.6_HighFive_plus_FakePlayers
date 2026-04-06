/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.service;

import java.util.logging.Logger;

import org.l2jmobius.gameserver.bot.core.model.BotInstance;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.item.enums.ItemGrade;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;

/**
 * Provides grade-appropriate gear sets for bots by role.
 * <p>
 * Set definitions are loaded from {@code data/bot/BotGearSets.xml} via {@link BotGearSetData}.
 * <p>
 * After giving items, call {@link EquipService#equip(BotInstance)} to put them on.
 */
public class GearService
{
	private static final Logger LOGGER = Logger.getLogger(GearService.class.getName());

	private GearService()
	{
	}

	// -------------------------------------------------------------------------
	// Public API
	// -------------------------------------------------------------------------

	/**
	 * Gives a complete grade-appropriate gear set to the bot based on its role.
	 * Items are added to inventory; call {@link EquipService#equip(BotInstance)} afterwards.
	 *
	 * @param bot   the bot to equip
	 * @param grade desired gear grade (D/C/B/A)
	 */
	public static void giveGearSet(BotInstance bot, ItemGrade grade)
	{
		final BotGearSetData data = BotGearSetData.getInstance();
		final int[] gear = data.getGearSet(bot.getRole(), grade);
		if (gear == null)
		{
			LOGGER.warning("GearService: no gear set defined for role=" + bot.getRole() + " grade=" + grade);
			return;
		}

		final Player player = bot.getPlayer();
		giveIfValid(player, gear[BotGearSetData.WEAPON],  "weapon");
		giveIfValid(player, gear[BotGearSetData.OFFHAND], "offhand");
		giveIfValid(player, gear[BotGearSetData.CHEST],   "chest");
		giveIfValid(player, gear[BotGearSetData.LEGS],    "legs");
		giveIfValid(player, gear[BotGearSetData.GLOVES],  "gloves");
		giveIfValid(player, gear[BotGearSetData.BOOTS],   "boots");
		giveIfValid(player, gear[BotGearSetData.HELMET],  "helmet");

		final int[] jewelry = data.getJewelrySet(grade);
		if (jewelry != null)
		{
			giveIfValid(player, jewelry[BotGearSetData.EARRING],  "earring1");
			giveIfValid(player, jewelry[BotGearSetData.EARRING],  "earring2");
			giveIfValid(player, jewelry[BotGearSetData.RING],     "ring1");
			giveIfValid(player, jewelry[BotGearSetData.RING],     "ring2");
			giveIfValid(player, jewelry[BotGearSetData.NECKLACE], "necklace");
		}

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

	private static void giveIfValid(Player player, int itemId, String slot)
	{
		if (itemId <= 0)
		{
			return;
		}
		player.getInventory().addItem(ItemProcessType.REWARD, itemId, 1, player, null);
	}
}
