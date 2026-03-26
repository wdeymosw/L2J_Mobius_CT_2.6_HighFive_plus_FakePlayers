/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.model;

import java.util.List;

import org.l2jmobius.gameserver.bot.zone.FarmZone;
import org.l2jmobius.gameserver.geoengine.pathfinding.GeoLocation;
import org.l2jmobius.gameserver.model.Location;
import org.l2jmobius.gameserver.model.actor.Creature;
import org.l2jmobius.gameserver.model.actor.Player;

/**
 * Wraps a {@link Player} instance and tracks bot-specific state.
 * <p>
 * The bot layer never reaches into game internals directly —
 * all game interactions go through the Player's public API.
 */
public class BotInstance
{
	// --- Game object (controlled, not extended) ---
	private final Player _player;
	private final BotProfile _profile;
	private final BotRole _role;

	// --- Runtime state ---
	private BotState _state = BotState.SEARCHING;
	private Creature _target;

	// --- Timing (epoch ms) ---
	private long _nextThinkTime = 0;
	private long _nextSearchTime = 0;
	private long _sessionEndTime = 0;   // 0 = no session limit (CORE bots)
	private long _targetExpireTime = 0; // 0 = no limit; set when target is acquired
	private long _cityIdleEndTime = 0;  // when to leave city and head to zone

	// --- Level tracking ---
	private int _lastKnownLevel;

	// --- Global path (long-range segment endpoints) ---
	private List<Location> _globalPath = null;
	private int _globalIndex = 0;

	// --- Local path (PathFinding waypoints for current segment) ---
	private List<GeoLocation> _currentPath = null;
	private int _pathIndex = 0;
	private long _lastPathTime = 0;
	private long _lastDirectCheckTime = 0;

	// --- Stuck detection (position snapshot) ---
	private long _stuckCheckTime = 0;
	private int _lastStuckX = Integer.MIN_VALUE;
	private int _lastStuckY = Integer.MIN_VALUE;

	// --- TRAVEL mode ---
	private Location _moveDestination = null;
	private TravelAction _travelAction = TravelAction.RETURN_TO_ZONE;

	// --- City walk (waypoints from BotZones.xml, used before teleporting to farm) ---
	private List<Location> _cityPath = null;
	private int _cityPathIdx = 0;
	private long _gateWaitEndTime = 0; // >0 while waiting at gatekeeper before teleport

	public BotInstance(Player player, BotProfile profile)
	{
		_player = player;
		_profile = profile;
		_role = RoleResolver.resolve(player.getActiveClass());
		_lastKnownLevel = player.getLevel();
	}

	// -------------------------------------------------------------------------
	// Core accessors
	// -------------------------------------------------------------------------

	public Player getPlayer()
	{
		return _player;
	}

	public BotProfile getProfile()
	{
		return _profile;
	}

	public BotRole getRole()
	{
		return _role;
	}

	public FarmZone getZone()
	{
		return _profile.getZone();
	}

	public BotState getState()
	{
		return _state;
	}

	public void setState(BotState state)
	{
		_state = state;
	}

	public Creature getTarget()
	{
		return _target;
	}

	public void setTarget(Creature target)
	{
		_target = target;
	}

	public void clearTarget()
	{
		_target = null;
		_targetExpireTime = 0;
	}

	public long getTargetExpireTime()
	{
		return _targetExpireTime;
	}

	public void setTargetExpireTime(long time)
	{
		_targetExpireTime = time;
	}

	// -------------------------------------------------------------------------
	// Timing
	// -------------------------------------------------------------------------

	public long getNextThinkTime()
	{
		return _nextThinkTime;
	}

	public void setNextThinkTime(long time)
	{
		_nextThinkTime = time;
	}

	public long getNextSearchTime()
	{
		return _nextSearchTime;
	}

	public void setNextSearchTime(long time)
	{
		_nextSearchTime = time;
	}

	public long getCityIdleEndTime()
	{
		return _cityIdleEndTime;
	}

	public void setCityIdleEndTime(long time)
	{
		_cityIdleEndTime = time;
	}

	public long getSessionEndTime()
	{
		return _sessionEndTime;
	}

	public void setSessionEndTime(long time)
	{
		_sessionEndTime = time;
	}

	public boolean isSessionExpired()
	{
		return (_sessionEndTime > 0) && (System.currentTimeMillis() >= _sessionEndTime);
	}

	// -------------------------------------------------------------------------
	// Global path (long-range segment endpoints)
	// -------------------------------------------------------------------------

	public void setGlobalPath(List<Location> path)
	{
		_globalPath = path;
		_globalIndex = 0;
		clearPath(); // local path must be reset when destination changes
	}

	public boolean hasGlobalPath()
	{
		return (_globalPath != null) && (_globalIndex < _globalPath.size());
	}

	public Location getCurrentGlobalPoint()
	{
		return hasGlobalPath() ? _globalPath.get(_globalIndex) : null;
	}

	/** Advances to the next segment and clears the local path. */
	public void advanceGlobalIndex()
	{
		_globalIndex++;
		clearPath();
	}

	public void clearGlobalPath()
	{
		_globalPath = null;
		_globalIndex = 0;
		clearPath();
	}

	/**
	 * Returns true if the global path's last waypoint matches the given coordinates.
	 * Used to detect when the travel destination has changed.
	 * @param tx destination X
	 * @param ty destination Y
	 * @return true if path leads to (tx, ty)
	 */
	public boolean isGlobalPathTo(int tx, int ty)
	{
		if ((_globalPath == null) || _globalPath.isEmpty())
		{
			return false;
		}
		final Location last = _globalPath.get(_globalPath.size() - 1);
		return (last.getX() == tx) && (last.getY() == ty);
	}

	// -------------------------------------------------------------------------
	// Local path (PathFinding waypoints for current segment)
	// -------------------------------------------------------------------------

	public void setPath(List<GeoLocation> path)
	{
		_currentPath = path;
		_pathIndex = 0;
	}

	public boolean hasPath()
	{
		return (_currentPath != null) && (_pathIndex < _currentPath.size());
	}

	public GeoLocation getCurrentWaypoint()
	{
		return hasPath() ? _currentPath.get(_pathIndex) : null;
	}

	public void advanceWaypoint()
	{
		_pathIndex++;
	}

	public void clearPath()
	{
		_currentPath = null;
		_pathIndex = 0;
	}

	public long getLastPathTime()
	{
		return _lastPathTime;
	}

	public void setLastPathTime(long time)
	{
		_lastPathTime = time;
	}

	public long getLastDirectCheckTime()
	{
		return _lastDirectCheckTime;
	}

	public void setLastDirectCheckTime(long time)
	{
		_lastDirectCheckTime = time;
	}

	// -------------------------------------------------------------------------
	// Stuck detection (position snapshot)
	// -------------------------------------------------------------------------

	public long getStuckCheckTime()
	{
		return _stuckCheckTime;
	}

	public void setStuckCheckTime(long time)
	{
		_stuckCheckTime = time;
	}

	public int getLastStuckX()
	{
		return _lastStuckX;
	}

	public int getLastStuckY()
	{
		return _lastStuckY;
	}

	public void setStuckSnapshot(int x, int y)
	{
		_lastStuckX = x;
		_lastStuckY = y;
	}

	// -------------------------------------------------------------------------
	// TRAVEL mode
	// -------------------------------------------------------------------------

	public Location getMoveDestination()
	{
		return _moveDestination;
	}

	public void setMoveDestination(Location dest)
	{
		_moveDestination = dest;
	}

	public TravelAction getTravelAction()
	{
		return _travelAction;
	}

	public void setTravelAction(TravelAction action)
	{
		_travelAction = action;
	}

	/**
	 * @param radius arrival radius in units
	 * @return true if the bot is within {@code radius} units of its move destination
	 */
	public boolean hasReachedDestination(int radius)
	{
		if (_moveDestination == null)
		{
			return true;
		}
		final long dx = _player.getX() - _moveDestination.getX();
		final long dy = _player.getY() - _moveDestination.getY();
		return (dx * dx + dy * dy) <= ((long) radius * radius);
	}

	// -------------------------------------------------------------------------
	// City walk (waypoints through city to gatekeeper, before farm teleport)
	// -------------------------------------------------------------------------

	/**
	 * @param path ordered waypoints through the city leading to the gatekeeper
	 */
	public void setCityPath(List<Location> path)
	{
		_cityPath = path;
		_cityPathIdx = 0;
	}

	/** @return true if there are remaining city-walk waypoints to visit */
	public boolean hasCityPath()
	{
		return (_cityPath != null) && (_cityPathIdx < _cityPath.size());
	}

	/** @return the current city-walk waypoint, or {@code null} if none remain */
	public Location getCurrentCityWaypoint()
	{
		return hasCityPath() ? _cityPath.get(_cityPathIdx) : null;
	}

	/** Advances to the next city-walk waypoint. */
	public void advanceCityPath()
	{
		_cityPathIdx++;
	}

	/** Clears the city walk path. */
	public void clearCityPath()
	{
		_cityPath = null;
		_cityPathIdx = 0;
	}

	/**
	 * @return the time (epoch ms) when the gate wait expires, or 0 if not waiting
	 */
	public long getGateWaitEndTime()
	{
		return _gateWaitEndTime;
	}

	/**
	 * @param time epoch ms when the wait at the gatekeeper should end (0 to clear)
	 */
	public void setGateWaitEndTime(long time)
	{
		_gateWaitEndTime = time;
	}

	// -------------------------------------------------------------------------
	// Level tracking
	// -------------------------------------------------------------------------

	/**
	 * @return true if the player leveled up since the last call; updates cache
	 */
	public boolean hasLeveledUp()
	{
		final int current = _player.getLevel();
		if (current > _lastKnownLevel)
		{
			_lastKnownLevel = current;
			return true;
		}
		return false;
	}

	// -------------------------------------------------------------------------
	// Convenience delegates
	// -------------------------------------------------------------------------

	public boolean isDead()
	{
		return _player.isDead();
	}

	public boolean isInZone()
	{
		return _profile.getZone().contains(_player.getX(), _player.getY());
	}

	public float getMistakeRate()
	{
		return _profile.getMistakeRate();
	}
}
