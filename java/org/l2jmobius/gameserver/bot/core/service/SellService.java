/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;

import org.l2jmobius.gameserver.bot.core.model.BotInstance;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.item.ItemTemplate;
import org.l2jmobius.gameserver.model.item.enums.ItemGrade;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.model.item.type.EtcItemType;
import org.l2jmobius.gameserver.model.itemcontainer.Inventory;

/**
 * Sells items when a bot visits the city.
 * <p>
 * Strategy:
 * <ol>
 *   <li>Identify <em>valuable</em> items: A/S-grade weapons/armour and recipes.</li>
 *   <li>Keep the top {@value #MAX_VALUABLE_KEEP} most expensive valuables (by
 *       reference price) — they will be listed in a player private store by
 *       {@link ShopService}.</li>
 *   <li>Sell everything else (trash and excess valuables) to a virtual NPC at
 *       {@value #SELL_RATE} × reference price.</li>
 * </ol>
 * Selling is virtual (no NPC walk required).
 */
public class SellService
{
	private static final Logger LOGGER = Logger.getLogger(SellService.class.getName());

	/** NPC sell price = reference price × this multiplier (retail ≈ 50 %). */
	private static final double SELL_RATE = 0.5;

	/** Maximum number of valuable items kept for the private store. */
	static final int MAX_VALUABLE_KEEP = 4;

	private SellService()
	{
	}

	/**
	 * Returns {@code true} if the bot should head to the shop now.
	 *
	 * @param bot the bot to check
	 * @return true if inventory is ≥ 90 % full
	 */
	public static boolean needsSell(BotInstance bot)
	{
		return !bot.getPlayer().isInventoryUnder90(false);
	}

	/**
	 * Sells all items except equipped gear, adena, and the top
	 * {@value #MAX_VALUABLE_KEEP} most expensive valuables.
	 *
	 * @param bot the bot selling items
	 */
	public static void sell(BotInstance bot)
	{
		final Player player = bot.getPlayer();

		// Snapshot to avoid ConcurrentModificationException.
		final List<Item> all = new ArrayList<>(player.getInventory().getItems());

		// Collect and sort valuables by reference price descending.
		final List<Item> valuables = new ArrayList<>();
		for (Item item : all)
		{
			if (!item.isEquipped() && isValuable(item))
			{
				valuables.add(item);
			}
		}
		valuables.sort(Comparator.comparingLong((Item i) -> i.getTemplate().getReferencePrice()).reversed());

		// Items to keep: first MAX_VALUABLE_KEEP by price.
		final int keepCount = Math.min(valuables.size(), MAX_VALUABLE_KEEP);

		long totalEarned = 0;
		int soldCount = 0;

		for (Item item : all)
		{
			if (item.isEquipped())
			{
				continue;
			}
			if ((item.getId() == Inventory.ADENA_ID) || (item.getId() == Inventory.ANCIENT_ADENA_ID))
			{
				continue;
			}

			// Keep top-N valuables; sell the rest (including excess valuables).
			if (isValuable(item) && (valuables.indexOf(item) < keepCount))
			{
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
			LOGGER.info("SellService: " + player.getName() + " sold " + soldCount + " item(s), earned " + totalEarned + " adena"
				+ (keepCount > 0 ? ", kept " + keepCount + " valuable(s) for shop" : "") + ".");
		}
	}

	// -------------------------------------------------------------------------

	/**
	 * Returns {@code true} for items the bot should not sell to an NPC:
	 * A/S-grade weapons/armour and recipes.
	 *
	 * @param item the item to classify
	 * @return true if valuable
	 */
	static boolean isValuable(Item item)
	{
		final ItemTemplate template = item.getTemplate();

		if (template.getItemType() == EtcItemType.RECIPE)
		{
			return true;
		}

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
