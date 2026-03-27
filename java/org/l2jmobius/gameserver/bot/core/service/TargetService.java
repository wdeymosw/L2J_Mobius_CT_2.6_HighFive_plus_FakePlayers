/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.service;

import java.util.List;

import org.l2jmobius.gameserver.bot.core.model.BotInstance;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Attackable;
import org.l2jmobius.gameserver.model.actor.Creature;
import org.l2jmobius.gameserver.model.actor.Player;

/**
 * Selects a combat target for a bot.
 * <p>
 * Search is throttled: called only when nextSearchTime has elapsed.
 * Only searches for Attackable mobs that are alive, inside the bot's zone,
 * and preferably not already in combat (to spread aggro naturally).
 */
public class TargetService
{
	/** Search radius in game units (~500–1000 is typical aggro range). */
	private static final int SEARCH_RADIUS = 700;

	/** Minimum delay between searches (ms). */
	private static final long SEARCH_COOLDOWN_MIN = 2000;

	/** Maximum delay between searches (ms). */
	private static final long SEARCH_COOLDOWN_MAX = 4000;

	private TargetService()
	{
	}

	/**
	 * Attempts to find and assign a target to the bot.
	 * Sets nextSearchTime regardless of whether a target was found.
	 *
	 * @param bot the bot that needs a target
	 */
	public static void findTarget(BotInstance bot)
	{
		// Schedule next search regardless of outcome.
		final long cooldown = SEARCH_COOLDOWN_MIN + (long) (Math.random() * (SEARCH_COOLDOWN_MAX - SEARCH_COOLDOWN_MIN));
		bot.setNextSearchTime(System.currentTimeMillis() + cooldown);

		final Player player = bot.getPlayer();

		// Find all Attackable mobs within search radius.
		final List<Attackable> candidates = World.getInstance().getVisibleObjectsInRange(player, Attackable.class, SEARCH_RADIUS, mob -> isValidTarget(mob));

		if (candidates.isEmpty())
		{
			return;
		}

		// Pick the closest candidate.
		Creature nearest = null;
		double nearestDist = Double.MAX_VALUE;
		for (Attackable mob : candidates)
		{
			final double dist = player.calculateDistance2D(mob);
			if (dist < nearestDist)
			{
				nearestDist = dist;
				nearest = mob;
			}
		}

		bot.setTarget(nearest);
	}

	private static boolean isValidTarget(Attackable mob)
	{
		return !mob.isDead();
	}
}
