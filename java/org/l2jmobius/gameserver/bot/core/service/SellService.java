/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.service;

import java.util.ArrayList;
import org.l2jmobius.gameserver.bot.core.goap.GoapTuning;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;

import org.l2jmobius.gameserver.bot.core.model.BotInstance;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.item.ItemTemplate;
import org.l2jmobius.gameserver.model.item.enums.ItemGrade;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.model.item.type.CrystalType;
import org.l2jmobius.gameserver.model.item.type.EtcItemType;
import org.l2jmobius.gameserver.model.itemcontainer.Inventory;

/**
 * Sells items when a bot visits the city.
 * <p>
 * Strategy:
 * <ol>
 *   <li>Identify <em>valuable</em> items: B/A/S-grade weapons/armour, craft
 *       materials and crystals; recipes of any grade.</li>
 *   <li>Among valuables, discard those whose reference price is below
 *       {@value #MIN_SHOP_PRICE} — not worth a shop slot.</li>
 *   <li>Keep up to {@code player.getPrivateSellStoreLimit()} of the most
 *       expensive remaining valuables for {@link ShopService}.</li>
 *   <li>Sell everything else (trash and overflow valuables) to a virtual NPC
 *       at {@value #SELL_RATE} × reference price.</li>
 * </ol>
 */
public class SellService
{
	private static final Logger LOGGER = Logger.getLogger(SellService.class.getName());

	/** NPC sell price = reference price × this multiplier (retail ≈ 50 %). */
	private static final double SELL_RATE = GoapTuning.SELL_RATE;

	/**
	 * Minimum reference price for an item to be worth listing in a private store.
	 * Items below this threshold are sold to NPC even if technically "valuable".
	 */
	private static final long MIN_SHOP_PRICE = GoapTuning.MIN_SHOP_PRICE;

	private SellService()
	{
	}

	/** Weight threshold (fraction of max load) at which the bot goes to sell. */
	private static final double WEIGHT_SELL_THRESHOLD = GoapTuning.WEIGHT_SELL_THRESHOLD;

	/**
	 * Returns {@code true} if the bot should head to the shop now.
	 * Triggers when inventory slots are ≥ 90% full OR weight exceeds 60% of max load.
	 *
	 * @param bot the bot to check
	 * @return true if inventory needs selling
	 */
	public static boolean needsSell(BotInstance bot)
	{
		final Player player = bot.getPlayer();
		// Slot-based check (original).
		if (!player.isInventoryUnder90(false))
		{
			return true;
		}
		// Weight-based check — sell before becoming overloaded.
		final int maxLoad = player.getMaxLoad();
		return (maxLoad > 0) && (player.getCurrentLoad() > maxLoad * WEIGHT_SELL_THRESHOLD);
	}

	/**
	 * Sells all items except equipped gear, adena, and the top-N most expensive
	 * shop-worthy valuables (N = player's private store slot limit).
	 *
	 * @param bot the bot selling items
	 */
	public static void sell(BotInstance bot)
	{
		final Player player = bot.getPlayer();
		final List<Item> all = new ArrayList<>(player.getInventory().getItems());

		// Collect valuables that meet the price floor, sorted most expensive first.
		final List<Item> keepCandidates = new ArrayList<>();
		for (Item item : all)
		{
			if (!item.isEquipped() && isValuable(item) && (item.getTemplate().getReferencePrice() >= MIN_SHOP_PRICE))
			{
				keepCandidates.add(item);
			}
		}
		keepCandidates.sort(Comparator.comparingLong((Item i) -> i.getTemplate().getReferencePrice()).reversed());

		final int keepCount = Math.min(keepCandidates.size(), player.getPrivateSellStoreLimit());

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

			// Keep the top-N shop-worthy valuables; sell everything else.
			final int candidateIndex = keepCandidates.indexOf(item);
			if ((candidateIndex >= 0) && (candidateIndex < keepCount))
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
	 * Returns {@code true} for items worth listing in a player private store
	 * (high-grade gear, craft materials, crystals, and recipes).
	 *
	 * @param item the item to classify
	 * @return true if valuable
	 */
	static boolean isValuable(Item item)
	{
		final ItemTemplate template = item.getTemplate();

		// Recipes of any grade.
		if (template.getItemType() == EtcItemType.RECIPE)
		{
			return true;
		}

		// Craft materials B-grade and above.
		if (template.getItemType() == EtcItemType.MATERIAL)
		{
			final ItemGrade grade = template.getItemGrade();
			return (grade == ItemGrade.B) || (grade == ItemGrade.A) || (grade == ItemGrade.S);
		}

		// Crystals B/A/S — identified by their specific item IDs.
		final int id = item.getId();
		if ((id == CrystalType.B.getCrystalId()) || (id == CrystalType.A.getCrystalId()) || (id == CrystalType.S.getCrystalId()))
		{
			return true;
		}

		// B/A/S-grade weapons and armour.
		if (item.isWeapon() || item.isArmor())
		{
			final ItemGrade grade = template.getItemGrade();
			return (grade == ItemGrade.B) || (grade == ItemGrade.A) || (grade == ItemGrade.S);
		}

		return false;
	}
}
