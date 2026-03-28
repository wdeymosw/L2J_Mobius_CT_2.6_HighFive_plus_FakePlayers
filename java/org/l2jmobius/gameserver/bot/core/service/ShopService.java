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
 *   <li>{@link SellService#sell} keeps the top-4 valuables in inventory.</li>
 *   <li>{@link #openShop} lists them in a sell store at 2× reference price.</li>
 *   <li>{@link #closeShop} is called before the bot leaves the city.</li>
 * </ol>
 * Price factor 2× is intentionally above the NPC buy price (0.5×) but below
 * player-market rates for A/S gear — making it attractive to buyers while
 * keeping implementation price-table-free.
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
	 * Opens a private sell store with all valuable items currently in the bot's
	 * inventory (up to {@link SellService#MAX_VALUABLE_KEEP} items).
	 * Does nothing if no valuables are present.
	 *
	 * @param bot the bot opening the shop
	 */
	public static void openShop(BotInstance bot)
	{
		final Player player = bot.getPlayer();

		// Collect valuables, sorted most expensive first.
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

		// Populate sell list.
		final TradeList sellList = player.getSellList();
		sellList.clear();

		int listed = 0;
		for (Item item : valuables)
		{
			if (listed >= SellService.MAX_VALUABLE_KEEP)
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

		LOGGER.info("ShopService: " + player.getName() + " opened private shop with " + listed + " item(s).");
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
