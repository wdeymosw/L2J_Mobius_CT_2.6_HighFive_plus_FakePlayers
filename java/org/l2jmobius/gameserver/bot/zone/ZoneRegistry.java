/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.zone;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of all available FarmZones.
 * Tracks how many bots are currently assigned to each zone.
 * Provides zone selection logic: least-filled zone wins.
 */
public class ZoneRegistry
{
	private final List<FarmZone> _zones = new ArrayList<>();

	/** objectId → zone mapping for fast lookup when a bot is removed. */
	private final Map<Integer, FarmZone> _botZoneMap = new ConcurrentHashMap<>();

	// -------------------------------------------------------------------------
	// Singleton
	// -------------------------------------------------------------------------

	private ZoneRegistry()
	{
		registerDefaultZones();
	}

	public static ZoneRegistry getInstance()
	{
		return SingletonHolder.INSTANCE;
	}

	private static class SingletonHolder
	{
		static final ZoneRegistry INSTANCE = new ZoneRegistry();
	}

	// -------------------------------------------------------------------------
	// Zone management
	// -------------------------------------------------------------------------

	public void register(FarmZone zone)
	{
		_zones.add(zone);
	}

	public List<FarmZone> getZones()
	{
		return Collections.unmodifiableList(_zones);
	}

	/**
	 * Selects the zone with the lowest fill ratio (currentBots / maxBots)
	 * that still has capacity. Returns null if all zones are full.
	 */
	public FarmZone selectZone()
	{
		FarmZone best = null;
		double bestFill = Double.MAX_VALUE;

		for (FarmZone zone : _zones)
		{
			final long count = _botZoneMap.values().stream().filter(z -> z == zone).count();
			if (count >= zone.getMaxBots())
			{
				continue;
			}
			final double fill = (double) count / zone.getMaxBots();
			if (fill < bestFill)
			{
				bestFill = fill;
				best = zone;
			}
		}
		return best;
	}

	/** Records that a bot has been assigned to a zone. */
	public void assignBot(int objectId, FarmZone zone)
	{
		_botZoneMap.put(objectId, zone);
	}

	/** Removes bot's zone assignment when the bot is removed. */
	public void unassignBot(int objectId)
	{
		_botZoneMap.remove(objectId);
	}

	/** Returns how many bots are currently in the given zone. */
	public int getBotCount(FarmZone zone)
	{
		return (int) _botZoneMap.values().stream().filter(z -> z == zone).count();
	}

	// -------------------------------------------------------------------------
	// Default zones — edit coordinates to match your server's hunting areas
	// -------------------------------------------------------------------------

	private void registerDefaultZones()
	{
		// Format: name, centerX, centerY, centerZ, radius, minLevel, maxLevel, maxBots
		register(new FarmZone("Execution Grounds",    116782, -178334, -980,   600, 20, 35, 8));
		register(new FarmZone("Abandoned Camp",       -41107,  211355, -3064,  500, 25, 38, 6));
		register(new FarmZone("Cruma Tower",           16658,  114570, -3720,  700, 40, 52, 8));
		register(new FarmZone("Alligator Island",      47458,  186744, -3465,  600, 45, 55, 6));
		register(new FarmZone("Dragon Valley",         95417,  108608, -3720,  800, 55, 65, 10));
		register(new FarmZone("Forest of Mirrors",     49572,   66464, -3456,  600, 58, 68, 6));
		register(new FarmZone("Giants Cave",          115613,   16028, -4895,  700, 62, 72, 8));
		register(new FarmZone("Hot Springs",           80858,  149249, -3080,  600, 68, 76, 6));
		register(new FarmZone("Stakato Nest",          82390,  194699, -3712,  700, 70, 78, 8));
		register(new FarmZone("Monastery of Silence", 116507,    9536, -2200,  600, 74, 80, 6));
	}
}
