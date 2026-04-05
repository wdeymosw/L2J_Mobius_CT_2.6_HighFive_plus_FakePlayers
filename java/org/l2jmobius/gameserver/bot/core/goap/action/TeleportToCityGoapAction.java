/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.goap.action;

import org.l2jmobius.gameserver.bot.core.action.TeleportAction;
import org.l2jmobius.gameserver.bot.core.action.WaitAction;
import org.l2jmobius.gameserver.bot.core.goap.AbstractGoapAction;
import org.l2jmobius.gameserver.bot.core.goap.Fact;
import org.l2jmobius.gameserver.bot.core.goap.GoapTuning;
import org.l2jmobius.gameserver.bot.core.goap.WorldState;
import org.l2jmobius.gameserver.bot.core.model.BotContext;
import org.l2jmobius.gameserver.bot.core.model.BotInstance;
import org.l2jmobius.gameserver.bot.core.service.ShopService;
import org.l2jmobius.gameserver.model.Location;

/**
 * pre:  (none — always possible)
 * eff:  IN_SAFE_PLACE=true, IN_FARM_ZONE=false
 * cost: 5.0
 */
public class TeleportToCityGoapAction extends AbstractGoapAction
{
	private static final long SETTLE_MS = GoapTuning.TELEPORT_SETTLE_MS;

	private static final WorldState PRECONDITIONS = new WorldState();
	private static final WorldState EFFECTS = new WorldState();

	static
	{
		EFFECTS.set(Fact.IN_SAFE_PLACE, true);
		EFFECTS.set(Fact.IN_FARM_ZONE, false);
		EFFECTS.set(Fact.IN_COMBAT, false); // teleporting to city ends combat for planning purposes
	}

	public TeleportToCityGoapAction()
	{
		super(PRECONDITIONS, EFFECTS);
	}

	@Override
	public float getCost(WorldState worldState)
	{
		return GoapTuning.COST_TELEPORT_CITY;
	}

	@Override
	public boolean isValid(BotContext ctx, BotInstance bot)
	{
		return true; // always possible as an escape option
	}

	@Override
	public void activate(BotInstance bot, long now)
	{
		ShopService.closeShop(bot);
		bot.clearQueue();
		bot.clearTarget();
		bot.clearGlobalPath();
		final Location home = bot.getProfile().getZone().getHomeLocation();
		bot.queueAction(new TeleportAction(home.getX(), home.getY(), home.getZ()));
		bot.queueAction(new WaitAction(SETTLE_MS));
	}

	@Override
	public boolean isComplete(BotInstance bot, BotContext ctx, long now)
	{
		return !ctx.isInFarmZone && bot.isQueueIdle();
	}

	@Override
	public String getName()
	{
		return "TeleportToCityGoapAction";
	}
}
