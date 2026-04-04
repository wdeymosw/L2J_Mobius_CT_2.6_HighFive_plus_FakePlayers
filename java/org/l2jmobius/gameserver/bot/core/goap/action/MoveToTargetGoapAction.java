/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.goap.action;

import org.l2jmobius.gameserver.bot.core.action.MoveToCreatureAction;
import org.l2jmobius.gameserver.bot.core.goap.Fact;
import org.l2jmobius.gameserver.bot.core.goap.GoapAction;
import org.l2jmobius.gameserver.bot.core.goap.WorldState;
import org.l2jmobius.gameserver.bot.core.model.BotContext;
import org.l2jmobius.gameserver.bot.core.model.BotInstance;
import org.l2jmobius.gameserver.model.actor.Creature;
import org.l2jmobius.gameserver.model.actor.Player;
/**
 * pre:  TARGET_EXISTS
 * eff:  TARGET_IN_RANGE
 * cost: 2.0
 */
public class MoveToTargetGoapAction implements GoapAction
{
	/** Shorter timeout: if we can't reach the mob in 8s, it's likely unreachable. */
	private static final long TIMEOUT_MS = 8_000L;
	private static final WorldState PRECONDITIONS = new WorldState();
	private static final WorldState EFFECTS = new WorldState();

	static
	{
		PRECONDITIONS.set(Fact.TARGET_EXISTS, true);
		EFFECTS.set(Fact.TARGET_IN_RANGE, true);
	}

	@Override
	public WorldState getPreconditions()
	{
		return PRECONDITIONS;
	}

	@Override
	public WorldState getEffects()
	{
		return EFFECTS;
	}

	@Override
	public float getCost(WorldState worldState)
	{
		return 2.0f;
	}

	@Override
	public boolean isValid(BotContext ctx, BotInstance bot)
	{
		// TARGET_EXISTS is already a GOAP precondition — planner handles "no target" by
		// inserting SearchTargetGoapAction before this action. Always return true so the
		// planner can include this action in chains even when no target exists yet.
		return true;
	}

	@Override
	public void activate(BotInstance bot, long now)
	{
		final Creature target = bot.getTarget();
		// Target gone or already dead — clear it so the planner re-inserts SearchTargetGoapAction.
		if ((target == null) || target.isDead())
		{
			bot.clearTarget();
			return;
		}
		final Player player = bot.getPlayer();
		final int range = player.getPhysicalAttackRange();
		bot.clearQueue();
		bot.queueAction(new MoveToCreatureAction(target, range));
	}

	@Override
	public boolean isComplete(BotInstance bot, BotContext ctx, long now)
	{
		// Complete if target is dead (not yet despawned) — treat same as no target.
		final Creature target = bot.getTarget();
		if ((target != null) && target.isDead())
		{
			bot.clearTarget();
			return true;
		}
		return ctx.canAttackTarget || !ctx.hasTarget();
	}

	@Override
	public String getName()
	{
		return "MoveToTargetGoapAction";
	}

	@Override
	public long getActionTimeoutMs()
	{
		return TIMEOUT_MS;
	}
}
