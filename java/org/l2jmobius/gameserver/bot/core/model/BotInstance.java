/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.model;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

import org.l2jmobius.gameserver.bot.core.brain.BotBrain;
import org.l2jmobius.gameserver.bot.core.brain.BotContext;
import org.l2jmobius.gameserver.bot.core.brain.BotDecision;
import org.l2jmobius.gameserver.bot.core.brain.BotExecutor;
import org.l2jmobius.gameserver.bot.core.brain.TravelReason;
import org.l2jmobius.gameserver.bot.core.action.AttackAction;
import org.l2jmobius.gameserver.bot.core.action.MoveToAction;
import org.l2jmobius.gameserver.bot.core.action.TeleportAction;
import org.l2jmobius.gameserver.bot.core.action.WaitAction;
import org.l2jmobius.gameserver.bot.core.service.EquipService;
import org.l2jmobius.gameserver.bot.core.service.LootService;
import org.l2jmobius.gameserver.bot.core.service.SellService;
import org.l2jmobius.gameserver.bot.core.service.SkillService;
import org.l2jmobius.gameserver.bot.core.service.SupplyService;
import org.l2jmobius.gameserver.bot.core.service.TargetService;
import org.l2jmobius.gameserver.bot.core.zone.BotZoneData;
import org.l2jmobius.gameserver.config.custom.BotConfig;
import org.l2jmobius.gameserver.geoengine.pathfinding.GeoLocation;
import org.l2jmobius.gameserver.model.Location;
import org.l2jmobius.gameserver.model.actor.Creature;
import org.l2jmobius.gameserver.model.actor.Player;

/**
 * Wraps a {@link Player} and owns the bot's full runtime state.
 * <p>
 * Layer 2 entry point: {@link #update(long)} runs the pipeline each tick:
 * <pre>
 *   context → BotBrain.decide → applyDecision → BotExecutor.tick
 * </pre>
 * State is changed only inside {@code update()} — never inside Brain or Executor.
 */
public class BotInstance
{
	private static final Logger LOGGER = Logger.getLogger(BotInstance.class.getName());

	// -------------------------------------------------------------------------
	// Timing constants
	// -------------------------------------------------------------------------

	private static final long THINK_DELAY_MIN = 250;
	private static final long THINK_DELAY_MAX = 400;
	private static final long REVIVE_DELAY_MIN = 1000;
	private static final long REVIVE_DELAY_MAX = 5000;
	private static final long GATE_WAIT_MIN = 15_000;
	private static final long GATE_WAIT_MAX = 25_000;

	private static final float TICKS_PER_MINUTE = 185f;
	private static final float MISTAKE_CHANCE_MIN = 3f;
	private static final float MISTAKE_CHANCE_MAX = 7f;

	// -------------------------------------------------------------------------
	// Core (immutable after construction)
	// -------------------------------------------------------------------------

	private final Player _player;
	private final BotProfile _profile;
	private final BotRole _role;

	// -------------------------------------------------------------------------
	// AI components
	// -------------------------------------------------------------------------

	private final BotBrain _brain = new BotBrain();
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

	private long _nextThinkTime = 0;
	/** DEAD: when to revive. RESTING: when to head back to farm. */
	private long _stateEndTime = 0;
	/** Throttle for TargetService calls in SEARCHING state. */
	private long _nextSearchTime = 0;
	/** Session limit for NOISE bots (0 = unlimited). */
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
	// Main update loop (Layer 2 entry point)
	// =========================================================================

	/**
	 * Called every scheduler tick by BotManager.
	 * Runs the full pipeline: context → decision → state → action → execute.
	 *
	 * @param now current time in ms
	 */
	public void update(long now)
	{
		// Throttle: 250–400 ms between ticks per bot.
		if (now < _nextThinkTime)
		{
			return;
		}
		_nextThinkTime = now + THINK_DELAY_MIN + ThreadLocalRandom.current().nextLong(THINK_DELAY_MAX - THINK_DELAY_MIN);

		// 1. Dead — handle separately, no normal pipeline.
		if (_player.isDead())
		{
			handleDeadState(now);
			return;
		}

		// 2. Time-based transition: RESTING expired → return to farm.
		if ((_state == BotState.RESTING) && (now >= _stateEndTime))
		{
			enterTravel(TravelReason.RETURN_TO_FARM, now);
			return;
		}

		// 3. Drifted out of zone while farming → return.
		if (!isInZone() && ((_state == BotState.SEARCHING) || (_state == BotState.ATTACKING)))
		{
			enterTravel(TravelReason.RETURN_TO_FARM, now);
			return;
		}

		// 4. Random distraction (human-like mistake).
		if (((_state == BotState.ATTACKING) || (_state == BotState.SEARCHING)) && shouldMakeMistake())
		{
			clearTarget();
			_executor.clear();
			_state = BotState.IDLE;
			_executor.add(new WaitAction(2000 + ThreadLocalRandom.current().nextLong(3000)));
			return;
		}

		// 5. Build context snapshot.
		final BotContext ctx = BotContext.of(this);

		// 6. Brain decides.
		final BotDecision decision = _brain.decide(ctx, _state);

		// 7. Apply decision (may change state and queue actions).
		applyDecision(decision, ctx, now);

		// 8. Execute current action.
		_executor.tick(this, now);

		// 9. React to completed queue.
		if (_executor.isIdle())
		{
			onQueueCompleted(ctx, now);
		}
	}

	// =========================================================================
	// State handlers
	// =========================================================================

	private void handleDeadState(long now)
	{
		if (_state != BotState.DEAD)
		{
			_state = BotState.DEAD;
			_executor.clear();
			clearTarget();
			final long delay = REVIVE_DELAY_MIN + ThreadLocalRandom.current().nextLong(REVIVE_DELAY_MAX - REVIVE_DELAY_MIN);
			_stateEndTime = now + delay;
			LOGGER.info("BotInstance: " + _player.getName() + " died, reviving in " + (delay / 1000) + "s");
			return;
		}

		if (now >= _stateEndTime)
		{
			_player.doRevive();
			_player.setRunning();
			enterTravel(TravelReason.RETURN_TO_FARM, now);
			LOGGER.info("BotInstance: " + _player.getName() + " revived");
		}
	}

	private void applyDecision(BotDecision decision, BotContext ctx, long now)
	{
		switch (decision)
		{
			case RETREAT:
			{
				if (_state != BotState.TRAVELING)
				{
					// Low HP → flee to safe zone; else → city to restock/sell.
					final TravelReason reason = ctx.lowHp ? TravelReason.RETURN_TO_FARM : TravelReason.GO_TO_CITY;
					enterTravel(reason, now);
				}
				break;
			}
			case ATTACK_TARGET:
			{
				if (_state != BotState.ATTACKING)
				{
					_state = BotState.ATTACKING;
					_executor.clear();
					_executor.add(new AttackAction());
				}
				break;
			}
			case SEARCH_TARGET:
			{
				if (_state != BotState.SEARCHING)
				{
					_state = BotState.SEARCHING;
					_executor.clear();
					_nextSearchTime = 0;
				}

				if (_executor.isIdle() && (now >= _nextSearchTime))
				{
					// Try loot first, then search for target.
					if (!LootService.pickupNearest(this))
					{
						TargetService.findTarget(this);

						if (hasTarget())
						{
							_state = BotState.ATTACKING;
							_executor.clear();
							_executor.add(new AttackAction());
						}
						else
						{
							// No target — wander to a random zone point.
							final Location wander = _profile.getZone().randomPointInside();
							_executor.add(new WaitAction(3000 + ThreadLocalRandom.current().nextLong(3000)));
							_executor.add(new MoveToAction(wander));
							_nextSearchTime = now + 5000 + ThreadLocalRandom.current().nextLong(5000);
						}
					}
				}
				break;
			}
			case IDLE:
			default:
			{
				if (_executor.isIdle())
				{
					_executor.add(new WaitAction(2000 + ThreadLocalRandom.current().nextLong(2000)));
				}
				break;
			}
		}
	}

	private void onQueueCompleted(BotContext ctx, long now)
	{
		switch (_state)
		{
			case ATTACKING:
			{
				// Target dead — brief pause then search again.
				clearTarget();
				_state = BotState.SEARCHING;
				_nextSearchTime = now + 1000;
				break;
			}
			case TRAVELING:
			{
				onArrived(ctx, now);
				break;
			}
			default:
				break;
		}
	}

	private void onArrived(BotContext ctx, long now)
	{
		if (_travelReason == TravelReason.RETURN_TO_FARM)
		{
			_travelReason = null;
			_state = BotState.SEARCHING;
		}
		else
		{
			// Arrived in city: sell, restock, equip, then rest.
			SellService.sell(this);
			EquipService.equip(this);
			SupplyService.restock(this);
			if (hasLeveledUp())
			{
				SkillService.setup(this);
			}
			enterResting(now);
		}
	}

	// =========================================================================
	// Travel and rest helpers
	// =========================================================================

	private void enterTravel(TravelReason reason, long now)
	{
		_state = BotState.TRAVELING;
		_travelReason = reason;
		_executor.clear();
		clearTarget();
		clearGlobalPath();

		if (reason == TravelReason.RETURN_TO_FARM)
		{
			// City → gatekeeper (walk) → farm (teleport).
			final BotZoneData cityData = _profile.getZone().getCityData();
			if ((cityData != null) && !cityData.getCityPath().isEmpty())
			{
				for (Location wp : cityData.getCityPath())
				{
					_executor.add(new MoveToAction(wp));
				}
				final long wait = GATE_WAIT_MIN + ThreadLocalRandom.current().nextLong(GATE_WAIT_MAX - GATE_WAIT_MIN);
				_executor.add(new WaitAction(wait));
			}
			final Location farm = _profile.getZone().getCenter();
			_executor.add(new TeleportAction(farm.getX(), farm.getY(), farm.getZ()));
		}
		else
		{
			// Farm → city: teleport directly to home location.
			final Location home = _profile.getZone().getHomeLocation();
			_executor.add(new TeleportAction(home.getX(), home.getY(), home.getZ()));
		}

		LOGGER.info("BotInstance: " + _player.getName() + " → TRAVELING (" + reason + ")");
	}

	private void enterResting(long now)
	{
		final long minMs = BotConfig.BOT_CITY_IDLE_MIN_SECONDS * 1000L;
		final long maxMs = BotConfig.BOT_CITY_IDLE_MAX_SECONDS * 1000L;
		final long idleMs = minMs + ThreadLocalRandom.current().nextLong(Math.max(1, maxMs - minMs));
		_stateEndTime = now + idleMs;
		_state = BotState.RESTING;
		LOGGER.info("BotInstance: " + _player.getName() + " RESTING for " + (idleMs / 60_000) + " min");
	}

	// =========================================================================
	// Private helpers
	// =========================================================================

	private boolean hasTarget()
	{
		return (_target != null) && !_target.isDead();
	}

	private boolean shouldMakeMistake()
	{
		final float chance = MISTAKE_CHANCE_MIN + ThreadLocalRandom.current().nextFloat() * (MISTAKE_CHANCE_MAX - MISTAKE_CHANCE_MIN);
		return ThreadLocalRandom.current().nextFloat() < (chance / 100f / TICKS_PER_MINUTE);
	}

	// =========================================================================
	// Public API — used by services and BotManager
	// =========================================================================

	public Player getPlayer() { return _player; }
	public BotProfile getProfile() { return _profile; }
	public BotRole getRole() { return _role; }
	public BotState getState() { return _state; }
	public void setState(BotState state) { _state = state; }

	public FarmZone getZone() { return _profile.getZone(); }

	public Creature getTarget() { return _target; }
	public void setTarget(Creature target) { _target = target; }
	public void clearTarget() { _target = null; }

	public float getMistakeRate() { return _profile.getMistakeRate(); }

	public boolean isDead() { return _player.isDead(); }

	public boolean isInZone()
	{
		return _profile.getZone().contains(_player.getX(), _player.getY());
	}

	// --- Session ---

	public long getSessionEndTime() { return _sessionEndTime; }
	public void setSessionEndTime(long time) { _sessionEndTime = time; }

	public boolean isSessionExpired()
	{
		return (_sessionEndTime > 0) && (System.currentTimeMillis() >= _sessionEndTime);
	}

	// --- Level tracking ---

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
	// Global path (PathService — long-range segments)
	// -------------------------------------------------------------------------

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

	// -------------------------------------------------------------------------
	// Local path (PathService — A* waypoints)
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

	// -------------------------------------------------------------------------
	// Stuck detection (PathService)
	// -------------------------------------------------------------------------

	public long getStuckCheckTime() { return _stuckCheckTime; }
	public void setStuckCheckTime(long time) { _stuckCheckTime = time; }
	public int getLastStuckX() { return _lastStuckX; }
	public int getLastStuckY() { return _lastStuckY; }
	public void setStuckSnapshot(int x, int y) { _lastStuckX = x; _lastStuckY = y; }
}
