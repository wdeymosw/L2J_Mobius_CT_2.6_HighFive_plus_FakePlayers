/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.model;

import java.util.LinkedList;
import java.util.List;

import org.l2jmobius.gameserver.bot.core.action.BotAction;
import org.l2jmobius.gameserver.bot.core.action.BotExecutor;
import org.l2jmobius.gameserver.bot.core.behaviour.BehaviourController;
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
 * <p>
 * <b>Not thread-safe.</b> All accesses must occur on the BotManager scheduler thread.
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

	/** Manages the active BotBehaviour and transitions between behaviours. Set by BotFactory. */
	private BehaviourController _behaviourController;

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
	/** Last time the bot was in active combat (took damage or fought). Used to block premature sit. */
	private long _lastCombatTime = 0;
	/** HP snapshot taken at the end of each tick — used to detect damage taken between ticks. */
	private double _lastHpSnapshot = -1.0;

	// -------------------------------------------------------------------------
	// Dynamic search radius state (TargetService)
	// -------------------------------------------------------------------------

	/** Dynamic search radius — starts at base, expands on repeated failures. */
	private int _searchRadius = 700; // matches TargetService.SEARCH_RADIUS_BASE
	/** Consecutive failed searches — drives radius expansion. */
	private int _searchFailCount = 0;
	/** Effective zone radius for this bot — may exceed FarmZone.getBaseRadius() during expansion. */
	private int _effectiveZoneRadius = -1; // -1 = use zone's base radius

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
	/** Priority of the goal that produced the current plan. 0 = no active plan. */
	private int _activeGoalPriority = 0;

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
		// Tick the behaviour controller first (checks timers, handles transitions).
		if (_behaviourController != null)
		{
			_behaviourController.tick(this, now);
		}
		org.l2jmobius.gameserver.bot.core.goap.GoapAgent.tick(this, now);
	}

	// =========================================================================
	// Public API — core
	// =========================================================================

	public Player getPlayer() { return _player; }
	public BotProfile getProfile() { return _profile; }
	public BotRole getRole() { return _role; }
	public BehaviourController getBehaviourController() { return _behaviourController; }
	public void setBehaviourController(BehaviourController controller) { _behaviourController = controller; }

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

	/**
	 * Convenience: clear the queue and immediately enqueue a single action.
	 * Equivalent to {@code clearQueue(); queueAction(action);}.
	 *
	 * @param action the sole action to execute next
	 */
	public void replaceQueue(BotAction action)
	{
		clearQueue();
		queueAction(action);
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
	public void clearTarget()
	{
		_target = null;
		_player.abortAttack();
		_player.setTarget(null);
	}
	public boolean hasTarget() { return (_target != null) && !_target.isDead(); }

	public BotPhase getPhase() { return _phase; }
	public void setPhase(BotPhase phase) { _phase = phase; }

	public float getMistakeRate() { return _profile.getMistakeRate(); }
	public boolean isDead() { return _player.isDead(); }
	public int getPhysicalAttackRange() { return _player.getPhysicalAttackRange(); }

	public boolean isInZone()
	{
		final FarmZone zone = _profile.getZone();
		final int radius = (_effectiveZoneRadius > 0) ? _effectiveZoneRadius : zone.getRadius();
		final long dx = _player.getX() - zone.getX();
		final long dy = _player.getY() - zone.getY();
		return (dx * dx + dy * dy) <= ((long) radius * radius);
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

	public long getLastCombatTime() { return _lastCombatTime; }
	public void updateLastCombatTime() { _lastCombatTime = System.currentTimeMillis(); }

	/**
	 * Returns {@code true} if the bot's HP has dropped since the last snapshot.
	 * Used as a reliable fallback combat indicator when attacker lists haven't
	 * been populated yet (e.g. mob aggroed but hasn't attacked on the server yet).
	 */
	public boolean hasTakenDamageSinceLastTick()
	{
		return (_lastHpSnapshot > 0) && (_player.getCurrentHp() < _lastHpSnapshot - 1.0);
	}

	/** Called at the end of each GoapAgent tick to capture the current HP for next-tick comparison. */
	public void updateHpSnapshot()
	{
		_lastHpSnapshot = _player.getCurrentHp();
	}

	// =========================================================================
	// Dynamic search radius (TargetService)
	// =========================================================================

	/** Base search radius — never shrinks below this value. */
	private static final int SEARCH_RADIUS_BASE = 700;
	/** Maximum search radius before zone expansion kicks in. */
	private static final int SEARCH_RADIUS_PHASE1_MAX = 1000;
	/** Maximum allowed zone radius multiplier. */
	private static final float ZONE_RADIUS_MAX_MULT = 5.0f; // 1000 * 5 = 5000
	/** Growth factor per failed search during phase 1 (search radius). */
	private static final int SEARCH_RADIUS_STEP = 50;
	/** Growth factor per failed search during phase 2 (zone radius). x1.5 per step. */
	private static final float ZONE_RADIUS_STEP = 1.5f;

	public int getSearchRadius() { return _searchRadius; }

	/**
	 * Called when no mob was found. Expands search radius step-by-step:
	 * <ol>
	 *   <li>Phase 1 — search radius grows 50 units/fail up to {@value #SEARCH_RADIUS_PHASE1_MAX}</li>
	 *   <li>Phase 2 — zone radius grows ×1.5/fail up to base×{@value #ZONE_RADIUS_MAX_MULT}</li>
	 * </ol>
	 */
	public void onSearchFailed()
	{
		_searchFailCount++;
		if (_searchRadius < SEARCH_RADIUS_PHASE1_MAX)
		{
			// Phase 1: grow search radius smoothly
			_searchRadius = Math.min(_searchRadius + SEARCH_RADIUS_STEP, SEARCH_RADIUS_PHASE1_MAX);
		}
		else
		{
			// Phase 2: expand the per-bot effective zone radius (does not affect other bots)
			final int baseZoneRadius = _profile.getZone().getBaseRadius();
			final int currentEffective = (_effectiveZoneRadius > 0) ? _effectiveZoneRadius : baseZoneRadius;
			final int maxZoneRadius = (int) (baseZoneRadius * ZONE_RADIUS_MAX_MULT);
			if (currentEffective < maxZoneRadius)
			{
				_effectiveZoneRadius = Math.min((int) (currentEffective * ZONE_RADIUS_STEP), maxZoneRadius);
			}
		}
	}

	/** Called when a target is successfully found — resets all expansion state. */
	public void onSearchSuccess()
	{
		_searchFailCount = 0;
		_searchRadius = SEARCH_RADIUS_BASE;
		_effectiveZoneRadius = -1; // back to base zone radius
	}

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

	/** Returns the last A* waypoint (mob's stored destination), or null if no path. */
	public GeoLocation getLastWaypoint()
	{
		if ((_currentPath == null) || _currentPath.isEmpty())
		{
			return null;
		}
		return _currentPath.get(_currentPath.size() - 1);
	}

	/**
	 * Replaces the last A* waypoint with updated coordinates.
	 * Used by MoveToCreatureAction to track a moving mob without rebuilding the whole path.
	 * Silently skips if the coordinates are outside geo-data bounds.
	 */
	public void updateLastWaypoint(int x, int y, int z)
	{
		if ((_currentPath != null) && !_currentPath.isEmpty())
		{
			try
			{
				_currentPath.set(_currentPath.size() - 1, new GeoLocation(x, y, z));
			}
			catch (Exception ignored)
			{
				// Coordinates may be outside geodata bounds — skip the update silently.
			}
		}
	}

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

	public int getActiveGoalPriority() { return _activeGoalPriority; }
	public void setActiveGoalPriority(int priority) { _activeGoalPriority = priority; }

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
	public void clearGoapPlan()
	{
		_goapPlan.clear();
		_activeGoalPriority = 0;
	}
}
