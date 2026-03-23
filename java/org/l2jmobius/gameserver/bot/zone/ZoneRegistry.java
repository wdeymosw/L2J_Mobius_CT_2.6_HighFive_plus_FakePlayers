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
 * Tracks how many bots are assigned to each zone.
 * Zone selection filters by bot level first, then picks the least-filled match.
 */
public class ZoneRegistry
{
	private final List<FarmZone> _zones = new ArrayList<>();

	/** objectId → zone mapping for fast lookup when a bot is removed */
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
	 * Selects a zone suitable for a bot of the given level.
	 * Filters by minLevel..maxLevel, then picks the zone with the lowest fill ratio.
	 * Returns null if no zone matches or all matching zones are full.
	 * @param level bot character level
	 */
	public FarmZone selectZone(int level)
	{
		FarmZone best = null;
		double bestFill = Double.MAX_VALUE;

		for (FarmZone zone : _zones)
		{
			if ((level < zone.getMinLevel()) || (level > zone.getMaxLevel()))
			{
				continue;
			}

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

	/** Returns how many bots are currently assigned to the given zone. */
	public int getBotCount(FarmZone zone)
	{
		return (int) _botZoneMap.values().stream().filter(z -> z == zone).count();
	}

	// -------------------------------------------------------------------------
	// Default zones — city centers by level range
	// Bots spawn here and search for mobs within TargetService.SEARCH_RADIUS.
	// Format: name, centerX, centerY, centerZ, radius, minLevel, maxLevel, maxBots
	// -------------------------------------------------------------------------

	private void registerDefaultZones()
	{
		register(new FarmZone("Talking Island",   -84318,  243572, -3728,  500,  1, 20, 5));
		register(new FarmZone("Gludin Village",   -81168,  149888, -3043,  500, 15, 30, 5));
		register(new FarmZone("Gludio",           -14168,  123688, -3119,  500, 25, 40, 5));
		register(new FarmZone("Dion",              17768,  144624, -3096,  500, 30, 50, 5));
		register(new FarmZone("Giran",             83400,  147720, -3403,  500, 45, 60, 8));
		register(new FarmZone("Oren",              82608,   53120, -1506,  500, 50, 65, 5));
		register(new FarmZone("Aden",             147456,  -55360, -2979,  500, 58, 75, 8));
		register(new FarmZone("Rune",              43648,  -47744,  -800,  500, 65, 80, 5));
		register(new FarmZone("Goddard",          -79264,  150400, -3651,  500, 68, 80, 5));
		register(new FarmZone("Schuttgart",        87360, -142976, -1293,  500, 70, 80, 5));
	}
}
