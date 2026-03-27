/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.zone;

import java.util.Collections;
import java.util.List;

import org.l2jmobius.gameserver.model.Location;

/**
 * City-side NPC coordinates and walk path for a bot zone.
 * Loaded from {@code data/bot/BotZones.xml}.
 * <p>
 * <b>gatekeeper</b>  — where the bot teleports to reach the farm zone.<br>
 * <b>shop</b>         — where the bot buys supplies (Grocer/Merchant).<br>
 * <b>guildmaster</b> — Adventure Guildsman position (used for NOISE bot city walk).<br>
 * <b>cityPath</b>     — ordered waypoints to walk from city entry to the gatekeeper.
 *                      The last point must be at or near the gatekeeper.
 */
public class BotZoneData
{
	private final Location _gatekeeper;
	private final Location _shop;
	private final Location _guildmaster;
	private final List<Location> _cityPath;

	public BotZoneData(Location gatekeeper, Location shop, Location guildmaster, List<Location> cityPath)
	{
		_gatekeeper = gatekeeper;
		_shop = shop;
		_guildmaster = guildmaster;
		_cityPath = Collections.unmodifiableList(cityPath);
	}

	/**
	 * @return gatekeeper NPC position — bot walks here before teleporting to the farm zone
	 */
	public Location getGatekeeper()
	{
		return _gatekeeper;
	}

	/**
	 * @return grocer/merchant NPC position — bot walks here when buying supplies
	 */
	public Location getShop()
	{
		return _shop;
	}

	/**
	 * @return Adventure Guildsman position — used as waypoint for NOISE bot city walk
	 */
	public Location getGuildmaster()
	{
		return _guildmaster;
	}

	/**
	 * @return ordered waypoints through the city leading to the gatekeeper;
	 *         the last point is at or near the gatekeeper
	 */
	public List<Location> getCityPath()
	{
		return _cityPath;
	}
}
