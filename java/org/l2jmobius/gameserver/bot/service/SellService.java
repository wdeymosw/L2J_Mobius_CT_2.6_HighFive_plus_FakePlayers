/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.service;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.l2jmobius.gameserver.bot.model.BotInstance;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.item.ItemTemplate;
import org.l2jmobius.gameserver.model.item.enums.ItemGrade;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.model.item.type.EtcItemType;
import org.l2jmobius.gameserver.model.itemcontainer.Inventory;

/**
 * Sells "trash" items to an imaginary NPC shop and returns adena to the bot.
 * <p>
 * Items are split into two categories:
 * <ul>
 *   <li><b>Trash</b> — sold immediately at 50 % of reference price.</li>
 *   <li><b>Valuable</b> — kept in inventory for a future private-shop stage
 *       (A/S-grade weapons/armour, recipes).</li>
 * </ul>
 * Selling is virtual (no NPC walk required). This keeps the service simple
 * and reliable; real NPC interaction can be layered on top later.
 */
public class SellService
{
	private static final Logger LOGGER = Logger.getLogger(SellService.class.getName());

	/** NPC sell price = reference price × this multiplier (retail is ~50 %). */
	private static final double SELL_RATE = 0.5;

	private SellService()
	{
	}

	/**
	 * Returns {@code true} if the bot should head to the shop now.
	 * Triggered when inventory slots reach 90 % capacity.
	 *
	 * @param bot the bot to check
	 * @return true if inventory is >= 90 % full
	 */
	public static boolean needsSell(BotInstance bot)
	{
		return !bot.getPlayer().isInventoryUnder90(false);
	}

	/**
	 * Sells all trash items from the bot's inventory and credits adena.
	 * Valuable items (A/S gear, recipes) are left untouched for the
	 * private-shop stage.
	 *
	 * @param bot the bot selling items
	 * @return true if any valuable items remain that warrant a private shop
	 */
	public static boolean sell(BotInstance bot)
	{
		final Player player = bot.getPlayer();
		long totalEarned = 0;
		int soldCount = 0;
		boolean hasValuables = false;

		// Snapshot to avoid ConcurrentModificationException.
		final List<Item> snapshot = new ArrayList<>(player.getInventory().getItems());

		for (Item item : snapshot)
		{
			if (item.isEquipped())
			{
				continue;
			}

			// Never sell adena.
			if ((item.getId() == Inventory.ADENA_ID) || (item.getId() == Inventory.ANCIENT_ADENA_ID))
			{
				continue;
			}

			if (isValuable(item))
			{
				hasValuables = true;
				continue;
			}

			final long price = (long) (item.getTemplate().getReferencePrice() * SELL_RATE);
			final long earned = price * item.getCount();
			player.destroyItem(ItemProcessType.SELL, item, null, false);
			if (earned > 0)
			{
				player.addAdena(ItemProcessType.SELL, earned, null, false);
				totalEarned += earned;
			}
			soldCount++;
		}

		if (soldCount > 0)
		{
			LOGGER.info("SellService: " + player.getName()
				+ " sold " + soldCount + " item(s), earned " + totalEarned + " adena."
				+ (hasValuables ? " Valuable loot remains for private shop." : ""));
		}

		return hasValuables;
	}

	// -------------------------------------------------------------------------
	// Classification
	// -------------------------------------------------------------------------

	/**
	 * Returns {@code true} for items the bot should NOT sell to an NPC
	 * (high-grade gear and recipes — saved for a private shop).
	 *
	 * @param item the item to classify
	 * @return true if valuable (keep), false if trash (sell)
	 */
	private static boolean isValuable(Item item)
	{
		final ItemTemplate template = item.getTemplate();

		// Recipes → private shop.
		if (template.getItemType() == EtcItemType.RECIPE)
		{
			return true;
		}

		// A/S-grade weapons and armour → private shop.
		if (item.isWeapon() || item.isArmor())
		{
			final ItemGrade grade = template.getItemGrade();
			if ((grade == ItemGrade.A) || (grade == ItemGrade.S))
			{
				return true;
			}
		}

		return false;
	}
}
