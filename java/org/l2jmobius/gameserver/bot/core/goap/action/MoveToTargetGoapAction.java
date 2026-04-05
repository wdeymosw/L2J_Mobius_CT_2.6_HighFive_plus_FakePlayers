/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.goap.action;

import org.l2jmobius.gameserver.bot.core.action.MoveToCreatureAction;
import org.l2jmobius.gameserver.bot.core.goap.AbstractGoapAction;
import org.l2jmobius.gameserver.bot.core.goap.Fact;
import org.l2jmobius.gameserver.bot.core.goap.GoapTuning;
import org.l2jmobius.gameserver.bot.core.goap.WorldState;
import org.l2jmobius.gameserver.bot.core.model.BotContext;
import org.l2jmobius.gameserver.bot.core.model.BotInstance;
import org.l2jmobius.gameserver.bot.core.service.TargetService;
import org.l2jmobius.gameserver.model.actor.Player;

/**
 * pre:  TARGET_EXISTS
 * eff:  TARGET_IN_RANGE
 * cost: 2.0
 */
public class MoveToTargetGoapAction extends AbstractGoapAction
{
	private static final WorldState PRECONDITIONS = new WorldState();
	private static final WorldState EFFECTS = new WorldState();

	static
	{
		PRECONDITIONS.set(Fact.TARGET_EXISTS, true);
		EFFECTS.set(Fact.TARGET_IN_RANGE, true);
	}

	public MoveToTargetGoapAction()
	{
		super(PRECONDITIONS, EFFECTS);
	}

	@Override
	public float getCost(WorldState worldState)
	{
		return GoapTuning.COST_MOVE_TO_TARGET;
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
		// Target gone or already dead — clear it so the planner re-inserts SearchTargetGoapAction.
		if (TargetService.clearIfDead(bot) || !TargetService.isTargetAlive(bot))
		{
			return;
		}
		final Player player = bot.getPlayer();
		final int range = player.getPhysicalAttackRange();
		bot.replaceQueue(new MoveToCreatureAction(bot.getTarget(), range));
	}

	@Override
	public boolean isComplete(BotInstance bot, BotContext ctx, long now)
	{
		// Complete if target is dead (not yet despawned) — treat same as no target.
		if (TargetService.clearIfDead(bot))
		{
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
		return GoapTuning.ACTION_MOVE_TO_TARGET_TIMEOUT_MS;
	}
}
