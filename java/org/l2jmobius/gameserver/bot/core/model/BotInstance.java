/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.model;

import java.util.LinkedList;
import java.util.List;

import org.l2jmobius.gameserver.bot.core.action.BotAction;
import org.l2jmobius.gameserver.bot.core.action.BotExecutor;
import org.l2jmobius.gameserver.bot.core.goap.GoapAction;
import org.l2jmobius.gameserver.bot.core.zone.FarmZone;
import org.l2jmobius.gameserver.geoengine.pathfinding.GeoLocation;
import org.l2jmobius.gameserver.model.Location;
import org.l2jmobius.gameserver.model.actor.Creature;
import org.l2jmobius.gameserver.model.actor.Player;

/**
 * Pure data container for one bot's runtime state.
 * <p>
 * Layer 2 entry point is {@link org.l2jmobius.gameserver.bot.core.goap.GoapAgent#tick(BotInstance, long)}.
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

	private BotPhase _phase = BotPhase.FARMING;
	private Creature _target;

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
	/** Time to revive after death (0 = alive). */
	private long _reviveTime = 0;
	/** Earliest time the bot may attempt to drink another potion (reuse guard). */
	private long _nextPotionTime = 0;
	/** When the current GOAP action started (for timeout detection). */
	private long _currentActionStartTime = 0;
	/** Maximum time the current action is allowed to run (0 = no limit). */
	private long _currentActionTimeoutMs = 0;

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
	private int _stuckCount = 0;

	// -------------------------------------------------------------------------
	// GOAP plan state (GoapAgent)
	// -------------------------------------------------------------------------

	private final LinkedList<GoapAction> _goapPlan = new LinkedList<>();

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
		org.l2jmobius.gameserver.bot.core.goap.GoapAgent.tick(this, now);
	}

	// =========================================================================
	// Public API — core
	// =========================================================================

	public Player getPlayer() { return _player; }
	public BotProfile getProfile() { return _profile; }
	public BotRole getRole() { return _role; }

	// =========================================================================
	// Executor delegates (prefer these over exposing the executor directly)
	// =========================================================================

	public void queueAction(BotAction action) { _executor.add(action); }
	public void clearQueue()
	{
		_executor.clear();
		// Reset stuck detection so the next movement starts fresh.
		_stuckCount = 0;
		_stuckCheckTime = 0;
		_lastStuckX = Integer.MIN_VALUE;
		_lastStuckY = Integer.MIN_VALUE;
	}
	public boolean isQueueIdle() { return _executor.isIdle(); }
	public void tickExecutor(long now) { _executor.tick(this, now); }

	/**
	 * Instantly moves the bot to the given world coordinates.
	 * Uses the server's spawn/despawn cycle (decayMe → setXYZ → spawnMe).
	 *
	 * @param x target X
	 * @param y target Y
	 * @param z target Z
	 */
	public void teleport(int x, int y, int z)
	{
		_player.decayMe();
		_player.setXYZ(x, y, z);
		_player.spawnMe(x, y, z);
	}

	public FarmZone getZone() { return _profile.getZone(); }

	public Creature getTarget() { return _target; }
	public void setTarget(Creature target) { _target = target; _player.setTarget(target); }
	public void clearTarget() { _target = null; _player.setTarget(null); }
	public boolean hasTarget() { return (_target != null) && !_target.isDead(); }

	public BotPhase getPhase() { return _phase; }
	public void setPhase(BotPhase phase) { _phase = phase; }

	public float getMistakeRate() { return _profile.getMistakeRate(); }
	public boolean isDead() { return _player.isDead(); }
	public int getPhysicalAttackRange() { return _player.getPhysicalAttackRange(); }

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

	public long getCurrentActionStartTime() { return _currentActionStartTime; }
	public void setCurrentActionStartTime(long time) { _currentActionStartTime = time; }

	public long getCurrentActionTimeoutMs() { return _currentActionTimeoutMs; }
	public void setCurrentActionTimeoutMs(long timeoutMs) { _currentActionTimeoutMs = timeoutMs; }

	// =========================================================================
	// Session
	// =========================================================================

	public long getSessionEndTime() { return _sessionEndTime; }
	public void setSessionEndTime(long time) { _sessionEndTime = time; }

	public long getReviveTime() { return _reviveTime; }
	public void setReviveTime(long time) { _reviveTime = time; }

	public long getNextPotionTime() { return _nextPotionTime; }
	public void setNextPotionTime(long time) { _nextPotionTime = time; }

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
	public int getStuckCount() { return _stuckCount; }
	public void incrementStuckCount() { _stuckCount++; }
	public void resetStuckCount() { _stuckCount = 0; }

	// =========================================================================
	// GOAP plan (GoapAgent)
	// =========================================================================

	/** @return the action currently at the head of the GOAP plan, or {@code null} if the plan is empty. */
	public GoapAction getCurrentGoapAction() { return _goapPlan.peekFirst(); }

	/**
	 * Replaces the current plan with the given list.
	 * The first element becomes the current action.
	 *
	 * @param plan ordered list of actions produced by GoapPlanner
	 */
	public void setGoapPlan(List<GoapAction> plan)
	{
		_goapPlan.clear();
		_goapPlan.addAll(plan);
	}

	/** Removes the completed head action and exposes the next one. */
	public void advanceGoapPlan() { _goapPlan.pollFirst(); }

	/** Discards the entire plan. A replan will occur on the next tick. */
	public void clearGoapPlan() { _goapPlan.clear(); }
}
