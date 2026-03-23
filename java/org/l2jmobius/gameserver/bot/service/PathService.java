/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.service;

import java.util.List;
import java.util.logging.Logger;

import org.l2jmobius.gameserver.bot.model.BotInstance;
import org.l2jmobius.gameserver.geoengine.pathfinding.GeoLocation;
import org.l2jmobius.gameserver.geoengine.pathfinding.PathFinding;
import org.l2jmobius.gameserver.model.Location;
import org.l2jmobius.gameserver.model.actor.Player;

/**
 * Path-finding navigation for bots.
 * <p>
 * Uses the server's A* {@link PathFinding} to compute multi-waypoint routes.
 * Bots follow the path waypoint by waypoint each ThinkService tick.
 * Falls back to a direct MOVE_TO if no path is found.
 */
public class PathService
{
	private static final Logger LOGGER = Logger.getLogger(PathService.class.getName());

	/** Distance (units) considered "arrived" at a waypoint. */
	private static final int WAYPOINT_ARRIVAL_RADIUS = 150;

	private PathService()
	{
	}

	// -------------------------------------------------------------------------
	// Public API
	// -------------------------------------------------------------------------

	/**
	 * Starts navigating the bot to the given world coordinates.
	 * Computes an A* path and stores it in the bot; the first waypoint
	 * movement is issued immediately.
	 *
	 * @param bot the bot that should navigate
	 * @param x   destination X
	 * @param y   destination Y
	 * @param z   destination Z
	 * @return {@code true} if a path was found, {@code false} if falling back to direct movement
	 */
	public static boolean navigateTo(BotInstance bot, int x, int y, int z)
	{
		final Player player = bot.getPlayer();
		final List<GeoLocation> path = PathFinding.getInstance().findPath(player.getX(), player.getY(), player.getZ(), x, y, z, player.getInstanceId(), true);

		if ((path == null) || path.isEmpty())
		{
			// No A* path — fall back to direct movement intention.
			bot.clearPath();
			CombatService.moveTo(bot, new Location(x, y, z));
			LOGGER.fine("PathService: no path for " + player.getName() + " → direct MOVE_TO");
			return false;
		}

		bot.setPath(path);
		issueNextWaypoint(bot);
		LOGGER.fine("PathService: " + player.getName() + " path " + path.size() + " nodes → " + x + "," + y + "," + z);
		return true;
	}

	/**
	 * Advances waypoint following each ThinkService tick.
	 * Call this every tick while the bot is in RETURNING state.
	 *
	 * @param bot the bot following a path
	 * @return {@code true} if the path is complete (no more waypoints), {@code false} otherwise
	 */
	public static boolean tickPath(BotInstance bot)
	{
		if (!bot.hasPath())
		{
			return true;
		}

		final GeoLocation wp = bot.getCurrentWaypoint();
		if (wp == null)
		{
			bot.clearPath();
			return true;
		}

		// Check if bot has arrived at the current waypoint.
		final Player player = bot.getPlayer();
		final int dx = player.getX() - wp.getX();
		final int dy = player.getY() - wp.getY();
		final boolean arrived = ((dx * dx) + (dy * dy)) < (WAYPOINT_ARRIVAL_RADIUS * WAYPOINT_ARRIVAL_RADIUS);

		if (!arrived)
		{
			// Still moving — re-issue the movement in case the intention was reset.
			issueNextWaypoint(bot);
			return false;
		}

		// Arrived — advance to the next waypoint.
		if (bot.hasNextWaypoint())
		{
			bot.advanceWaypoint();
			issueNextWaypoint(bot);
			return false;
		}

		// All waypoints visited — path complete.
		bot.clearPath();
		return true;
	}

	// -------------------------------------------------------------------------
	// Internal
	// -------------------------------------------------------------------------

	private static void issueNextWaypoint(BotInstance bot)
	{
		final GeoLocation wp = bot.getCurrentWaypoint();
		if (wp != null)
		{
			CombatService.moveTo(bot, new Location(wp.getX(), wp.getY(), wp.getZ()));
		}
	}
}
