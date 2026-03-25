/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core;

import java.util.logging.Logger;

import org.l2jmobius.gameserver.bot.model.BotInstance;
import org.l2jmobius.gameserver.model.actor.Player;

/**
 * Puts a bot into the game world and removes it cleanly.
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
	 * Places the bot into the world at its last saved position (loaded from DB).
	 * Does nothing if the player is already online.
	 * @param bot the bot to spawn
	 */
	public static void spawnBot(BotInstance bot)
	{
		final Player player = bot.getPlayer();

		// Characters may have online=1 left over from a crash — reset to proceed cleanly.
		if (player.isOnline())
		{
			LOGGER.info("BotSpawner: resetting stale online status for " + player.getName());
			player.setOnlineStatus(false, false);
		}

		// Use the position stored in DB (already loaded by Player.load()).
		final int x = player.getX();
		final int y = player.getY();
		final int z = player.getZ();
		player.setOnlineStatus(true, false);
		player.spawnMe(x, y, z);
		LOGGER.info("BotSpawner: spawned " + player.getName() + " at " + x + "," + y + "," + z);
	}

	/**
	 * Moves the bot to the given coordinates without requiring a client.
	 * Uses decayMe → setXYZ → spawnMe so the world region is updated correctly.
	 *
	 * @param bot the bot to move
	 * @param x   destination X
	 * @param y   destination Y
	 * @param z   destination Z
	 */
	public static void teleportBot(BotInstance bot, int x, int y, int z)
	{
		final Player player = bot.getPlayer();
		player.decayMe();
		player.setXYZ(x, y, z);
		player.spawnMe(x, y, z);
		LOGGER.info("BotSpawner: teleported " + player.getName() + " to " + x + "," + y + "," + z);
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
