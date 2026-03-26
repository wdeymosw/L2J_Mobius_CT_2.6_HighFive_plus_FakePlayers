/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.zone;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Registry of all available FarmZones.
 * Tracks how many bots are assigned to each zone.
 * Zone selection filters by bot level first, then picks the least-filled match.
 */
public class ZoneRegistry
{
	private static final Logger LOGGER = Logger.getLogger(ZoneRegistry.class.getName());

	private final List<FarmZone> _zones = new ArrayList<>();

	/** objectId → zone mapping for fast lookup when a bot is removed */
	private final Map<Integer, FarmZone> _botZoneMap = new ConcurrentHashMap<>();

	// -------------------------------------------------------------------------
	// Singleton
	// -------------------------------------------------------------------------

	private ZoneRegistry()
	{
		registerDefaultZones();
		loadCityData();
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
	 * Selects a zone suitable for a bot at the given level and position.
	 * Filters by minLevel..maxLevel and capacity, then picks the zone whose
	 * homeLocation is closest to (x, y). Ties broken by lowest fill ratio.
	 *
	 * @param level bot character level
	 * @param x     bot's current X coordinate
	 * @param y     bot's current Y coordinate
	 * @return the best matching FarmZone, or {@code null} if none available
	 */
	public FarmZone selectZone(int level, int x, int y)
	{
		FarmZone best = null;
		double bestDist = Double.MAX_VALUE;
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
			final org.l2jmobius.gameserver.model.Location home = zone.getHomeLocation();
			final int dx = home.getX() - x;
			final int dy = home.getY() - y;
			final double dist = Math.sqrt((dx * dx) + (dy * dy));

			// Prefer closest home; use fill as tiebreaker.
			if ((dist < bestDist) || ((dist == bestDist) && (fill < bestFill)))
			{
				bestDist = dist;
				bestFill = fill;
				best = zone;
			}
		}
		return best;
	}

	/**
	 * Records that a bot has been assigned to a zone.
	 *
	 * @param objectId bot's character objectId
	 * @param zone     the zone assigned
	 */
	public void assignBot(int objectId, FarmZone zone)
	{
		_botZoneMap.put(objectId, zone);
	}

	/**
	 * Removes bot's zone assignment when the bot is removed.
	 *
	 * @param objectId bot's character objectId
	 */
	public void unassignBot(int objectId)
	{
		_botZoneMap.remove(objectId);
	}

	/**
	 * @param zone the zone to count bots for
	 * @return how many bots are currently assigned to the given zone
	 */
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
		// Format: name, farmX, farmY, farmZ, farmRadius, homeX (city), homeY, homeZ, minLevel, maxLevel, maxBots
		// Home = city shop NPC area. Farm = actual mob spawn center (from XML spawn data).
		// Distance doesn't matter — bots teleport between home and farm.
		register(new FarmZone("Talking Island", -82000,  255000, -3500, 1000, -84318,  243572, -3728,  1, 20, 5)); // beach mobs near TI
		register(new FarmZone("Gludin Village", -99404,  102154, -3342, 1000, -81168,  149888, -3043, 15, 30, 5)); // Turek Orcs
		register(new FarmZone("Gludio",          28818,  135149, -2889, 1000, -14168,  123688, -3119, 25, 40, 5)); // Plains of Dion (low end)
		register(new FarmZone("Dion",            39000,  146000, -3461, 1000,  17768,  144624, -3096, 30, 50, 5)); // Execution Grounds
		register(new FarmZone("Giran",           79000,  121000, -2220, 1000,  83400,  147720, -3403, 45, 60, 8)); // Breka's Stronghold
		register(new FarmZone("Oren",            61000,   11000, -3668, 1000,  82608,   53120, -1506, 50, 65, 5)); // Sea of Spores
		register(new FarmZone("Aden",           135143,     841, -3872, 1000, 147456,  -55360, -2979, 58, 75, 8)); // Plains of Glory
		register(new FarmZone("Rune",           176140,  -23000, -3256, 1000,  43648,  -47744,  -800, 65, 80, 5)); // Fields of Massacre
		register(new FarmZone("Goddard",        147736, -112290, -2238, 1000, -79264,  150400, -3651, 68, 80, 5)); // Hot Springs
		register(new FarmZone("Schuttgart",     167785,  -49088, -3421, 1000,  87360, -142976, -1293, 70, 80, 5)); // Wall of Argos
	}

	// -------------------------------------------------------------------------
	// City data — loaded from BotZones.xml
	// -------------------------------------------------------------------------

	private void loadCityData()
	{
		final Map<String, BotZoneData> data = BotZoneDataLoader.load();
		int matched = 0;
		for (FarmZone zone : _zones)
		{
			final BotZoneData cityData = data.get(zone.getName());
			if (cityData != null)
			{
				zone.setCityData(cityData);
				matched++;
			}
			else
			{
				LOGGER.warning("ZoneRegistry: no city data for zone '" + zone.getName() + "' — city walk disabled for this zone.");
			}
		}
		LOGGER.info("ZoneRegistry: city data attached to " + matched + "/" + _zones.size() + " zone(s).");
	}
}
