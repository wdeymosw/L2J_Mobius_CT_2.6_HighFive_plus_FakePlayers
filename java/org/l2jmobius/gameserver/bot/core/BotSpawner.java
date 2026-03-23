/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core;

import java.util.logging.Logger;

import org.l2jmobius.gameserver.bot.model.BotInstance;
import org.l2jmobius.gameserver.model.Location;
import org.l2jmobius.gameserver.model.actor.Player;

/**
 * Puts a bot into the game world and removes it cleanly.
 * <p>
 * Mirrors the pattern used by OfflineTraderTable:
 * spawn = setOnlineStatus + spawnMe, remove = storeMe + deleteMe.
 */
public class BotSpawner
{
	private static final Logger LOGGER = Logger.getLogger(BotSpawner.class.getName());

	private BotSpawner()
	{
	}

	/**
	 * Places the bot into the world at a random point inside its zone.
	 * Does nothing if the player is already online.
	 *
	 * @param bot the bot to spawn
	 */
	public static void spawnBot(BotInstance bot)
	{
		final Player player = bot.getPlayer();

		// Characters may have online=1 left over from a crash or previous session.
		// Reset to offline first so setOnlineStatus(true) + spawnMe() always proceed cleanly.
		if (player.isOnline())
		{
			LOGGER.info("BotSpawner: resetting stale online status for " + player.getName());
			player.setOnlineStatus(false, false);
		}

		final Location spawnLoc = bot.getZone().randomPointInside();
		player.setXYZInvisible(spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ());
		player.setOnlineStatus(true, false);
		player.spawnMe(spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ());
		LOGGER.info("BotSpawner: placed " + player.getName() + " at " + spawnLoc.getX() + "," + spawnLoc.getY() + "," + spawnLoc.getZ());
	}

	/**
	 * Saves the bot's data and removes it from the world.
	 * Safe to call even if the bot is already offline.
	 *
	 * @param bot the bot to remove
	 */
	public static void removeBot(BotInstance bot)
	{
		final Player player = bot.getPlayer();
		if (!player.isOnline())
		{
			return;
		}

		try
		{
			player.storeMe();
			player.deleteMe();
		}
		catch (Exception e)
		{
			LOGGER.warning("BotSpawner: error removing bot " + player.getName() + " — " + e.getMessage());
		}
	}
}
