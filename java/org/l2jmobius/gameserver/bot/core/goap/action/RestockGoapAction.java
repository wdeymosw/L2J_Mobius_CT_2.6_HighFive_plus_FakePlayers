/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.goap.action;

import org.l2jmobius.gameserver.bot.core.action.WaitAction;
import org.l2jmobius.gameserver.bot.core.goap.Fact;
import org.l2jmobius.gameserver.bot.core.goap.GoapAction;
import org.l2jmobius.gameserver.bot.core.goap.WorldState;
import org.l2jmobius.gameserver.bot.core.model.BotContext;
import org.l2jmobius.gameserver.bot.core.model.BotInstance;
import org.l2jmobius.gameserver.bot.core.service.SupplyService;

/**
 * pre:  IN_SAFE_PLACE
 * eff:  HAS_AMMO=true
 * cost: 2.0
 */
public class RestockGoapAction implements GoapAction
{
	private static final WorldState PRECONDITIONS = new WorldState();
	private static final WorldState EFFECTS = new WorldState();

	static
	{
		PRECONDITIONS.set(Fact.IN_SAFE_PLACE, true);
		EFFECTS.set(Fact.HAS_AMMO, true);
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
		return !ctx.isInFarmZone && (ctx.outOfAmmo || SupplyService.needsRestock(bot));
	}

	@Override
	public void activate(BotInstance bot, long now)
	{
		SupplyService.restock(bot);
		bot.clearQueue();
		bot.queueAction(new WaitAction(500));
	}

	@Override
	public boolean isComplete(BotInstance bot, BotContext ctx, long now)
	{
		return bot.isQueueIdle();
	}

	@Override
	public String getName()
	{
		return "RestockGoapAction";
	}
}
