/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.goap.action;

import org.l2jmobius.gameserver.bot.core.action.DrinkPotionAction;
import org.l2jmobius.gameserver.bot.core.goap.Fact;
import org.l2jmobius.gameserver.bot.core.goap.GoapAction;
import org.l2jmobius.gameserver.bot.core.goap.WorldState;
import org.l2jmobius.gameserver.bot.core.model.BotContext;
import org.l2jmobius.gameserver.bot.core.model.BotInstance;
import org.l2jmobius.gameserver.bot.core.service.PotionData;

/**
 * pre:  POTION_READY, HP_LOW, HAS_POTIONS
 * eff:  HP_LOW=false  (optimistic — real HP recovery is async)
 * cost: 1.0
 */
public class DrinkPotionGoapAction implements GoapAction
{
	private static final WorldState PRECONDITIONS = new WorldState();
	private static final WorldState EFFECTS = new WorldState();

	static
	{
		PRECONDITIONS.set(Fact.POTION_READY, true);
		PRECONDITIONS.set(Fact.HP_LOW, true);
		PRECONDITIONS.set(Fact.HAS_POTIONS, true);
		EFFECTS.set(Fact.HP_LOW, false);
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
		if (!ctx.potionReuseReady || !(ctx.hpPercent < 60.0))
		{
			return false;
		}
		for (int id : PotionData.HEAL_POTION_IDS)
		{
			if (bot.getPlayer().getInventory().getInventoryItemCount(id, -1) > 0)
			{
				return true;
			}
		}
		return false;
	}

	@Override
	public void activate(BotInstance bot, long now)
	{
		bot.clearQueue();
		bot.queueAction(new DrinkPotionAction());
	}

	@Override
	public boolean isComplete(BotInstance bot, BotContext ctx, long now)
	{
		return bot.isQueueIdle();
	}

	@Override
	public String getName()
	{
		return "DrinkPotionGoapAction";
	}
}
