/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.goap.action;

import org.l2jmobius.gameserver.bot.core.goap.AbstractGoapAction;
import org.l2jmobius.gameserver.bot.core.goap.Fact;
import org.l2jmobius.gameserver.bot.core.goap.GoapTuning;
import org.l2jmobius.gameserver.bot.core.goap.WorldState;
import org.l2jmobius.gameserver.bot.core.model.BotContext;
import org.l2jmobius.gameserver.bot.core.model.BotInstance;
import org.l2jmobius.gameserver.bot.core.service.TargetService;
import org.l2jmobius.gameserver.model.actor.Player;

/**
 * pre:  IN_COMBAT=false, IS_DEAD=false
 * eff:  HP_FULL=true, MP_FULL=true
 * cost: 10.0  (high — last-resort regen, prefer potions/skills first)
 * <p>
 * Sits the bot down until HP and MP are fully restored, then stands up.
 */
public class SitRestGoapAction extends AbstractGoapAction
{
	private static final double STAND_HP_THRESHOLD = GoapTuning.STAND_HP_THRESHOLD;
	private static final double STAND_MP_THRESHOLD = GoapTuning.STAND_MP_THRESHOLD;
	/** How long to wait after calling standUp() for the 2500ms animation to finish. */
	private static final long STAND_UP_ANIMATION_MS = GoapTuning.STAND_UP_ANIMATION_MS;

	private static final WorldState PRECONDITIONS = new WorldState();
	private static final WorldState EFFECTS = new WorldState();

	/** Timestamp when standUp() was called — we wait for animation before returning isComplete=true. */
	private long _standUpStartTime = 0;

	public SitRestGoapAction()
	{
		super(PRECONDITIONS, EFFECTS);
	}

	static
	{
		PRECONDITIONS.set(Fact.IN_COMBAT, false);
		PRECONDITIONS.set(Fact.IS_DEAD, false);
		// Effects match RestoreGoal.DESIRED — HP and MP no longer "low"
		EFFECTS.set(Fact.HP_LOW, false);
		EFFECTS.set(Fact.MP_LOW, false);
	}

	@Override
	public float getCost(WorldState worldState)
	{
		return GoapTuning.COST_SIT_REST;
	}

	@Override
	public boolean isValid(BotContext ctx, BotInstance bot)
	{
		// Never sit while in combat — live query beats stale ctx snapshot.
		if (ctx.hasTarget() || (ctx.attackerCount > 0) || (TargetService.countAttackers(bot) > 0))
		{
			return false;
		}
		// HP-drop guard: if we took damage this tick, a mob may be attacking but not yet in lists.
		if (bot.hasTakenDamageSinceLastTick())
		{
			return false;
		}
		// 3-second grace window after last combat — covers slow-registering aggressors.
		final long now = System.currentTimeMillis();
		if ((now - bot.getLastCombatTime()) < GoapTuning.POST_COMBAT_REST_GRACE_MS)
		{
			return false;
		}
		return !bot.getPlayer().isDead();
	}

	@Override
	public void activate(BotInstance bot, long now)
	{
		bot.clearQueue();
		final Player player = bot.getPlayer();
		if (!player.isSitting())
		{
			player.sitDown();
		}
	}

	@Override
	public boolean isComplete(BotInstance bot, BotContext ctx, long now)
	{
		// If standUp() was triggered, wait for the 2500ms sit→stand animation before completing.
		if (_standUpStartTime > 0)
		{
			if ((now - _standUpStartTime) >= STAND_UP_ANIMATION_MS)
			{
				_standUpStartTime = 0;
				return true;
			}
			return false;
		}

		// Live combat check — do NOT rely on stale ctx snapshot.
		// countAttackers() is a fresh World query; hasTakenDamageSinceLastTick() catches
		// mobs that aggroed but haven't registered in attacker lists yet.
		final boolean underAttack = (ctx.attackerCount > 0) || (TargetService.countAttackers(bot) > 0) || bot.hasTakenDamageSinceLastTick();
		if (ctx.hasTarget() || underAttack)
		{
			// Mark combat time so the 3-second grace window in isValid() blocks
			// SitRest from being replanned immediately after standing up.
			bot.updateLastCombatTime();
			standUp(bot, now);
			return false; // wait for animation
		}
		// HP and MP recovered enough — stand up and resume hunting.
		// Uses hysteresis thresholds above the goal-trigger values to avoid re-sit immediately.
		if ((ctx.hpPercent >= STAND_HP_THRESHOLD) && (ctx.mpPercent >= STAND_MP_THRESHOLD))
		{
			standUp(bot, now);
			return false; // wait for animation
		}
		// sitDown() is asynchronous — retry each tick until the server confirms the sitting state.
		if (!bot.getPlayer().isSitting())
		{
			bot.getPlayer().sitDown();
		}
		return false;
	}

	/**
	 * No timeout — the bot must sit until HP/MP actually reaches 90%
	 * or combat interrupts. A 30 s timeout would abort recovery prematurely,
	 * causing a stand-up / sit-down loop on slow natural regen.
	 */
	@Override
	public long getActionTimeoutMs()
	{
		return 0L;
	}

	private void standUp(BotInstance bot, long now)
	{
		if (_standUpStartTime > 0)
		{
			return; // already standing up
		}
		final Player player = bot.getPlayer();
		if (player.isSitting())
		{
			// Use the native standUp() which:
			// 1. Broadcasts ChangeWaitType(WT_STANDING) to the client
			// 2. Schedules StandUpTask (2500ms) → setParalyzed(false) + setSitting(false)
			player.standUp();
		}
		else
		{
			// Not sitting — ensure paralysis is cleared in case SitDownTask hasn't run yet.
			player.setParalyzed(false);
		}
		player.setRunning();
		_standUpStartTime = now;
	}

	@Override
	public String getName()
	{
		return "SitRestGoapAction";
	}

	/**
	 * If aborted while sitting (timeout, interrupt, plan clear), force stand-up
	 * so the bot can move on the next tick.
	 */
	@Override
	public void onAbort(BotInstance bot)
	{
		final long now = System.currentTimeMillis();
		standUp(bot, now);
	}
}
