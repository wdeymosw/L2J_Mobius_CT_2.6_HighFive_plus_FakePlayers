/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.goap.action;

import org.l2jmobius.gameserver.bot.core.action.MoveToAction;
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
		return ctx.hasTarget();
	}

	@Override
	public void activate(BotInstance bot, long now)
	{
		final Creature target = bot.getTarget();
		if (target == null)
		{
			return;
		}
		final Player player = bot.getPlayer();
		final int range = player.getPhysicalAttackRange();
		bot.clearQueue();
		bot.queueAction(new MoveToAction(target.getX(), target.getY(), target.getZ(), range));
	}

	@Override
	public boolean isComplete(BotInstance bot, BotContext ctx, long now)
	{
		return ctx.canAttackTarget || !ctx.hasTarget();
	}

	@Override
	public String getName()
	{
		return "MoveToTargetGoapAction";
	}
}
