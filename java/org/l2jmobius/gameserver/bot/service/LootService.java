/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.service;

import org.l2jmobius.gameserver.ai.Intention;
import org.l2jmobius.gameserver.bot.model.BotInstance;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.item.instance.Item;

/**
 * Handles ground item pickup for bots.
 * <p>
 * Called by ThinkService before target searching so that drop from a just-killed
 * mob is collected before the bot moves on.  One item is processed per call:
 * <ul>
 *   <li>If the nearest valid item is within pickup range → {@code doPickupItem}.</li>
 *   <li>If it is further away → move towards it and return {@code true} so the
 *       caller knows the bot is still busy.</li>
 *   <li>If no items are found → return {@code false} so the caller proceeds to
 *       target search.</li>
 * </ul>
 * Pattern mirrors {@code AutoPlayTaskManager} which uses the same approach for
 * the retail auto-play feature.
 */
public class LootService
{
	/** Radius to scan for ground items (game units). */
	private static final int SCAN_RADIUS = 400;

	/** Distance at which doPickupItem is called instead of moving closer. */
	private static final int PICKUP_RADIUS = 70;

	private LootService()
	{
	}

	/**
	 * Attempts to pick up the nearest ground item within scan radius.
	 *
	 * @param bot the bot that should pick up loot
	 * @return {@code true} if the bot is busy with an item (caller should skip
	 *         target search this tick), {@code false} if nothing was found
	 */
	public static boolean pickupNearest(BotInstance bot)
	{
		final Player player = bot.getPlayer();

		// Skip if inventory is nearly full — avoid overloading the bot.
		if (!player.isInventoryUnder90(false))
		{
			return false;
		}

		Item nearest = null;
		double nearestDist = Double.MAX_VALUE;

		for (Item item : World.getInstance().getVisibleObjectsInRange(player, Item.class, SCAN_RADIUS))
		{
			if (!item.isSpawned())
			{
				continue;
			}

			// Only pick up items we own or whose protection timer has expired.
			if (item.isProtected() && (item.getOwnerId() != player.getObjectId()))
			{
				continue;
			}

			// Geodata line-of-reach check.
			if (!GeoEngine.getInstance().canMoveToTarget(
				player.getX(), player.getY(), player.getZ(),
				item.getX(), item.getY(), item.getZ(),
				player.getInstanceId()))
			{
				continue;
			}

			final double dist = player.calculateDistance2D(item);
			if (dist < nearestDist)
			{
				nearestDist = dist;
				nearest = item;
			}
		}

		if (nearest == null)
		{
			return false;
		}

		if (nearestDist <= PICKUP_RADIUS)
		{
			player.doPickupItem(nearest);
		}
		else
		{
			// Move towards the item; pickup will happen on a subsequent tick.
			player.getAI().setIntention(Intention.MOVE_TO, nearest);
		}

		return true;
	}
}
