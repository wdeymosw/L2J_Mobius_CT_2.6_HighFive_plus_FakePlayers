/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.action;

import org.l2jmobius.gameserver.bot.core.model.BotInstance;
import org.l2jmobius.gameserver.bot.core.service.CombatService;
import org.l2jmobius.gameserver.bot.core.service.PathService;
import org.l2jmobius.gameserver.model.actor.Creature;

/**
 * Attacks the bot's current target until it dies.
 * <p>
 * Movement (approach) lives here — Action layer.
 * Actual strike is delegated to {@link CombatService#perform} — Service layer.
 */
public class AttackAction implements BotAction
{
	@Override
	public void execute(BotInstance bot, long now)
	{
		final Creature target = bot.getTarget();
		if ((target == null) || target.isDead())
		{
			return;
		}

		final int range = bot.getPlayer().getPhysicalAttackRange() + 40;
		if (!bot.getPlayer().isInsideRadius2D(target, range))
		{
			// Out of range — approach via PathService (movement stays in Action layer).
			PathService.thinkMove(bot, target.getX(), target.getY(), target.getZ());
			return;
		}

		CombatService.perform(bot);
	}

	@Override
	public boolean isDone(BotInstance bot, long now)
	{
		final Creature target = bot.getTarget();
		return (target == null) || target.isDead();
	}
}
