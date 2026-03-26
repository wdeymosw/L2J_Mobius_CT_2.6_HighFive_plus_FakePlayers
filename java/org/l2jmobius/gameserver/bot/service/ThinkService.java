/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.service;

import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

import org.l2jmobius.gameserver.ai.Intention;
import org.l2jmobius.gameserver.bot.core.BotSpawner;
import org.l2jmobius.gameserver.bot.model.BotInstance;
import org.l2jmobius.gameserver.bot.model.BotState;
import org.l2jmobius.gameserver.bot.model.BotType;
import org.l2jmobius.gameserver.bot.model.TravelAction;
import org.l2jmobius.gameserver.bot.zone.BotZoneData;
import org.l2jmobius.gameserver.model.Location;
import org.l2jmobius.gameserver.model.actor.Creature;
import org.l2jmobius.gameserver.model.actor.Player;

/**
 * Drives the bot's three-mode state machine each scheduler tick.
 * <p>
 * Modes: {@link BotState#TRAVEL}, {@link BotState#FARM_MOB}, {@link BotState#CITY_IDLE}.<br>
 * FARM_MOB has two internal sub-phases: {@link BotState#SEARCHING} and {@link BotState#ATTACKING}.<br>
 * No game internals are accessed here — only BotInstance API and service calls.
 */
public class ThinkService
{
	private static final Logger LOGGER = Logger.getLogger(ThinkService.class.getName());

	// --- Think throttle ---
	/** Min/max delay between consecutive think calls per bot (ms). */
	private static final long THINK_DELAY_MIN = 250;
	private static final long THINK_DELAY_MAX = 400;

	// --- Revive ---
	/** Random delay after death before reviving (ms). */
	private static final long REVIVE_DELAY_MIN = 1000;
	private static final long REVIVE_DELAY_MAX = 5000;

	// --- Target duration ---
	/** How long a CORE bot keeps the same target before looking for a new one (ms). */
	private static final long TARGET_DURATION_MIN = 5000;
	private static final long TARGET_DURATION_MAX = 15000;

	// --- Pause ---
	/** Duration of a human-like combat pause (ms). */
	private static final long PAUSE_DURATION_MIN = 1000;
	private static final long PAUSE_DURATION_MAX = 3000;
	/** Pause chance per minute (%). */
	private static final float PAUSE_CHANCE_PER_MIN = 1.5f;

	// --- Mistake ---
	/** "Тупняк" chance range per minute (%). Randomised per check to vary between bots. */
	private static final float MISTAKE_CHANCE_MIN_PER_MIN = 3f;
	private static final float MISTAKE_CHANCE_MAX_PER_MIN = 7f;

	/** Approximate ticks per minute at ~325ms average tick interval. */
	private static final float TICKS_PER_MINUTE = 185f;

	/** Arrival radius for TRAVEL destinations (units). */
	private static final int TRAVEL_ARRIVAL_RADIUS = 200;

	/** If travel distance exceeds this, teleport instead of walking (city walls block pathfinding). */
	private static final int TELEPORT_THRESHOLD = 5000;

	private ThinkService()
	{
	}

	// -------------------------------------------------------------------------
	// Main entry point
	// -------------------------------------------------------------------------

	/**
	 * Main think loop — called every scheduler tick per bot.
	 *
	 * @param bot the bot to process
	 */
	public static void think(BotInstance bot)
	{
		final long now = System.currentTimeMillis();

		// Throttle: 250–400 ms between thinks per bot.
		if (now < bot.getNextThinkTime())
		{
			return;
		}
		bot.setNextThinkTime(now + THINK_DELAY_MIN + ThreadLocalRandom.current().nextLong(THINK_DELAY_MAX - THINK_DELAY_MIN));

		// --- 1. Dead? ---
		if (bot.isDead())
		{
			handleDead(bot, now);
			return;
		}

		// --- 2. Session expired? (NOISE bots) ---
		if (bot.isSessionExpired())
		{
			bot.setState(BotState.FARM_MOB);
			return;
		}

		// --- 3. Out of zone? Start travel back (skip during city/travel states). ---
		final BotState state = bot.getState();
		if (!bot.isInZone()
			&& (state != BotState.TRAVEL)
			&& (state != BotState.CITY_IDLE))
		{
			startTravel(bot, bot.getZone().getCenter(), TravelAction.RETURN_TO_ZONE);
			return;
		}

		// --- 4. "Тупняк" — random mistake (skip during city/travel). ---
		if ((state != BotState.TRAVEL)
			&& (state != BotState.CITY_IDLE)
			&& shouldMakeMistake())
		{
			bot.clearTarget();
			bot.setState(BotState.SEARCHING);
			return;
		}

		// --- 5a. Inventory full? Head to city to sell. ---
		if (SellService.needsSell(bot)
			&& (state != BotState.TRAVEL)
			&& (state != BotState.CITY_IDLE))
		{
			bot.clearTarget();
			startTravel(bot, bot.getZone().getHomeLocation(), TravelAction.SELL);
			return;
		}

		// --- 5b. Out of supplies? Head to city to restock. ---
		if (SupplyService.needsRestock(bot)
			&& (state != BotState.TRAVEL)
			&& (state != BotState.CITY_IDLE))
		{
			bot.clearTarget();
			startTravel(bot, bot.getZone().getHomeLocation(), TravelAction.BUY);
			return;
		}

		// --- 6. State machine. ---
		switch (bot.getState())
		{
			case DEAD:
			{
				bot.setState(BotState.SEARCHING);
				break;
			}
			case TRAVEL:
			{
				handleTravel(bot, now);
				break;
			}
			case CITY_IDLE:
			{
				if (now >= bot.getCityIdleEndTime())
				{
					LOGGER.info("ThinkService: " + bot.getPlayer().getName() + " leaving city, heading to zone.");
					startTravel(bot, bot.getZone().getCenter(), TravelAction.RETURN_TO_ZONE);
				}
				break;
			}
			case ATTACKING:
			{
				handleAttacking(bot, now);
				break;
			}
			case FARM_MOB:
			case SEARCHING:
			default:
			{
				handleSearching(bot, now);
				break;
			}
		}
	}

	// -------------------------------------------------------------------------
	// State handlers
	// -------------------------------------------------------------------------

	private static void handleDead(BotInstance bot, long now)
	{
		if (bot.getState() != BotState.DEAD)
		{
			// Just died — arm the revive timer (stored in nextSearchTime to avoid a new field).
			bot.setState(BotState.DEAD);
			bot.clearTarget();
			final long delay = REVIVE_DELAY_MIN + ThreadLocalRandom.current().nextLong(REVIVE_DELAY_MAX - REVIVE_DELAY_MIN);
			bot.setNextSearchTime(now + delay);
			LOGGER.info("ThinkService: " + bot.getPlayer().getName() + " died, reviving in " + (delay / 1000) + "s");
			return;
		}

		if (now >= bot.getNextSearchTime())
		{
			bot.getPlayer().doRevive();
			bot.getPlayer().setRunning();
			startTravel(bot, bot.getZone().getCenter(), TravelAction.RETURN_TO_ZONE);
			LOGGER.info("ThinkService: " + bot.getPlayer().getName() + " revived, returning to zone");
		}
	}

	/**
	 * Starts TRAVEL mode toward {@code dest}, executing {@code action} on arrival.
	 * <p>
	 * For {@link TravelAction#RETURN_TO_ZONE}: if the zone has a {@code cityPath}
	 * defined in {@code BotZones.xml}, the bot will walk through those waypoints
	 * to the gatekeeper first, then teleport to the farm zone center.
	 *
	 * @param bot    the bot to move
	 * @param dest   destination location (farm center for RETURN_TO_ZONE)
	 * @param action what to do when the destination is reached
	 */
	private static void startTravel(BotInstance bot, Location dest, TravelAction action)
	{
		bot.setState(BotState.TRAVEL);
		bot.setTravelAction(action);
		bot.clearCityPath();

		if (action == TravelAction.RETURN_TO_ZONE)
		{
			final BotZoneData cityData = bot.getZone().getCityData();
			if ((cityData != null) && !cityData.getCityPath().isEmpty())
			{
				// Walk through city to gatekeeper first, then teleport to farm.
				bot.setCityPath(cityData.getCityPath());
				bot.setMoveDestination(cityData.getCityPath().get(0));
				LOGGER.info("ThinkService: " + bot.getPlayer().getName() + " → CITY WALK (" + cityData.getCityPath().size() + " waypoints) → " + bot.getZone().getName());
				return;
			}
		}

		bot.setMoveDestination(dest);
		LOGGER.info("ThinkService: " + bot.getPlayer().getName() + " → TRAVEL (" + action + ") to " + bot.getZone().getName());
	}

	/** Min/max wait at the gatekeeper before teleporting (ms). */
	private static final long GATE_WAIT_MIN = 15000;
	private static final long GATE_WAIT_MAX = 25000;

	/**
	 * Handles the TRAVEL state each tick.
	 * <p>
	 * For RETURN_TO_ZONE with a city path: walks waypoint-by-waypoint to the
	 * gatekeeper, waits 15–25 s (human-like), then teleports to the farm zone center.<br>
	 * For SELL/BUY: teleports directly to the city (home location), then
	 * executes the arrival action.
	 *
	 * @param bot the bot in TRAVEL state
	 * @param now current time in ms
	 */
	private static void handleTravel(BotInstance bot, long now)
	{
		// --- Waiting at gatekeeper before teleporting to farm ---
		if (bot.getGateWaitEndTime() > 0)
		{
			if (now >= bot.getGateWaitEndTime())
			{
				bot.setGateWaitEndTime(0);
				final Location farm = bot.getZone().getCenter();
				LOGGER.info("ThinkService: " + bot.getPlayer().getName() + " teleporting to farm zone");
				BotSpawner.teleportBot(bot, farm.getX(), farm.getY(), farm.getZ());
				bot.setMoveDestination(farm);
			}
			return; // still waiting — do nothing
		}

		// --- City walk: waypoints to gatekeeper before the wait ---
		if (bot.hasCityPath())
		{
			handleCityWalk(bot, now);
			return;
		}

		final Location dest = bot.getMoveDestination();
		if (dest != null)
		{
			// Teleport for long distances — city walls block pathfinding.
			final long tdx = bot.getPlayer().getX() - dest.getX();
			final long tdy = bot.getPlayer().getY() - dest.getY();
			if ((tdx * tdx + tdy * tdy) > ((long) TELEPORT_THRESHOLD * TELEPORT_THRESHOLD))
			{
				bot.getPlayer().getAI().setIntention(Intention.IDLE);
				LOGGER.info("ThinkService: " + bot.getPlayer().getName() + " teleporting to " + dest.getX() + "," + dest.getY());
				BotSpawner.teleportBot(bot, dest.getX(), dest.getY(), dest.getZ());
			}
			else
			{
				PathService.thinkMove(bot, dest.getX(), dest.getY(), dest.getZ());
			}
		}

		if (!bot.hasReachedDestination(TRAVEL_ARRIVAL_RADIUS))
		{
			return;
		}

		// Arrived — execute the action.
		bot.setMoveDestination(null);
		switch (bot.getTravelAction())
		{
			case SELL:
			{
				final boolean hasValuables = SellService.sell(bot);
				if (hasValuables)
				{
					// TODO Step 9: open private shop for A/S gear and recipes.
					LOGGER.info("ThinkService: " + bot.getPlayer().getName() + " has valuables — private shop TBD.");
				}
				EquipService.equip(bot);
				SupplyService.restock(bot);
				onEnterCityIdle(bot);
				break;
			}
			case BUY:
			{
				SupplyService.restock(bot);
				onEnterCityIdle(bot);
				break;
			}
			case RETURN_TO_ZONE:
			default:
			{
				bot.setState(BotState.SEARCHING);
				break;
			}
		}
	}

	/**
	 * Walks the bot through the city waypoints one by one.
	 * When all waypoints are visited (gatekeeper reached), arms the gate-wait timer.
	 *
	 * @param bot the bot currently walking through the city
	 * @param now current time in ms
	 */
	private static void handleCityWalk(BotInstance bot, long now)
	{
		final Location waypoint = bot.getCurrentCityWaypoint();
		if (waypoint == null)
		{
			bot.clearCityPath();
			return;
		}

		final Player player = bot.getPlayer();
		final long dx = player.getX() - waypoint.getX();
		final long dy = player.getY() - waypoint.getY();

		if ((dx * dx + dy * dy) <= ((long) TRAVEL_ARRIVAL_RADIUS * TRAVEL_ARRIVAL_RADIUS))
		{
			// Reached this waypoint — advance.
			bot.advanceCityPath();

			if (bot.hasCityPath())
			{
				// Move toward the next waypoint.
				final Location next = bot.getCurrentCityWaypoint();
				bot.setMoveDestination(next);
				LOGGER.info("ThinkService: " + player.getName() + " city walk → next waypoint " + next.getX() + "," + next.getY());
			}
			else
			{
				// All waypoints done — at the gatekeeper. Stop and wait before teleporting.
				bot.clearCityPath();
				bot.setMoveDestination(null); // prevent normal travel from seeing stale destination
				player.getAI().setIntention(Intention.IDLE);
				final long wait = GATE_WAIT_MIN + ThreadLocalRandom.current().nextLong(GATE_WAIT_MAX - GATE_WAIT_MIN);
				bot.setGateWaitEndTime(now + wait);
				LOGGER.info("ThinkService: " + player.getName() + " at gatekeeper — waiting " + (wait / 1000) + "s before teleport");
			}
			return;
		}

		// Not yet at waypoint — issue MOVE_TO (city waypoints are short hops, no PathService needed).
		if (!player.isMoving())
		{
			player.getAI().setIntention(Intention.MOVE_TO, waypoint);
		}
	}

	private static void handleAttacking(BotInstance bot, long now)
	{
		final Creature target = bot.getTarget();

		// Target gone or dead — search for a new one.
		if ((target == null) || target.isDead())
		{
			bot.clearTarget();
			bot.setState(BotState.SEARCHING);
			return;
		}

		// Target duration expired — switch to a new target after 5–15s.
		if ((bot.getTargetExpireTime() > 0) && (now >= bot.getTargetExpireTime()))
		{
			bot.clearTarget();
			bot.setState(BotState.SEARCHING);
			return;
		}

		// Occasional human-like pause (1–3s, ~1.5%/min chance).
		if (shouldPause())
		{
			final long pause = PAUSE_DURATION_MIN + ThreadLocalRandom.current().nextLong(PAUSE_DURATION_MAX - PAUSE_DURATION_MIN);
			bot.setNextThinkTime(now + pause);
			bot.getPlayer().getAI().setIntention(Intention.IDLE);
			return;
		}

		// ATTACK intention handles movement (maybeMoveToPawn) internally.
		if (!SkillService.tryDamageSkill(bot))
		{
			CombatService.attack(bot);
		}
	}

	private static void handleSearching(BotInstance bot, long now)
	{
		// NOISE bots don't hunt — handled separately.
		if (bot.getProfile().getType() == BotType.NOISE)
		{
			bot.setState(BotState.FARM_MOB);
			return;
		}

		// Pick up ground items before looking for a new target.
		if (LootService.pickupNearest(bot))
		{
			return;
		}

		// Navigate to current wander destination via PathService (handles walls).
		final org.l2jmobius.gameserver.model.Location wanderDest = bot.getMoveDestination();
		if ((wanderDest != null) && !bot.hasReachedDestination(TRAVEL_ARRIVAL_RADIUS))
		{
			PathService.thinkMove(bot, wanderDest.getX(), wanderDest.getY(), wanderDest.getZ());
		}

		if (now >= bot.getNextSearchTime())
		{
			TargetService.findTarget(bot);

			if (bot.getTarget() != null)
			{
				bot.setMoveDestination(null);
				bot.clearGlobalPath();
				// Randomise how long this target is kept (5–15s).
				final long duration = TARGET_DURATION_MIN + ThreadLocalRandom.current().nextLong(TARGET_DURATION_MAX - TARGET_DURATION_MIN);
				bot.setTargetExpireTime(now + duration);
				LOGGER.info("ThinkService: " + bot.getPlayer().getName() + " → attacking " + bot.getTarget().getName());
				bot.setState(BotState.ATTACKING);
			}
			else
			{
				// No target found — wander to a random spot in the zone via PathService.
				final org.l2jmobius.gameserver.model.Location wander = bot.getZone().randomPointInside();
				bot.setMoveDestination(wander);
				// Wait 3–6s before searching again.
				final long wanderDelay = 3000 + ThreadLocalRandom.current().nextLong(3000);
				bot.setNextSearchTime(now + wanderDelay);
			}
		}
	}

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	/**
	 * Called once when the bot enters CITY_IDLE (after selling or buying).
	 * Good time to give new skills — happens at most a few times per hour.
	 *
	 * @param bot the bot that just entered city idle
	 */
	private static void onEnterCityIdle(BotInstance bot)
	{
		if (bot.hasLeveledUp())
		{
			SkillService.setup(bot);
		}
	}

	/** @return true with 3–7% probability per minute, evaluated per tick. */
	private static boolean shouldMakeMistake()
	{
		final float chancePerMin = MISTAKE_CHANCE_MIN_PER_MIN + ThreadLocalRandom.current().nextFloat() * (MISTAKE_CHANCE_MAX_PER_MIN - MISTAKE_CHANCE_MIN_PER_MIN);
		return ThreadLocalRandom.current().nextFloat() < (chancePerMin / 100f / TICKS_PER_MINUTE);
	}

	/** @return true with ~1.5% probability per minute. */
	private static boolean shouldPause()
	{
		return ThreadLocalRandom.current().nextFloat() < (PAUSE_CHANCE_PER_MIN / 100f / TICKS_PER_MINUTE);
	}
}
