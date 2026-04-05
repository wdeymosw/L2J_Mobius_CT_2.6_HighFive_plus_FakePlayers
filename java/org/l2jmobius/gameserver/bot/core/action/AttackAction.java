/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.action;

import org.l2jmobius.gameserver.bot.core.exception.FatalBotException;
import org.l2jmobius.gameserver.bot.core.goap.GoapTuning;
import org.l2jmobius.gameserver.bot.core.exception.RecoverableBotException;
import org.l2jmobius.gameserver.bot.core.exception.ValidationBotException;
import org.l2jmobius.gameserver.bot.core.model.BotInstance;
import org.l2jmobius.gameserver.bot.core.service.CombatService;
import org.l2jmobius.gameserver.bot.core.service.SkillService;
import org.l2jmobius.gameserver.model.Location;
import org.l2jmobius.gameserver.model.actor.Creature;
import org.l2jmobius.gameserver.model.actor.Player;

/**
 * Atomic strike action — calls {@link CombatService#attack} once per tick.
 * <p>
 * {@code CombatService.attack} calls {@code doAttack} which handles range
 * checking internally: if the target is out of range the server AI moves the
 * bot toward the target automatically.
 * <p>
 * Finishes when the target dies or {@link #ATTACK_TIMEOUT_MS} elapses
 * without the target dying (mob fled / unreachable).
 */
public class AttackAction implements BotAction
{
	/** Maximum time (ms) to pursue before giving up. */
	private static final long ATTACK_TIMEOUT_MS = GoapTuning.ACTION_ATTACK_TIMEOUT_MS;

	private long _startTime = 0;

	@Override
	public void execute(BotInstance bot, long now) throws FatalBotException, ValidationBotException, RecoverableBotException
	{
		final Creature target = bot.getTarget();
		if ((target == null) || target.isDead())
		{
			return;
		}

		if (_startTime == 0)
		{
			_startTime = now;
		}

		final Player player = bot.getPlayer();
		if (player.calculateDistance3D(target) >= player.getPhysicalAttackRange())
		{
			// Mob may have moved since MoveToAction was queued — chase dynamically.
			CombatService.moveTo(bot, new Location(target.getX(), target.getY(), target.getZ()));
			return;
		}

		if (!SkillService.tryDamageSkill(bot))
		{
			CombatService.attack(bot);
		}
	}

	@Override
	public boolean isDone(BotInstance bot, long now)
	{
		final Creature target = bot.getTarget();
		if ((target == null) || target.isDead())
		{
			return true;
		}
		// Timeout: give up chasing but keep the target — onQueueCompleted will re-queue
		// a new AttackAction so the bot keeps fighting until the mob actually dies.
		if ((_startTime > 0) && ((now - _startTime) > ATTACK_TIMEOUT_MS))
		{
			return true;
		}
		return false;
	}
}
