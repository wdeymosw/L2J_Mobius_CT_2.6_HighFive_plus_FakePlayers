/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import org.l2jmobius.gameserver.ai.Intention;
import org.l2jmobius.gameserver.bot.core.model.BotInstance;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.geoengine.pathfinding.GeoLocation;
import org.l2jmobius.gameserver.geoengine.pathfinding.PathFinding;
import org.l2jmobius.gameserver.model.Location;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.bot.core.service.TargetService;

/**
 * Two-level movement engine for bots.
 * <p>
 * <b>Global path</b> — straight-line segment endpoints spaced
 * {@value #SEGMENT_STEP} units apart. Guides the bot across the map.<br>
 * <b>Local path</b> — {@code PathFinding.findPath()} waypoints for the
 * current segment (≤ {@value #SEGMENT_STEP} units). Handles walls and
 * obstacles within each hop.<br>
 * <b>Direct move</b> — when {@code canMoveToTarget()} is true for the
 * current segment target, skip PathFinding and use {@code MOVE_TO} directly.
 * <p>
 * Anti-stuck: every {@value #STUCK_CHECK_MS} ms compare current position to
 * the snapshot taken at the previous check. If the bot has not moved at least
 * 30% of {@code speed × elapsed}, clear the local path so PathFinding retries.
 * <p>
 * PathFinding rate limit: at most {@value #MAX_PATH_CALLS_PER_WINDOW} calls
 * per {@value #PATH_RATE_WINDOW_MS} ms window across all bots (prevents lag
 * spikes when many bots hit walls simultaneously).
 */
public class PathService
{
	private static final Logger LOGGER = Logger.getLogger(PathService.class.getName());

	/** Size of each global-path segment (units). Also the max range for GeoEngine checks. */
	private static final int SEGMENT_STEP = 600;

	/** Arrival radius to consider "reached the segment target" (units). */
	private static final int SEGMENT_RADIUS = 60;

	/** Arrival radius to consider "reached the final destination" (units). */
	private static final int ARRIVAL_RADIUS = 60;

	/** Arrival radius for local waypoint advance (units). */
	private static final int WAYPOINT_RADIUS = 50;

	/** Minimum ms between PathFinding calls for the same bot (per-bot cooldown). */
	private static final long PATH_COOLDOWN_MS = 1000;

	/** Anti-stuck check interval (ms). */
	private static final long STUCK_CHECK_MS = 3000;

	/** After this many consecutive stuck triggers, teleport to destination. */
	private static final int STUCK_TELEPORT_THRESHOLD = 10;

	/** At this stuck count, start trying escape maneuvers (left/right/back). */
	private static final int STUCK_ESCAPE_THRESHOLD = 3;

	/** At this stuck count, force a full repath (clear global + local path). */
	private static final int STUCK_REPATH_THRESHOLD = 7;

	/** Escape distance (units) for left/right/backward sidestep. */
	private static final int ESCAPE_DIST = 200;

	/** Max PathFinding calls across all bots within PATH_RATE_WINDOW_MS. */
	private static final int MAX_PATH_CALLS_PER_WINDOW = 10;

	/** Rate-limit window duration (ms). */
	private static final long PATH_RATE_WINDOW_MS = 300;

	// --- Global PathFinding rate limiter ---
	private static final AtomicInteger _pathCallsThisWindow = new AtomicInteger(0);
	private static volatile long _pathRateWindowStart = 0;

	private PathService()
	{
	}

	// -------------------------------------------------------------------------
	// Public API
	// -------------------------------------------------------------------------

	/**
	 * Moves the bot one step toward ({@code tx}, {@code ty}, {@code tz}).
	 * Call every tick for any movement goal (TRAVEL, wander, etc.).
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

		// --- Guard: skip movement when the character cannot physically move.
		// Reset stuck counter so these states never accumulate toward teleportation. ---
		if (player.isDead() || player.isSitting() || player.isStunned() || player.isParalyzed() || player.isImmobilized() || player.isRooted() || player.isOverloaded())
		{
			bot.resetStuckCount();
			bot.setStuckCheckTime(now); // refresh snapshot time so the pause isn't counted
			bot.setStuckSnapshot(px, py);
			return;
		}

		// --- Arrival check (final destination) ---
		final long adx = px - tx;
		final long ady = py - ty;
		if ((adx * adx + ady * ady) < (ARRIVAL_RADIUS * ARRIVAL_RADIUS))
		{
			player.getAI().setIntention(Intention.IDLE);
			bot.clearGlobalPath(); // also clears local path
			bot.setLastPathTime(0);
			bot.setLastDirectCheckTime(0);
			bot.setStuckCheckTime(0);
			bot.setStuckSnapshot(Integer.MIN_VALUE, Integer.MIN_VALUE);
			return;
		}

		// --- Anti-stuck ---
		if (handleStuck(bot, player, tx, ty, tz, now))
		{
			return; // teleported — skip the rest of this tick
		}

		// --- Global path: build or validate ---
		final double totalDist = Math.sqrt((double) (adx * adx) + (double) (ady * ady));
		if (totalDist > SEGMENT_STEP)
		{
			if (!bot.hasGlobalPath() || !bot.isGlobalPathTo(tx, ty))
			{
				bot.setGlobalPath(buildSegments(px, py, pz, tx, ty, tz));
				LOGGER.info("PathService: " + player.getName() + " global path → " + (int) (totalDist / SEGMENT_STEP + 1) + " segments, dist=" + (int) totalDist);
			}
		}
		else if (bot.hasGlobalPath())
		{
			// Came close enough — discard global path, navigate directly to dest.
			bot.clearGlobalPath();
		}

		// --- Determine navigation target for this tick ---
		final Location navTarget;
		if (bot.hasGlobalPath())
		{
			final Location seg = bot.getCurrentGlobalPoint();
			if (seg == null)
			{
				bot.clearGlobalPath();
				navTarget = new Location(tx, ty, tz);
			}
			else
			{
				final int sdx = px - seg.getX();
				final int sdy = py - seg.getY();
				if ((sdx * sdx + sdy * sdy) < (SEGMENT_RADIUS * SEGMENT_RADIUS))
				{
					// Reached this segment — advance (also clears local path).
					bot.advanceGlobalIndex();
					return;
				}
				navTarget = seg;
			}
		}
		else
		{
			navTarget = new Location(tx, ty, tz);
		}

		// --- Local navigation to navTarget ---
		handleLocalMove(bot, player, navTarget, now);
	}

	// -------------------------------------------------------------------------
	// Local navigation
	// -------------------------------------------------------------------------

	// Navigates the bot to target:
	//   - if already moving via engine (no local path) → trust it
	//   - if following waypoints → advance or re-issue MOVE_TO
	//   - if stopped with no path → try PathFinding (ignores canMoveToTarget so
	//     we don't keep running into a wall that geodata misreported as passable)
	//   - PathFinding failed/throttled → canMoveToTarget as last-resort MOVE_TO
	private static void handleLocalMove(BotInstance bot, Player player, Location target, long now)
	{
		final int px = player.getX();
		final int py = player.getY();
		final int pz = player.getZ();
		final int tx = target.getX();
		final int ty = target.getY();
		final int tz = target.getZ();

		// --- Already following a waypoint path ---
		if (bot.hasPath())
		{
			// Periodically check if direct line opened up — drop waypoints if so.
			if ((now - bot.getLastDirectCheckTime()) >= 500)
			{
				bot.setLastDirectCheckTime(now);
				if (GeoEngine.getInstance().canMoveToTarget(px, py, pz, tx, ty, tz, player.getInstanceId()))
				{
					bot.clearPath();
					// fall through to "stopped, no path" branch below
				}
			}

			if (bot.hasPath())
			{
				final GeoLocation wp = bot.getCurrentWaypoint();
				if (wp == null)
				{
					bot.clearPath();
					return;
				}
				final int wdx = px - wp.getX();
				final int wdy = py - wp.getY();
				if ((wdx * wdx + wdy * wdy) < (WAYPOINT_RADIUS * WAYPOINT_RADIUS))
				{
					bot.advanceWaypoint();
				}
				else if (!player.isMoving())
				{
					player.getAI().setIntention(Intention.MOVE_TO, new Location(wp.getX(), wp.getY(), wp.getZ()));
				}
				return;
			}
		}

		// --- Engine MOVE_TO is still running (no local path) ---
		if (player.isMoving())
		{
			return; // trust it; stuck detection will handle a stall
		}

		// --- Stopped with no path: find a route via PathFinding only.
		// No MOVE_TO fallback — a blind move into a wall causes stuck loops.
		// If PathFinding fails or is throttled, signal FAIL so the caller can react.
		if ((now - bot.getLastPathTime()) >= PATH_COOLDOWN_MS)
		{
			if (tryPathFinding(bot, player, px, py, pz, tx, ty, tz, now))
			{
				return; // waypoints set — will be followed next tick
			}
			// PathFinding returned no path — signal failure.
			LOGGER.info("PathService: " + player.getName() + " no path to " + tx + "," + ty + " — signalling pathFailed");
			bot.setPathFailed(true);
		}
		// else: still in cooldown — wait for next tick, no action needed.
	}

	// -------------------------------------------------------------------------
	// Anti-stuck
	// -------------------------------------------------------------------------

	// Escape angle constants for sidestep maneuvers.
	// Angle = 105° from forward direction (= 75° from backward) → slightly backward + mostly sideways.
	// cos(105°) = −cos(75°) ≈ −0.2588,  sin(105°) = sin(75°) ≈ 0.9659
	private static final double ESC_COS = -0.2588;
	private static final double ESC_SIN = 0.9659;

	// Sequence: counts 1-2 → normal retry, 3 → left-75°, 4 → right-75°,
	//           5 → backward-180°, 6+ → force repath, 10 → teleport.
	//
	// Returns true if the bot was teleported (caller should skip the rest of tick).
	private static boolean handleStuck(BotInstance bot, Player player, int tx, int ty, int tz, long now)
	{
		if ((now - bot.getStuckCheckTime()) < STUCK_CHECK_MS)
		{
			return false;
		}

		final double elapsed = (now - bot.getStuckCheckTime()) / 1000.0;
		final int lx = bot.getLastStuckX();
		final int ly = bot.getLastStuckY();
		final int px = player.getX();
		final int py = player.getY();
		final int pz = player.getZ();

		bot.setStuckCheckTime(now);
		bot.setStuckSnapshot(px, py);

		if (lx == Integer.MIN_VALUE)
		{
			return false; // first sample — no comparison yet
		}

		// In combat, movement interruptions are intentional — never count as stuck.
		if (player.isAttackingNow() || TargetService.isUnderAttack(bot))
		{
			bot.resetStuckCount();
			return false;
		}

		final double moved = Math.hypot(px - lx, py - ly);
		final double expected = player.getMoveSpeed() * elapsed * 0.3;

		if (moved >= expected)
		{
			bot.resetStuckCount();
			return false;
		}

		bot.incrementStuckCount();
		final int count = bot.getStuckCount();
		LOGGER.info("PathService: " + player.getName() + " stuck " + count + "/" + STUCK_TELEPORT_THRESHOLD + " (moved " + (int) moved + " < min " + (int) expected + ")");

		// ── Last resort: signal path failure — let caller decide (city → skip, farm → wander).
		// Teleport is intentionally removed; fallback is now the caller's responsibility.
		if (count >= STUCK_TELEPORT_THRESHOLD)
		{
			bot.clearGlobalPath();
			bot.setLastPathTime(0);
			bot.setLastDirectCheckTime(0);
			bot.setStuckCheckTime(0);
			bot.setStuckSnapshot(Integer.MIN_VALUE, Integer.MIN_VALUE);
			bot.resetStuckCount();
			LOGGER.info("PathService: " + player.getName() + " stuck x" + STUCK_TELEPORT_THRESHOLD + " — signalling pathFailed");
			bot.setPathFailed(true);
			return true;
		}

		// ── Force repath: clear everything, let PathFinding rebuild from scratch ──
		if (count >= STUCK_REPATH_THRESHOLD)
		{
			bot.clearGlobalPath();
			bot.clearPath();
			bot.setLastPathTime(0);
			bot.setLastDirectCheckTime(0);
			player.getAI().setIntention(Intention.IDLE);
			LOGGER.info("PathService: " + player.getName() + " stuck " + count + " — force repath");
			return false;
		}

		// ── Escape maneuvers: sidestep at 75° from forward direction ─────────
		if (count >= STUCK_ESCAPE_THRESHOLD)
		{
			final double ddx = tx - px;
			final double ddy = ty - py;
			final double dist = Math.hypot(ddx, ddy);

			if (dist > 0)
			{
				// Unit vector toward destination.
				final double ndx = ddx / dist;
				final double ndy = ddy / dist;

				int ex;
				int ey;
				String dir;

				switch (count - STUCK_ESCAPE_THRESHOLD)
				{
					case 0: // LEFT: rotate forward +75°
					{
						ex = (int) (px + (ndx * ESC_COS - ndy * ESC_SIN) * ESCAPE_DIST);
						ey = (int) (py + (ndx * ESC_SIN + ndy * ESC_COS) * ESCAPE_DIST);
						dir = "LEFT-75";
						break;
					}
					case 1: // RIGHT: rotate forward -75°
					{
						ex = (int) (px + (ndx * ESC_COS + ndy * ESC_SIN) * ESCAPE_DIST);
						ey = (int) (py + (-ndx * ESC_SIN + ndy * ESC_COS) * ESCAPE_DIST);
						dir = "RIGHT-75";
						break;
					}
					default: // BACKWARD: 180°
					{
						ex = (int) (px - ndx * ESCAPE_DIST);
						ey = (int) (py - ndy * ESCAPE_DIST);
						dir = "BACK";
						break;
					}
				}

				bot.clearGlobalPath();
				bot.clearPath();
				bot.setLastPathTime(0);
				player.getAI().setIntention(Intention.MOVE_TO, new Location(ex, ey, pz));
				LOGGER.info("PathService: " + player.getName() + " stuck " + count + " — escape " + dir + " to " + ex + "," + ey);
				return false;
			}
		}

		// ── Default retry (counts 1-2): stop and let PathFinding retry ───────
		player.getAI().setIntention(Intention.IDLE);
		bot.clearPath();
		bot.setLastPathTime(0);
		return false;
	}

	// -------------------------------------------------------------------------
	// PathFinding with rate limit
	// -------------------------------------------------------------------------

	// Attempts PathFinding.findPath() subject to the global rate limit.
	// Sets the local path on success. Returns true if a path was found and set.
	private static boolean tryPathFinding(BotInstance bot, Player player, int px, int py, int pz, int tx, int ty, int tz, long now)
	{
		// Reset the rate-limit window if it has expired.
		if ((now - _pathRateWindowStart) > PATH_RATE_WINDOW_MS)
		{
			_pathCallsThisWindow.set(0);
			_pathRateWindowStart = now;
		}

		if (_pathCallsThisWindow.getAndIncrement() >= MAX_PATH_CALLS_PER_WINDOW)
		{
			return false; // throttled — retry next tick
		}

		bot.setLastPathTime(now);
		List<GeoLocation> path;
		try
		{
			path = PathFinding.getInstance().findPath(px, py, pz, tx, ty, tz, player.getInstanceId(), true);
		}
		catch (Exception e)
		{
			// Coordinates may be outside geodata bounds (ArrayIndexOutOfBoundsException, etc.)
			LOGGER.fine("PathService: " + player.getName() + " PathFinding threw " + e.getClass().getSimpleName() + " → fallback MOVE_TO");
			return false;
		}
		if ((path != null) && !path.isEmpty())
		{
			bot.setPath(path);
			LOGGER.info("PathService: " + player.getName() + " path found (" + path.size() + " pts) → " + tx + "," + ty);
			return true;
		}
		LOGGER.info("PathService: " + player.getName() + " PathFinding failed → " + tx + "," + ty + " (fallback MOVE_TO)");
		return false;
	}

	// -------------------------------------------------------------------------
	// Global path builder
	// -------------------------------------------------------------------------

	/**
	 * Builds straight-line segment endpoints every {@value #SEGMENT_STEP} units.
	 * The last point is exactly the destination.
	 *
	 * @param px current X
	 * @param py current Y
	 * @param pz current Z
	 * @param tx destination X
	 * @param ty destination Y
	 * @param tz destination Z
	 * @return ordered list of segment endpoints from current position to destination
	 */
	private static List<Location> buildSegments(int px, int py, int pz, int tx, int ty, int tz)
	{
		final double dx = tx - px;
		final double dy = ty - py;
		final double dz = tz - pz;
		final double totalDist = Math.sqrt((dx * dx) + (dy * dy));
		final int steps = (int) Math.ceil(totalDist / SEGMENT_STEP);
		final List<Location> path = new ArrayList<>(steps);
		for (int i = 1; i <= steps; i++)
		{
			final double t = (double) i / steps;
			path.add(new Location((int) (px + (dx * t)), (int) (py + (dy * t)), (int) (pz + (dz * t))));
		}
		return path;
	}
}
