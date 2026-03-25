/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.service;

import java.util.List;
import java.util.logging.Logger;

import org.l2jmobius.gameserver.bot.model.BotInstance;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.geoengine.pathfinding.GeoLocation;
import org.l2jmobius.gameserver.geoengine.pathfinding.PathFinding;
import org.l2jmobius.gameserver.model.Location;
import org.l2jmobius.gameserver.model.actor.Player;

/**
 * Universal movement engine for bots.
 * <p>
 * {@link #thinkMove(BotInstance, int, int, int)} is the single entry point
 * called every tick for any movement goal — combat, returning to zone,
 * walking to city, wandering. No special cases per state.
 * <p>
 * Algorithm per tick:
 * <ol>
 *   <li>Anti-stuck check (every {@value #STUCK_CHECK_SECONDS}s): if the bot
 *       covered less than {@value #STUCK_FRACTION} × expected speed × time,
 *       the current path is cleared so it is recalculated.</li>
 *   <li>If geodata line-of-movement is clear → move directly, drop stale path.</li>
 *   <li>Otherwise → build or reuse an A* path; recalculate if destination
 *       shifted more than {@value #TARGET_MOVE_THRESHOLD} units.</li>
 *   <li>Follow path waypoint by waypoint; fall back to direct nudge if A* fails.</li>
 * </ol>
 */
public class PathService
{
	private static final Logger LOGGER = Logger.getLogger(PathService.class.getName());

	/** Radius (units) to consider "arrived" at a waypoint. */
	private static final int WAYPOINT_ARRIVAL_RADIUS = 50;

	/** Minimum time (ms) between two A* path calculations. */
	private static final long PATH_RECALC_COOLDOWN_MS = 1000;

	/**
	 * Recalculate path when destination shifts further than this (units).
	 * Relevant for moving targets (combat). Static destinations never trigger this.
	 */
	private static final int TARGET_MOVE_THRESHOLD = 100;

	// -------------------------------------------------------------------------
	// Movement check timings — edit here to tune bot responsiveness
	// -------------------------------------------------------------------------

	/**
	 * How often (ms) to drop a stale A* path once a direct line becomes clear.
	 * Lower = snappier reaction to cleared obstacles.
	 */
	private static final long DIRECT_CLEAR_CHECK_MS = 500;

	/**
	 * How often (seconds) to verify the bot is actually moving.
	 * Longer than DIRECT_CLEAR_CHECK_MS to avoid false positives on short pauses.
	 */
	private static final double STUCK_CHECK_SECONDS = 2.0;

	/**
	 * Bot is considered stuck if it covered less than this fraction of the
	 * distance expected from its current run speed × elapsed time.
	 */
	private static final double STUCK_FRACTION = 0.3;

	private PathService()
	{
	}

	// -------------------------------------------------------------------------
	// Public API — single method for all bot movement
	// -------------------------------------------------------------------------

	/**
	 * Moves the bot one step toward ({@code tx}, {@code ty}, {@code tz}).
	 * Call every tick regardless of whether the destination is a moving target
	 * or a fixed world point. The method is stateless externally — all path
	 * state is stored in {@link BotInstance}.
	 *
	 * @param bot the bot to move
	 * @param tx  destination X
	 * @param ty  destination Y
	 * @param tz  destination Z
	 */
	public static void thinkMove(BotInstance bot, int tx, int ty, int tz)
	{
		final Player player = bot.getPlayer();
		final long now = System.currentTimeMillis();

		final int px = player.getX();
		final int py = player.getY();
		final int pz = player.getZ();

		// --- 1. Anti-stuck: clear path if bot hasn't moved enough ---
		handleStuck(bot, now, px, py);

		// --- 2. Direct geodata line available? ---
		if (GeoEngine.getInstance().canMoveToTarget(px, py, pz, tx, ty, tz, player.getInstanceId()))
		{
			if (bot.hasPath() && ((now - bot.getLastPathTime()) > DIRECT_CLEAR_CHECK_MS))
			{
				bot.clearPath();
			}
			CombatService.moveTo(bot, new Location(tx, ty, tz));
			return;
		}

		// --- 3. Need a new A* path? ---
		boolean needNewPath = !bot.hasPath();

		if (!needNewPath)
		{
			final Location last = bot.getLastTargetPos();
			if (last != null)
			{
				final int ldx = last.getX() - tx;
				final int ldy = last.getY() - ty;
				if (((ldx * ldx) + (ldy * ldy)) > (TARGET_MOVE_THRESHOLD * TARGET_MOVE_THRESHOLD))
				{
					needNewPath = true;
				}
			}
		}

		if (needNewPath && ((now - bot.getLastPathTime()) > PATH_RECALC_COOLDOWN_MS))
		{
			final List<GeoLocation> path = PathFinding.getInstance().findPath(px, py, pz, tx, ty, tz, player.getInstanceId(), true);
			bot.setPath(path);
			bot.setLastPathTime(now);
			bot.setLastTargetPos(new Location(tx, ty, tz));
		}

		// --- 4. Follow A* path waypoint by waypoint ---
		if (bot.hasPath())
		{
			final GeoLocation wp = bot.getCurrentWaypoint();
			if (wp != null)
			{
				final int dx = px - wp.getX();
				final int dy = py - wp.getY();
				if (((dx * dx) + (dy * dy)) < (WAYPOINT_ARRIVAL_RADIUS * WAYPOINT_ARRIVAL_RADIUS))
				{
					bot.advanceWaypoint();
				}
				else
				{
					CombatService.moveTo(bot, new Location(wp.getX(), wp.getY(), wp.getZ()));
				}
			}
		}
		else
		{
			// Fallback: A* returned nothing — nudge directly.
			CombatService.moveTo(bot, new Location(tx, ty, tz));
		}
	}

	// -------------------------------------------------------------------------
	// Internal
	// -------------------------------------------------------------------------

	private static void handleStuck(BotInstance bot, long now, int px, int py)
	{
		final double elapsed = (now - bot.getLastMoveCheckTime()) / 1000.0;
		if (elapsed < STUCK_CHECK_SECONDS)
		{
			return;
		}

		final double traveled = Math.hypot(px - bot.getLastX(), py - bot.getLastY());
		final double expected = bot.getPlayer().getMoveSpeed() * elapsed;

		if ((expected > 0) && (traveled < expected * STUCK_FRACTION))
		{
			bot.clearPath();
			LOGGER.fine("PathService: " + bot.getPlayer().getName() + " stuck (moved " + (int) traveled + " / expected " + (int) expected + ") — recalculating");
		}

		bot.updatePositionSnapshot();
	}
}
