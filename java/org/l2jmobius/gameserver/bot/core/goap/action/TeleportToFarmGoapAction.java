/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.goap.action;

import org.l2jmobius.gameserver.bot.core.action.TeleportAction;
import org.l2jmobius.gameserver.bot.core.action.WaitAction;
import org.l2jmobius.gameserver.bot.core.goap.Fact;
import org.l2jmobius.gameserver.bot.core.goap.GoapAction;
import org.l2jmobius.gameserver.bot.core.goap.WorldState;
import org.l2jmobius.gameserver.bot.core.model.BotContext;
import org.l2jmobius.gameserver.bot.core.model.BotInstance;
import org.l2jmobius.gameserver.bot.core.service.ShopService;
import org.l2jmobius.gameserver.model.Location;

/**
 * pre:  (none — always possible)
 * eff:  IN_FARM_ZONE=true, IN_SAFE_PLACE=false
 * cost: 5.0
 */
public class TeleportToFarmGoapAction implements GoapAction
{
	private static final long SETTLE_MS = 3_000;

	private static final WorldState PRECONDITIONS = new WorldState();
	private static final WorldState EFFECTS = new WorldState();

	static
	{
		EFFECTS.set(Fact.IN_FARM_ZONE, true);
		EFFECTS.set(Fact.IN_SAFE_PLACE, false);
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
		return 5.0f;
	}

	@Override
	public boolean isValid(BotContext ctx, BotInstance bot)
	{
		return !ctx.isInFarmZone;
	}

	@Override
	public void activate(BotInstance bot, long now)
	{
		ShopService.closeShop(bot);
		bot.clearQueue();
		bot.clearTarget();
		bot.clearGlobalPath();
		final Location farm = bot.getProfile().getZone().getCenter();
		bot.queueAction(new TeleportAction(farm.getX(), farm.getY(), farm.getZ()));
		bot.queueAction(new WaitAction(SETTLE_MS));
	}

	@Override
	public boolean isComplete(BotInstance bot, BotContext ctx, long now)
	{
		return ctx.isInFarmZone && bot.isQueueIdle();
	}

	@Override
	public String getName()
	{
		return "TeleportToFarmGoapAction";
	}
}
