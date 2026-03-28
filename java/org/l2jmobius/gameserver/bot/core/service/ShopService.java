/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;

import org.l2jmobius.gameserver.bot.core.model.BotInstance;
import org.l2jmobius.gameserver.model.TradeList;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.enums.player.PrivateStoreType;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.network.serverpackets.PrivateStoreMsgSell;

/**
 * Opens and closes a private sell store for a bot's valuable loot.
 * <p>
 * Flow:
 * <ol>
 *   <li>{@link SellService#sell} retains the most expensive shop-worthy
 *       valuables in inventory (up to the store slot limit).</li>
 *   <li>{@link #openShop} lists them at {@value #SHOP_PRICE_RATE}× reference
 *       price and sits the bot down.</li>
 *   <li>{@link #closeShop} stands the bot up before it leaves the city.</li>
 * </ol>
 */
public class ShopService
{
	private static final Logger LOGGER = Logger.getLogger(ShopService.class.getName());

	/** Shop price = reference price × this multiplier. */
	private static final double SHOP_PRICE_RATE = 2.0;

	private ShopService()
	{
	}

	/**
	 * Opens a private sell store with all valuable items in the bot's inventory.
	 * Items are listed most expensive first; the number of slots is capped by
	 * {@code player.getPrivateSellStoreLimit()}.
	 * Does nothing if no valuables are present.
	 *
	 * @param bot the bot opening the shop
	 */
	public static void openShop(BotInstance bot)
	{
		final Player player = bot.getPlayer();
		final int slotLimit = player.getPrivateSellStoreLimit();

		// Collect and sort valuables most expensive first.
		final List<Item> valuables = new ArrayList<>();
		for (Item item : player.getInventory().getItems())
		{
			if (!item.isEquipped() && SellService.isValuable(item))
			{
				valuables.add(item);
			}
		}

		if (valuables.isEmpty())
		{
			return;
		}

		valuables.sort(Comparator.comparingLong((Item i) -> i.getTemplate().getReferencePrice()).reversed());

		// Populate sell list up to the slot limit.
		final TradeList sellList = player.getSellList();
		sellList.clear();

		int listed = 0;
		for (Item item : valuables)
		{
			if (listed >= slotLimit)
			{
				break;
			}
			final long price = (long) (item.getTemplate().getReferencePrice() * SHOP_PRICE_RATE);
			if (sellList.addItem(item.getObjectId(), item.getCount(), price) != null)
			{
				listed++;
			}
		}

		if (listed == 0)
		{
			return;
		}

		player.sitDown();
		player.setPrivateStoreType(PrivateStoreType.SELL);
		player.broadcastUserInfo();
		player.broadcastPacket(new PrivateStoreMsgSell(player));

		LOGGER.info("ShopService: " + player.getName() + " opened private shop (" + listed + "/" + slotLimit + " slots).");
	}

	/**
	 * Closes the bot's private sell store if one is open.
	 * Call this before the bot travels back to the farm zone.
	 *
	 * @param bot the bot closing the shop
	 */
	public static void closeShop(BotInstance bot)
	{
		final Player player = bot.getPlayer();
		if (player.getPrivateStoreType() == PrivateStoreType.NONE)
		{
			return;
		}

		player.setPrivateStoreType(PrivateStoreType.NONE);
		player.standUp();
		player.broadcastUserInfo();

		LOGGER.info("ShopService: " + player.getName() + " closed private shop.");
	}
}
