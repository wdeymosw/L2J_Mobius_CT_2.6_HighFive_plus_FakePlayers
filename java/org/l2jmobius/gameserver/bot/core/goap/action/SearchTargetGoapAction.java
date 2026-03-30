/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.goap.action;

import org.l2jmobius.gameserver.bot.core.action.MoveToAction;
import org.l2jmobius.gameserver.bot.core.action.WaitAction;
import org.l2jmobius.gameserver.bot.core.goap.Fact;
import org.l2jmobius.gameserver.bot.core.goap.GoapAction;
import org.l2jmobius.gameserver.bot.core.goap.WorldState;
import org.l2jmobius.gameserver.bot.core.model.BotContext;
import org.l2jmobius.gameserver.bot.core.model.BotInstance;
import org.l2jmobius.gameserver.bot.core.service.BuffService;
import org.l2jmobius.gameserver.bot.core.service.LootService;
import org.l2jmobius.gameserver.bot.core.service.TargetService;
import org.l2jmobius.gameserver.model.Location;

import java.util.concurrent.ThreadLocalRandom;

/**
 * pre:  IN_FARM_ZONE
 * eff:  TARGET_EXISTS
 * cost: 3.0
 * <p>
 * Tries buffs → loot → target search → wander if nothing found.
 */
public class SearchTargetGoapAction implements GoapAction
{
	private static final WorldState PRECONDITIONS = new WorldState();
	private static final WorldState EFFECTS = new WorldState();

	static
	{
		PRECONDITIONS.set(Fact.IN_FARM_ZONE, true);
		EFFECTS.set(Fact.TARGET_EXISTS, true);
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
		return 3.0f;
	}

	@Override
	public boolean isValid(BotContext ctx, BotInstance bot)
	{
		return ctx.isInFarmZone;
	}

	@Override
	public void activate(BotInstance bot, long now)
	{
		bot.clearQueue();

		// 1. Buff first if available
		if (BuffService.tryBuffSelf(bot))
		{
			bot.queueAction(new WaitAction(500));
			return;
		}

		// 2. Pick up nearby loot
		if (LootService.pickupNearest(bot))
		{
			return;
		}

		// 3. Search for a target
		TargetService.findTarget(bot);
		if (bot.hasTarget())
		{
			return; // GoapAgent will replan next tick: MoveToTarget + Attack
		}

		// 4. Nothing found — wander
		final Location wander = bot.getProfile().getZone().randomPointInside();
		bot.queueAction(new WaitAction(1000 + ThreadLocalRandom.current().nextInt(1000)));
		bot.queueAction(new MoveToAction(wander));
	}

	@Override
	public boolean isComplete(BotInstance bot, BotContext ctx, long now)
	{
		// Done if we found a target OR the wander queue finished
		return ctx.hasTarget() || bot.isQueueIdle();
	}

	@Override
	public String getName()
	{
		return "SearchTargetGoapAction";
	}
}
