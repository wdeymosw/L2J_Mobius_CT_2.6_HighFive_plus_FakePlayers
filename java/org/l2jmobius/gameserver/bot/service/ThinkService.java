/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.service;

import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

import org.l2jmobius.gameserver.ai.Intention;
import org.l2jmobius.gameserver.bot.model.BotInstance;
import org.l2jmobius.gameserver.bot.model.BotState;
import org.l2jmobius.gameserver.bot.model.BotType;
import org.l2jmobius.gameserver.model.actor.Creature;

/**
 * Drives the bot's state machine each scheduler tick.
 * <p>
 * Called by BotManager for every active bot. Decides what to do based on
 * current state, then delegates actions to TargetService / CombatService.
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
	/** Pause chance per minute (%). ~1% means roughly once every 100 minutes of combat. */
	private static final float PAUSE_CHANCE_PER_MIN = 1.5f;

	// --- Stuck detection ---
	private static final long STUCK_CHECK_INTERVAL = 5000;
	private static final int STUCK_MIN_DISTANCE = 50;

	// --- Mistake ---
	/** "Тупняк" chance range per minute (%). Randomised per check to vary between bots. */
	private static final float MISTAKE_CHANCE_MIN_PER_MIN = 3f;
	private static final float MISTAKE_CHANCE_MAX_PER_MIN = 7f;

	/** Approximate ticks per minute at ~325ms average tick interval. */
	private static final float TICKS_PER_MINUTE = 185f;

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
			bot.setState(BotState.IDLE);
			return;
		}

		// --- 3. Out of zone? Return (skip if in city states). ---
		if (!bot.isInZone()
			&& (bot.getState() != BotState.RETURNING)
			&& (bot.getState() != BotState.BUYING)
			&& (bot.getState() != BotState.CITY_IDLE))
		{
			startReturn(bot);
			return;
		}

		// --- 4. "Тупняк" — random mistake (skip during city phases). ---
		if ((bot.getState() != BotState.BUYING) && (bot.getState() != BotState.CITY_IDLE) && shouldMakeMistake())
		{
			bot.clearTarget();
			bot.setState(BotState.SEARCHING);
			return;
		}

		// --- 5. Out of supplies? Restock before farming. ---
		if (SupplyService.needsRestock(bot)
			&& (bot.getState() != BotState.BUYING)
			&& (bot.getState() != BotState.CITY_IDLE)
			&& (bot.getState() != BotState.RETURNING))
		{
			bot.clearTarget();
			SupplyService.restock(bot);
			return;
		}

		// --- 6. State machine. ---
		switch (bot.getState())
		{
			case DEAD:
			{
				bot.setState(BotState.IDLE);
				break;
			}
			case RETURNING:
			{
				if (PathService.tickPath(bot) && bot.isInZone())
				{
					bot.setState(BotState.IDLE);
				}
				else if (!bot.isInZone() && !bot.hasPath())
				{
					startReturn(bot);
				}
				break;
			}
			case BUYING:
			{
				// Safety: restock() transitions to CITY_IDLE immediately,
				// so this branch only runs if something went wrong.
				SupplyService.restock(bot);
				break;
			}
			case CITY_IDLE:
			{
				if (now >= bot.getCityIdleEndTime())
				{
					LOGGER.info("ThinkService: " + bot.getPlayer().getName() + " leaving city, heading to zone.");
					bot.setState(BotState.IDLE); // out of zone → next tick triggers RETURNING
				}
				break;
			}
			case ATTACKING:
			{
				handleAttacking(bot, now);
				break;
			}
			case IDLE:
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
			bot.setState(BotState.RETURNING);
			PathService.navigateTo(bot, bot.getZone().getX(), bot.getZone().getY(), bot.getZone().getZ());
			LOGGER.info("ThinkService: " + bot.getPlayer().getName() + " revived, returning to zone");
		}
	}

	private static void startReturn(BotInstance bot)
	{
		bot.setState(BotState.RETURNING);
		bot.clearTarget();
		final int cx = bot.getZone().getX();
		final int cy = bot.getZone().getY();
		final int cz = bot.getZone().getZ();
		PathService.navigateTo(bot, cx, cy, cz);
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

		// Stuck detection: bot hasn't moved enough in STUCK_CHECK_INTERVAL.
		if ((now - bot.getLastMoveCheckTime()) > STUCK_CHECK_INTERVAL)
		{
			final int dx = bot.getPlayer().getX() - bot.getLastX();
			final int dy = bot.getPlayer().getY() - bot.getLastY();
			if (Math.sqrt(dx * dx + dy * dy) < STUCK_MIN_DISTANCE)
			{
				final Creature stuckTarget = bot.getTarget();
				if (stuckTarget != null)
				{
					// Try to path around the obstacle.
					PathService.navigateTo(bot, stuckTarget.getX(), stuckTarget.getY(), stuckTarget.getZ());
				}
				else
				{
					bot.setState(BotState.SEARCHING);
				}
				bot.updatePositionSnapshot();
				return;
			}
			bot.updatePositionSnapshot();
		}

		// Occasional human-like pause (1–3s, ~1.5%/min chance).
		if (shouldPause())
		{
			final long pause = PAUSE_DURATION_MIN + ThreadLocalRandom.current().nextLong(PAUSE_DURATION_MAX - PAUSE_DURATION_MIN);
			bot.setNextThinkTime(now + pause);
			bot.getPlayer().getAI().setIntention(Intention.IDLE);
			return;
		}

		// Try a damage skill first; fall back to autoattack if nothing is ready.
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
			bot.setState(BotState.IDLE);
			return;
		}

		// Pick up ground items before looking for a new target.
		if (LootService.pickupNearest(bot))
		{
			return;
		}

		if (now >= bot.getNextSearchTime())
		{
			TargetService.findTarget(bot);

			if (bot.getTarget() != null)
			{
				// Randomise how long this target is kept (5–15s).
				final long duration = TARGET_DURATION_MIN + ThreadLocalRandom.current().nextLong(TARGET_DURATION_MAX - TARGET_DURATION_MIN);
				bot.setTargetExpireTime(now + duration);
				LOGGER.info("ThinkService: " + bot.getPlayer().getName() + " → attacking " + bot.getTarget().getName());
				bot.setState(BotState.ATTACKING);
			}
			else
			{
				LOGGER.info("ThinkService: " + bot.getPlayer().getName() + " found no targets in range");
			}
		}
	}

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

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
