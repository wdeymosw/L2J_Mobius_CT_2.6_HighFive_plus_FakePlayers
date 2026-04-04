/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.service;

import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import org.l2jmobius.gameserver.bot.core.model.BotInstance;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Attackable;
import org.l2jmobius.gameserver.model.actor.Creature;
import org.l2jmobius.gameserver.model.actor.Player;

/**
 * Selects a combat target for a bot.
 * <p>
 * Search is throttled: called only when nextSearchTime has elapsed.
 * Only searches for Attackable mobs that are alive, inside the bot's zone,
 * reachable by line-of-sight and path, and preferably not already in combat.
 */
public class TargetService
{
	private static final Logger LOGGER = Logger.getLogger(TargetService.class.getName());
	/** Search radius in game units (~500–1000 is typical aggro range). */
	private static final int SEARCH_RADIUS = 700;

	/** Maximum Z-axis difference to consider a mob reachable (avoids targeting mobs on other floors). */
	private static final int MAX_Z_DIFF = 800;

	/** Minimum delay between searches (ms). */
	private static final long SEARCH_COOLDOWN_MIN = 2000;

	/** Maximum delay between searches (ms). */
	private static final long SEARCH_COOLDOWN_MAX = 4000;

	private TargetService()
	{
	}

	/**
	 * Attempts to find and assign a target to the bot.
	 * Priority:
	 * <ol>
	 *   <li>Nearest mob currently attacking this bot (self-defence), reachable by GeoEngine.</li>
	 *   <li>Nearest reachable mob in search radius not already engaged by another player.</li>
	 *   <li>No target set — caller should wander.</li>
	 * </ol>
	 * Reachability = canSeeTarget + canMoveToTarget + Z-difference ≤ {@value #MAX_Z_DIFF}.
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
		final GeoEngine geo = GeoEngine.getInstance();
		final List<Attackable> candidates = World.getInstance().getVisibleObjectsInRange(player, Attackable.class, SEARCH_RADIUS, mob -> !mob.isDead());

		if (candidates.isEmpty())
		{
			LOGGER.fine("[" + bot.getPlayer().getName() + "] findTarget: no candidates in radius " + SEARCH_RADIUS);
			return; // caller handles wander
		}

		// --- Priority 1: nearest mob currently attacking this bot ---
		final Set<Creature> attackers = player.getAttackByList();
		Creature nearestAttacker = null;
		double nearestAttackerDist = Double.MAX_VALUE;
		for (Attackable mob : candidates)
		{
			if (!attackers.contains(mob) && (mob.getTarget() != player))
			{
				continue;
			}
			if (!isReachable(player, mob, geo))
			{
				continue;
			}
			final double dist = player.calculateDistance2D(mob);
			if (dist < nearestAttackerDist)
			{
				nearestAttackerDist = dist;
				nearestAttacker = mob;
			}
		}
		if (nearestAttacker != null)
		{
			bot.setTarget(nearestAttacker);
			return;
		}

		// --- Priority 2: nearest reachable mob not already engaged ---
		Creature nearest = null;
		double nearestDist = Double.MAX_VALUE;
		int skippedGeo = 0;
		int skippedEngaged = 0;
		for (Attackable mob : candidates)
		{
			if (!isReachable(player, mob, geo))
			{
				skippedGeo++;
				continue;
			}
			// Skip mobs already being fought by someone else (respectful hunting).
			if ((mob.getTarget() != null) && (mob.getTarget() != player) && mob.getTarget().isPlayer())
			{
				skippedEngaged++;
				continue;
			}
			final double dist = player.calculateDistance2D(mob);
			if (dist < nearestDist)
			{
				nearestDist = dist;
				nearest = mob;
			}
		}

		// Fallback: if all mobs are engaged, pick the nearest reachable one regardless.
		if (nearest == null)
		{
			for (Attackable mob : candidates)
			{
				if (!isReachable(player, mob, geo))
				{
					continue;
				}
				final double dist = player.calculateDistance2D(mob);
				if (dist < nearestDist)
				{
					nearestDist = dist;
					nearest = mob;
				}
			}
		}

		if (nearest == null)
		{
			LOGGER.fine("[" + bot.getPlayer().getName() + "] findTarget: " + candidates.size() + " candidates, none reachable (skippedGeo=" + skippedGeo + " skippedEngaged=" + skippedEngaged + ")");
		}
		else
		{
			LOGGER.fine("[" + bot.getPlayer().getName() + "] findTarget: selected " + nearest.getName() + " dist=" + (int) nearestDist);
		}

		bot.setTarget(nearest);
	}

	/**
	 * Returns {@code true} if there is at least one mob within search range
	 * that is currently targeting this bot. Used to bypass search cooldown
	 * for immediate self-defence reaction.
	 *
	 * @param bot the bot to check
	 * @return true if the bot is under attack
	 */
	public static boolean isUnderAttack(BotInstance bot)
	{
		final Player player = bot.getPlayer();
		final Set<Creature> attackers = player.getAttackByList();
		if (!attackers.isEmpty())
		{
			return true;
		}
		return !World.getInstance().getVisibleObjectsInRange(player, Attackable.class, SEARCH_RADIUS, mob -> !mob.isDead() && (mob.getTarget() == player)).isEmpty();
	}

	/**
	 * Returns {@code true} if at least one current attacker is NOT the bot's active target.
	 * Used to interrupt movement and defend when a different mob aggros the bot.
	 *
	 * @param bot the bot to check
	 * @return true if any attacker is different from the current target
	 */
	public static boolean isAttackedByDifferentMob(BotInstance bot)
	{
		final Creature currentTarget = bot.getTarget();
		final Player player = bot.getPlayer();
		final Set<Creature> attackers = player.getAttackByList();
		for (Creature c : attackers)
		{
			if (!c.isDead() && (c != currentTarget))
			{
				return true;
			}
		}
		return !World.getInstance().getVisibleObjectsInRange(player, Attackable.class, SEARCH_RADIUS, mob -> !mob.isDead() && (mob.getTarget() == player) && (mob != currentTarget)).isEmpty();
	}

	/**
	 * Immediately switches the bot's target to the nearest mob currently attacking it,
	 * preferring mobs that are NOT already the current target.
	 * No-op if no living attacker is found.
	 * <p>
	 * Called when the bot is interrupted mid-movement by a different aggressor so that
	 * the subsequent replan produces a plan aimed at the actual threat.
	 *
	 * @param bot the bot whose target should be switched
	 */
	public static void switchToNearestAttacker(BotInstance bot)
	{
		final Player player = bot.getPlayer();
		final Creature currentTarget = bot.getTarget();
		final Set<Creature> attackers = player.getAttackByList();
		final List<Attackable> candidates = World.getInstance().getVisibleObjectsInRange(player, Attackable.class, SEARCH_RADIUS, mob -> !mob.isDead() && (attackers.contains(mob) || (mob.getTarget() == player)));

		// First pass: find nearest attacker that is NOT the current target.
		Creature nearest = null;
		double nearestDist = Double.MAX_VALUE;
		for (Attackable mob : candidates)
		{
			if (mob == currentTarget)
			{
				continue;
			}
			final double dist = player.calculateDistance2D(mob);
			if (dist < nearestDist)
			{
				nearestDist = dist;
				nearest = mob;
			}
		}

		// Fallback: if only the current target is in range, keep it (no switch needed).
		if ((nearest == null) && !candidates.isEmpty())
		{
			return;
		}

		if (nearest != null)
		{
			bot.setTarget(nearest);
		}
	}

	/**
	 * Returns the number of alive mobs within search range that are currently
	 * targeting this bot. Used by {@link org.l2jmobius.gameserver.bot.core.model.BotContext}
	 * to evaluate whether the bot can fight back.
	 *
	 * @param bot the bot to check
	 * @return number of attackers (0 = no one attacking)
	 */
	public static int countAttackers(BotInstance bot)
	{
		final Player player = bot.getPlayer();
		final Set<Creature> attackers = player.getAttackByList();
		return World.getInstance().getVisibleObjectsInRange(player, Attackable.class, SEARCH_RADIUS, mob -> !mob.isDead() && (attackers.contains(mob) || (mob.getTarget() == player))).size();
	}

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	/**
	 * Returns {@code true} if {@code mob} is reachable from {@code player}:
	 * Z-difference within {@value #MAX_Z_DIFF}, line-of-sight visible, and a
	 * path exists (canMoveToTarget).
	 */
	private static boolean isReachable(Player player, Attackable mob, GeoEngine geo)
	{
		if (Math.abs(player.getZ() - mob.getZ()) > MAX_Z_DIFF)
		{
			return false;
		}
		if (!geo.canSeeTarget(player, mob))
		{
			return false;
		}
		return geo.canMoveToTarget(player.getX(), player.getY(), player.getZ(), mob.getX(), mob.getY(), mob.getZ(), player.getInstanceId());
	}
}

