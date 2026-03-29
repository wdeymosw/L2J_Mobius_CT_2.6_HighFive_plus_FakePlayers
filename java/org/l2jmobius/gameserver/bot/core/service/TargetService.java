/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.service;

import java.util.List;
import java.util.Set;

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
	 * Priority:
	 * <ol>
	 *   <li>Nearest mob currently attacking this bot (self-defence).</li>
	 *   <li>Nearest mob in search radius (proactive farming).</li>
	 *   <li>No target set — caller should wander.</li>
	 * </ol>
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
		final List<Attackable> candidates = World.getInstance().getVisibleObjectsInRange(player, Attackable.class, SEARCH_RADIUS, mob -> !mob.isDead());

		if (candidates.isEmpty())
		{
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

		// --- Priority 2: nearest mob in range ---
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
		bot.setTarget(nearest); // may be null if list was empty, already checked above
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
}
