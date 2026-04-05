/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.goap.action;

import org.l2jmobius.gameserver.bot.core.action.WaitAction;
import org.l2jmobius.gameserver.bot.core.goap.Fact;
import org.l2jmobius.gameserver.bot.core.goap.GoapAction;
import org.l2jmobius.gameserver.bot.core.goap.GoapTuning;
import org.l2jmobius.gameserver.bot.core.goap.WorldState;
import org.l2jmobius.gameserver.bot.core.model.BotContext;
import org.l2jmobius.gameserver.bot.core.model.BotInstance;
import org.l2jmobius.gameserver.bot.core.service.EquipService;
import org.l2jmobius.gameserver.bot.core.service.LevelUpService;
import org.l2jmobius.gameserver.bot.core.service.SellService;
import org.l2jmobius.gameserver.bot.core.service.ShopService;

/**
 * pre:  IN_SAFE_PLACE
 * eff:  INVENTORY_OK
 * cost: 2.0
 * <p>
 * Runs the full city pipeline: sell → equip → level-up → open private shop.
 */
public class SellItemsGoapAction implements GoapAction
{
	private static final WorldState PRECONDITIONS = new WorldState();
	private static final WorldState EFFECTS = new WorldState();

	static
	{
		PRECONDITIONS.set(Fact.IN_SAFE_PLACE, true);
		EFFECTS.set(Fact.INVENTORY_OK, true);
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
		return GoapTuning.COST_SELL_ITEMS;
	}

	@Override
	public boolean isValid(BotContext ctx, BotInstance bot)
	{
		return !ctx.isInFarmZone && (ctx.inventoryFull || ctx.weightPenalty >= 2);
	}

	@Override
	public void activate(BotInstance bot, long now)
	{
		SellService.sell(bot);
		EquipService.equip(bot);
		LevelUpService.checkAndUpgrade(bot);
		ShopService.openShop(bot);
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
		return "SellItemsGoapAction";
	}
}
