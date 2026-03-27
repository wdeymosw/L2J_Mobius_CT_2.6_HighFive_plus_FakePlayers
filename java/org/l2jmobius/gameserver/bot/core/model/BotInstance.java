/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.model;

import java.util.List;

import org.l2jmobius.gameserver.bot.core.action.BotExecutor;
import org.l2jmobius.gameserver.bot.core.action.TravelReason;
import org.l2jmobius.gameserver.bot.core.zone.FarmZone;
import org.l2jmobius.gameserver.geoengine.pathfinding.GeoLocation;
import org.l2jmobius.gameserver.model.Location;
import org.l2jmobius.gameserver.model.actor.Creature;
import org.l2jmobius.gameserver.model.actor.Player;

/**
 * Pure data container for one bot's runtime state.
 * <p>
 * Layer 2 entry point is {@link org.l2jmobius.gameserver.bot.core.BotController#tick(BotInstance, long)}.
 * This class owns no logic — it only holds fields and exposes them via getters/setters.
 */
public class BotInstance
{
	// -------------------------------------------------------------------------
	// Core (immutable after construction)
	// -------------------------------------------------------------------------

	private final Player _player;
	private final BotProfile _profile;
	private final BotRole _role;

	// -------------------------------------------------------------------------
	// AI components
	// -------------------------------------------------------------------------

	private final BotExecutor _executor = new BotExecutor();

	// -------------------------------------------------------------------------
	// Runtime state
	// -------------------------------------------------------------------------

	private BotState _state = BotState.IDLE;
	private Creature _target;
	private TravelReason _travelReason;

	// -------------------------------------------------------------------------
	// Timing
	// -------------------------------------------------------------------------

	/** Throttle: next allowed think time. */
	private long _nextThinkTime = 0;
	/** DEAD: when to revive. RESTING: when to head back to farm. */
	private long _stateEndTime = 0;
	/** Throttle for TargetService calls in SEARCHING state. */
	private long _nextSearchTime = 0;
	/** Session limit for PASSIVE bots (0 = unlimited). */
	private long _sessionEndTime = 0;

	// -------------------------------------------------------------------------
	// Level tracking
	// -------------------------------------------------------------------------

	private int _lastKnownLevel;

	// -------------------------------------------------------------------------
	// Global path — long-range segment endpoints (PathService)
	// -------------------------------------------------------------------------

	private List<Location> _globalPath = null;
	private int _globalIndex = 0;

	// -------------------------------------------------------------------------
	// Local path — A* waypoints for current segment (PathService)
	// -------------------------------------------------------------------------

	private List<GeoLocation> _currentPath = null;
	private int _pathIndex = 0;
	private long _lastPathTime = 0;
	private long _lastDirectCheckTime = 0;

	// -------------------------------------------------------------------------
	// Stuck detection
	// -------------------------------------------------------------------------

	private long _stuckCheckTime = 0;
	private int _lastStuckX = Integer.MIN_VALUE;
	private int _lastStuckY = Integer.MIN_VALUE;

	// =========================================================================
	// Constructor
	// =========================================================================

	public BotInstance(Player player, BotProfile profile)
	{
		_player = player;
		_profile = profile;
		_role = RoleResolver.resolve(player.getActiveClass());
		_lastKnownLevel = player.getLevel();
	}

	// =========================================================================
	// Single entry point — called by BotManager every tick
	// =========================================================================

	public void update(long now)
	{
		org.l2jmobius.gameserver.bot.core.BotController.tick(this, now);
	}

	// =========================================================================
	// Public API — core
	// =========================================================================

	public Player getPlayer() { return _player; }
	public BotProfile getProfile() { return _profile; }
	public BotRole getRole() { return _role; }
	public BotExecutor getExecutor() { return _executor; }

	public BotState getState() { return _state; }
	public void setState(BotState state) { _state = state; }

	public FarmZone getZone() { return _profile.getZone(); }

	public Creature getTarget() { return _target; }
	public void setTarget(Creature target) { _target = target; }
	public void clearTarget() { _target = null; }
	public boolean hasTarget() { return (_target != null) && !_target.isDead(); }

	public TravelReason getTravelReason() { return _travelReason; }
	public void setTravelReason(TravelReason reason) { _travelReason = reason; }

	public float getMistakeRate() { return _profile.getMistakeRate(); }
	public boolean isDead() { return _player.isDead(); }

	public boolean isInZone()
	{
		return _profile.getZone().contains(_player.getX(), _player.getY());
	}

	// =========================================================================
	// Timing
	// =========================================================================

	public long getNextThinkTime() { return _nextThinkTime; }
	public void setNextThinkTime(long time) { _nextThinkTime = time; }

	public long getStateEndTime() { return _stateEndTime; }
	public void setStateEndTime(long time) { _stateEndTime = time; }

	public long getNextSearchTime() { return _nextSearchTime; }
	public void setNextSearchTime(long time) { _nextSearchTime = time; }

	// =========================================================================
	// Session
	// =========================================================================

	public long getSessionEndTime() { return _sessionEndTime; }
	public void setSessionEndTime(long time) { _sessionEndTime = time; }

	public boolean isSessionExpired()
	{
		return (_sessionEndTime > 0) && (System.currentTimeMillis() >= _sessionEndTime);
	}

	// =========================================================================
	// Level tracking
	// =========================================================================

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

	// =========================================================================
	// Global path (PathService — long-range segments)
	// =========================================================================

	public void setGlobalPath(List<Location> path)
	{
		_globalPath = path;
		_globalIndex = 0;
		clearPath();
	}

	public boolean hasGlobalPath()
	{
		return (_globalPath != null) && (_globalIndex < _globalPath.size());
	}

	public Location getCurrentGlobalPoint()
	{
		return hasGlobalPath() ? _globalPath.get(_globalIndex) : null;
	}

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

	public boolean isGlobalPathTo(int tx, int ty)
	{
		if ((_globalPath == null) || _globalPath.isEmpty())
		{
			return false;
		}
		final Location last = _globalPath.get(_globalPath.size() - 1);
		return (last.getX() == tx) && (last.getY() == ty);
	}

	// =========================================================================
	// Local path (PathService — A* waypoints)
	// =========================================================================

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

	public void advanceWaypoint() { _pathIndex++; }

	public void clearPath()
	{
		_currentPath = null;
		_pathIndex = 0;
	}

	public long getLastPathTime() { return _lastPathTime; }
	public void setLastPathTime(long time) { _lastPathTime = time; }
	public long getLastDirectCheckTime() { return _lastDirectCheckTime; }
	public void setLastDirectCheckTime(long time) { _lastDirectCheckTime = time; }

	// =========================================================================
	// Stuck detection (PathService)
	// =========================================================================

	public long getStuckCheckTime() { return _stuckCheckTime; }
	public void setStuckCheckTime(long time) { _stuckCheckTime = time; }
	public int getLastStuckX() { return _lastStuckX; }
	public int getLastStuckY() { return _lastStuckY; }
	public void setStuckSnapshot(int x, int y) { _lastStuckX = x; _lastStuckY = y; }
}
