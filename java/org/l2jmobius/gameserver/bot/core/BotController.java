/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core;

import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

import org.l2jmobius.gameserver.bot.core.action.MoveToAction;
import org.l2jmobius.gameserver.bot.core.action.TeleportAction;
import org.l2jmobius.gameserver.bot.core.action.TravelReason;
import org.l2jmobius.gameserver.bot.core.action.WaitAction;
import org.l2jmobius.gameserver.bot.core.brain.ActiveBehavior;
import org.l2jmobius.gameserver.bot.core.brain.BotBehavior;
import org.l2jmobius.gameserver.bot.core.brain.BotContext;
import org.l2jmobius.gameserver.bot.core.brain.PassiveBehavior;
import org.l2jmobius.gameserver.bot.core.model.BotInstance;
import org.l2jmobius.gameserver.bot.core.model.BotState;
import org.l2jmobius.gameserver.bot.core.model.BotType;
import org.l2jmobius.gameserver.bot.core.zone.BotZoneData;
import org.l2jmobius.gameserver.config.custom.BotConfig;
import org.l2jmobius.gameserver.model.Location;

/**
 * Layer 2 entry point — orchestrates one tick for a bot.
 * <p>
 * Handles cross-cutting concerns (throttle, dead, state transitions, random mistakes),
 * then delegates to the bot's {@link BotBehavior} strategy based on {@link BotType}.
 * <p>
 * State-transition helpers ({@link #enterTravel}, {@link #enterResting}) are public so
 * behavior implementations can call them without duplicating the logic.
 */
public class BotController
{
	private static final Logger LOGGER = Logger.getLogger(BotController.class.getName());

	private static final long THINK_DELAY_MIN = 250;
	private static final long THINK_DELAY_MAX = 400;
	private static final long REVIVE_DELAY_MIN = 1000;
	private static final long REVIVE_DELAY_MAX = 5000;
	private static final long GATE_WAIT_MIN = 15_000;
	private static final long GATE_WAIT_MAX = 25_000;

	private static final float TICKS_PER_MINUTE = 185f;
	private static final float MISTAKE_CHANCE_MIN = 3f;
	private static final float MISTAKE_CHANCE_MAX = 7f;

	private static final BotBehavior ACTIVE_BEHAVIOR = new ActiveBehavior();
	private static final BotBehavior PASSIVE_BEHAVIOR = new PassiveBehavior();

	private BotController()
	{
	}

	// =========================================================================
	// Main entry point
	// =========================================================================

	/**
	 * Called every scheduler tick by BotManager.
	 * Runs cross-cutting checks then delegates to the bot's behavior strategy.
	 *
	 * @param bot the bot to tick
	 * @param now current time in ms
	 */
	public static void tick(BotInstance bot, long now)
	{
		// Throttle: 250–400 ms between ticks per bot.
		if (now < bot.getNextThinkTime())
		{
			return;
		}
		bot.setNextThinkTime(now + THINK_DELAY_MIN + ThreadLocalRandom.current().nextLong(THINK_DELAY_MAX - THINK_DELAY_MIN));

		// Dead — separate pipeline, no normal behavior.
		if (bot.getPlayer().isDead())
		{
			handleDead(bot, now);
			return;
		}

		// RESTING timer expired → return to farm.
		if ((bot.getState() == BotState.RESTING) && (now >= bot.getStateEndTime()))
		{
			enterTravel(bot, TravelReason.RETURN_TO_FARM, now);
			return;
		}

		// Drifted out of zone while farming → return.
		if (!bot.isInZone() && ((bot.getState() == BotState.SEARCHING) || (bot.getState() == BotState.ATTACKING)))
		{
			enterTravel(bot, TravelReason.RETURN_TO_FARM, now);
			return;
		}

		// Random distraction (human-like mistake).
		if (((bot.getState() == BotState.ATTACKING) || (bot.getState() == BotState.SEARCHING)) && shouldMakeMistake())
		{
			bot.clearTarget();
			bot.getExecutor().clear();
			bot.setState(BotState.IDLE);
			bot.getExecutor().add(new WaitAction(2000 + ThreadLocalRandom.current().nextLong(3000)));
			return;
		}

		// Delegate to behavior strategy.
		final BotContext ctx = BotContext.of(bot);
		final BotBehavior behavior = bot.getProfile().getType() == BotType.ACTIVE ? ACTIVE_BEHAVIOR : PASSIVE_BEHAVIOR;
		behavior.think(bot, ctx, now);
	}

	// =========================================================================
	// State-transition helpers (public — called by behavior implementations)
	// =========================================================================

	/**
	 * Transitions the bot into TRAVELING state toward the given destination.
	 * Queues city-walk waypoints + gate wait (when returning to farm), or a
	 * direct teleport to the city home location (when going to city).
	 */
	public static void enterTravel(BotInstance bot, TravelReason reason, long now)
	{
		bot.setState(BotState.TRAVELING);
		bot.setTravelReason(reason);
		bot.getExecutor().clear();
		bot.clearTarget();
		bot.clearGlobalPath();

		if (reason == TravelReason.RETURN_TO_FARM)
		{
			// City → gatekeeper (walk) → farm (teleport).
			final BotZoneData cityData = bot.getProfile().getZone().getCityData();
			if ((cityData != null) && !cityData.getCityPath().isEmpty())
			{
				for (Location wp : cityData.getCityPath())
				{
					bot.getExecutor().add(new MoveToAction(wp));
				}
				final long wait = GATE_WAIT_MIN + ThreadLocalRandom.current().nextLong(GATE_WAIT_MAX - GATE_WAIT_MIN);
				bot.getExecutor().add(new WaitAction(wait));
			}
			final Location farm = bot.getProfile().getZone().getCenter();
			bot.getExecutor().add(new TeleportAction(farm.getX(), farm.getY(), farm.getZ()));
		}
		else
		{
			// Farm → city: teleport directly to home location.
			final Location home = bot.getProfile().getZone().getHomeLocation();
			bot.getExecutor().add(new TeleportAction(home.getX(), home.getY(), home.getZ()));
		}

		LOGGER.info("BotController: " + bot.getPlayer().getName() + " → TRAVELING (" + reason + ")");
	}

	/**
	 * Transitions the bot into RESTING state for a randomised city-idle duration.
	 */
	public static void enterResting(BotInstance bot, long now)
	{
		final long minMs = BotConfig.BOT_CITY_IDLE_MIN_SECONDS * 1000L;
		final long maxMs = BotConfig.BOT_CITY_IDLE_MAX_SECONDS * 1000L;
		final long idleMs = minMs + ThreadLocalRandom.current().nextLong(Math.max(1, maxMs - minMs));
		bot.setStateEndTime(now + idleMs);
		bot.setState(BotState.RESTING);
		LOGGER.info("BotController: " + bot.getPlayer().getName() + " RESTING for " + (idleMs / 60_000) + " min");
	}

	// =========================================================================
	// Private helpers
	// =========================================================================

	private static void handleDead(BotInstance bot, long now)
	{
		if (bot.getState() != BotState.DEAD)
		{
			bot.setState(BotState.DEAD);
			bot.getExecutor().clear();
			bot.clearTarget();
			final long delay = REVIVE_DELAY_MIN + ThreadLocalRandom.current().nextLong(REVIVE_DELAY_MAX - REVIVE_DELAY_MIN);
			bot.setStateEndTime(now + delay);
			LOGGER.info("BotController: " + bot.getPlayer().getName() + " died, reviving in " + (delay / 1000) + "s");
			return;
		}

		if (now >= bot.getStateEndTime())
		{
			bot.getPlayer().doRevive();
			bot.getPlayer().setRunning();
			enterTravel(bot, TravelReason.RETURN_TO_FARM, now);
			LOGGER.info("BotController: " + bot.getPlayer().getName() + " revived");
		}
	}

	private static boolean shouldMakeMistake()
	{
		final float chance = MISTAKE_CHANCE_MIN + ThreadLocalRandom.current().nextFloat() * (MISTAKE_CHANCE_MAX - MISTAKE_CHANCE_MIN);
		return ThreadLocalRandom.current().nextFloat() < (chance / 100f / TICKS_PER_MINUTE);
	}
}
