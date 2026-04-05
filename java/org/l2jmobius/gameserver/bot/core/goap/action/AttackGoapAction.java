/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.goap.action;

import org.l2jmobius.gameserver.bot.core.action.AttackAction;
import org.l2jmobius.gameserver.bot.core.goap.AbstractGoapAction;
import org.l2jmobius.gameserver.bot.core.goap.Fact;
import org.l2jmobius.gameserver.bot.core.goap.GoapTuning;
import org.l2jmobius.gameserver.bot.core.goap.WorldState;
import org.l2jmobius.gameserver.bot.core.model.BotContext;
import org.l2jmobius.gameserver.bot.core.model.BotInstance;
import org.l2jmobius.gameserver.bot.core.service.TargetService;

/**
 * pre:  TARGET_EXISTS, TARGET_IN_RANGE
 * eff:  TARGET_DEAD
 * cost: 1.0
 */
public class AttackGoapAction extends AbstractGoapAction
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

	public AttackGoapAction()
	{
		super(PRECONDITIONS, EFFECTS);
	}

	@Override
	public float getCost(WorldState worldState)
	{
		return GoapTuning.COST_ATTACK;
	}

	@Override
	public boolean isValid(BotContext ctx, BotInstance bot)
	{
		// TARGET_EXISTS and TARGET_IN_RANGE are GOAP preconditions — planner handles them.
		// Autoattack is always available; no additional runtime check needed.
		return true;
	}

	@Override
	public void activate(BotInstance bot, long now)
	{
		if (TargetService.clearIfDead(bot))
		{
			return;
		}
		bot.replaceQueue(new AttackAction());
	}

	@Override
	public boolean isComplete(BotInstance bot, BotContext ctx, long now)
	{
		if (TargetService.clearIfDead(bot))
		{
			return true;
		}
		return !ctx.hasTarget();
	}

	@Override
	public String getName()
	{
		return "AttackGoapAction";
	}
}
