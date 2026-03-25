/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.zone;

import java.util.concurrent.ThreadLocalRandom;

import org.l2jmobius.gameserver.model.Location;

/**
 * Defines a circular farming area and its home city location.
 * <p>
 * Bots spawn and buy/sell supplies at the home (city) location,
 * then walk to the farm area center to hunt mobs.
 * They return to the farm center if they wander outside the radius.
 */
public class FarmZone
{
	private final String _name;
	// Farm area — center of the hunting grounds.
	private final int _x;
	private final int _y;
	private final int _z;
	private final int _radius;
	// Home — city location where the bot buys/sells supplies.
	private final int _homeX;
	private final int _homeY;
	private final int _homeZ;
	private final int _minLevel;
	private final int _maxLevel;
	private final int _maxBots;

	public FarmZone(String name, int x, int y, int z, int radius, int homeX, int homeY, int homeZ, int minLevel, int maxLevel, int maxBots)
	{
		_name = name;
		_x = x;
		_y = y;
		_z = z;
		_radius = radius;
		_homeX = homeX;
		_homeY = homeY;
		_homeZ = homeZ;
		_minLevel = minLevel;
		_maxLevel = maxLevel;
		_maxBots = maxBots;
	}

	public String getName()
	{
		return _name;
	}

	public int getX()
	{
		return _x;
	}

	public int getY()
	{
		return _y;
	}

	public int getZ()
	{
		return _z;
	}

	public int getRadius()
	{
		return _radius;
	}

	public int getMinLevel()
	{
		return _minLevel;
	}

	public int getMaxLevel()
	{
		return _maxLevel;
	}

	public int getMaxBots()
	{
		return _maxBots;
	}

	/** Returns the farm area center as a Location. */
	public Location getCenter()
	{
		return new Location(_x, _y, _z);
	}

	/** Returns the city (home) location where the bot buys/sells supplies. */
	public Location getHomeLocation()
	{
		return new Location(_homeX, _homeY, _homeZ);
	}

	/** Returns true if the given coordinates are within this zone's radius. */
	public boolean contains(int x, int y)
	{
		final int dx = x - _x;
		final int dy = y - _y;
		return (dx * dx + dy * dy) <= ((long) _radius * _radius);
	}

	/** Returns a random Location inside this zone (uniform distribution in circle). */
	public Location randomPointInside()
	{
		final ThreadLocalRandom rnd = ThreadLocalRandom.current();
		final double angle = rnd.nextDouble() * 2 * Math.PI;
		final double r = _radius * Math.sqrt(rnd.nextDouble());
		final int x = _x + (int) (r * Math.cos(angle));
		final int y = _y + (int) (r * Math.sin(angle));
		return new Location(x, y, _z);
	}
}
