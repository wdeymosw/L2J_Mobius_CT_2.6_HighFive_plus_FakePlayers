/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.action;

import org.l2jmobius.gameserver.ai.Intention;
import org.l2jmobius.gameserver.bot.core.model.BotInstance;
import org.l2jmobius.gameserver.bot.core.service.CombatService;
import org.l2jmobius.gameserver.model.actor.Creature;

/**
 * Attacks the bot's current target until it dies or becomes unreachable.
 * <p>
 * Movement (approach) lives here — Action layer.
 * When out of range, re-issues {@code MOVE_TO} every tick so the bot tracks
 * a moving mob without PathService overhead (PathService is for long travel).
 * When in range, delegates the strike to {@link CombatService#perform}.
 * <p>
 * If the target stays out of attack range for longer than {@link #CHASE_TIMEOUT_MS},
 * the action clears the target and finishes — handles fled targets and unreachable mobs.
 */
public class AttackAction implements BotAction
{
	/** Maximum time (ms) to chase without reaching attack range. */
	private static final long CHASE_TIMEOUT_MS = 8000;

	/** Timestamp when out-of-range chasing began; 0 means currently in range. */
	private long _chaseStartTime = 0;

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
			if (_chaseStartTime == 0)
			{
				_chaseStartTime = now;
			}
			// Track the moving mob — re-issue each tick so the destination stays current.
			bot.getPlayer().getAI().setIntention(Intention.MOVE_TO, target.getLocation());
			return;
		}

		// In range — reset chase timer.
		_chaseStartTime = 0;

		// Stop movement so doAttack lands cleanly, then strike.
		if (bot.getPlayer().isMoving())
		{
			bot.getPlayer().getAI().setIntention(Intention.IDLE);
		}
		CombatService.perform(bot);
	}

	@Override
	public boolean isDone(BotInstance bot, long now)
	{
		final Creature target = bot.getTarget();
		if ((target == null) || target.isDead())
		{
			return true;
		}
		// Give up if the target has been out of attack range too long.
		if ((_chaseStartTime > 0) && ((now - _chaseStartTime) > CHASE_TIMEOUT_MS))
		{
			bot.clearTarget();
			return true;
		}
		return false;
	}
}
