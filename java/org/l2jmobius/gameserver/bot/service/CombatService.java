/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.service;

import org.l2jmobius.gameserver.ai.Action;
import org.l2jmobius.gameserver.ai.Intention;
import org.l2jmobius.gameserver.bot.model.BotInstance;
import org.l2jmobius.gameserver.model.Location;
import org.l2jmobius.gameserver.model.actor.Creature;
import org.l2jmobius.gameserver.model.actor.Player;

/**
 * Drives bot combat and movement via the game's PlayerAI.
 * <p>
 * Problem: when {@code setIntention(ATTACK, sameTarget)} is called and the AI
 * already has ATTACK with that target, {@code CreatureAI.onIntentionAttack()}
 * just calls {@code clientActionFailed()} and exits — it does NOT call think.
 * <p>
 * Fix: after setting the intention, always call {@code notifyAction(THINK)} to
 * force {@code PlayerAI.thinkAttack()} to run. {@code thinkAttack()} handles
 * both movement (maybeMoveToPawn) and the actual attack (doAttack) internally,
 * so we do not need to manage range or movement manually here.
 */
public class CombatService
{
	private CombatService()
	{
	}

	/**
	 * Orders the bot to attack its current target each tick.
	 * Sets ATTACK intention on first call; subsequently forces a THINK action
	 * to keep the PlayerAI cycling through move → attack → move → attack.
	 *
	 * @param bot the bot that should attack
	 */
	public static void attack(BotInstance bot)
	{
		final Creature target = bot.getTarget();
		if ((target == null) || target.isDead())
		{
			bot.clearTarget();
			return;
		}

		final Player player = bot.getPlayer();
		player.setTarget(target);
		player.getAI().setIntention(Intention.ATTACK, target);

		// Force PlayerAI to think now — without this, repeated calls with the
		// same target are silently ignored by CreatureAI.onIntentionAttack().
		player.getAI().notifyAction(Action.THINK);
	}

	/**
	 * Orders the bot to move towards a location (used for zone returns and revives).
	 *
	 * @param bot      the bot that should move
	 * @param location destination
	 */
	public static void moveTo(BotInstance bot, Location location)
	{
		bot.getPlayer().getAI().setIntention(Intention.MOVE_TO, location);
	}
}
