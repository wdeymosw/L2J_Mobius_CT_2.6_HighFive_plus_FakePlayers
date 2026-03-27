/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.action;

import org.l2jmobius.gameserver.bot.core.model.BotInstance;
import org.l2jmobius.gameserver.bot.core.service.CombatService;
import org.l2jmobius.gameserver.bot.core.service.SkillService;
import org.l2jmobius.gameserver.model.actor.Creature;

/**
 * Attacks the bot's current target each tick until it dies.
 * Prefers a damage skill; falls back to auto-attack via CombatService.
 */
public class AttackAction implements BotAction
{
	@Override
	public void execute(BotInstance bot, long now)
	{
		if (!SkillService.tryDamageSkill(bot))
		{
			CombatService.attack(bot);
		}
	}

	@Override
	public boolean isDone(BotInstance bot, long now)
	{
		final Creature target = bot.getTarget();
		return (target == null) || target.isDead();
	}
}
