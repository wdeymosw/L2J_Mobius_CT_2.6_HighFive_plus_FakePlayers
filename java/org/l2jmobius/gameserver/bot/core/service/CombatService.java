/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.service;

import org.l2jmobius.gameserver.ai.Intention;
import org.l2jmobius.gameserver.bot.core.model.BotInstance;
import org.l2jmobius.gameserver.bot.core.model.BotRole;
import org.l2jmobius.gameserver.model.Location;
import org.l2jmobius.gameserver.model.actor.Creature;
import org.l2jmobius.gameserver.model.actor.Player;

/**
 * Drives bot combat and movement.
 * <p>
 * Null-client bots bypass the normal AI event loop, so we cannot rely on
 * {@code setIntention(ATTACK)} + {@code notifyAction(THINK)} — those calls
 * do not reach {@code thinkAttack()} for playerless connections.
 * <p>
 * Instead we manage range and attack directly: move toward the target when out
 * of reach, activate soulshots, and call {@code doAttack} when in range.
 */
public class CombatService
{
	private CombatService()
	{
	}

	/**
	 * Orders the bot to attack its current target each tick.
	 * Moves toward the target if out of physical attack range,
	 * otherwise fires soulshots and calls doAttack directly.
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
		if (!player.isAttackingNow())
		{
			final boolean magic = (bot.getRole() == BotRole.MAGE) || (bot.getRole() == BotRole.HEALER) || (bot.getRole() == BotRole.BUFFER) || (bot.getRole() == BotRole.SUMMONER);
			player.rechargeShots(!magic, magic);
			player.doAttack(target);
		}
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
