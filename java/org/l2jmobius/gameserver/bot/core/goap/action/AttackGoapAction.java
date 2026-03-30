/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.goap.action;

import org.l2jmobius.gameserver.bot.core.action.AttackAction;
import org.l2jmobius.gameserver.bot.core.goap.Fact;
import org.l2jmobius.gameserver.bot.core.goap.GoapAction;
import org.l2jmobius.gameserver.bot.core.goap.WorldState;
import org.l2jmobius.gameserver.bot.core.model.BotContext;
import org.l2jmobius.gameserver.bot.core.model.BotInstance;

/**
 * pre:  TARGET_EXISTS, TARGET_IN_RANGE
 * eff:  TARGET_DEAD
 * cost: 1.0
 */
public class AttackGoapAction implements GoapAction
{
	private static final WorldState PRECONDITIONS = new WorldState();
	private static final WorldState EFFECTS = new WorldState();

	static
	{
		PRECONDITIONS.set(Fact.TARGET_EXISTS, true);
		PRECONDITIONS.set(Fact.TARGET_IN_RANGE, true);
		EFFECTS.set(Fact.TARGET_DEAD, true);
		EFFECTS.set(Fact.TARGET_EXISTS, false);
		EFFECTS.set(Fact.THREAT_NEUTRALIZED, true);
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
		return 1.0f;
	}

	@Override
	public boolean isValid(BotContext ctx, BotInstance bot)
	{
		// Range is a GOAP precondition (TARGET_IN_RANGE), not a validity filter —
		// the planner inserts MoveToTarget when needed. Only require a living target.
		return ctx.hasTarget();
	}

	@Override
	public void activate(BotInstance bot, long now)
	{
		bot.clearQueue();
		bot.queueAction(new AttackAction());
	}

	@Override
	public boolean isComplete(BotInstance bot, BotContext ctx, long now)
	{
		return !ctx.hasTarget() || bot.isQueueIdle();
	}

	@Override
	public String getName()
	{
		return "AttackGoapAction";
	}
}
