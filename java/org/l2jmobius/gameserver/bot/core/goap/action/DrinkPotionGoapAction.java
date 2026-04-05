/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.goap.action;

import org.l2jmobius.gameserver.bot.core.action.DrinkPotionAction;
import org.l2jmobius.gameserver.bot.core.goap.AbstractGoapAction;
import org.l2jmobius.gameserver.bot.core.goap.Fact;
import org.l2jmobius.gameserver.bot.core.goap.GoapTuning;
import org.l2jmobius.gameserver.bot.core.goap.WorldState;
import org.l2jmobius.gameserver.bot.core.model.BotContext;
import org.l2jmobius.gameserver.bot.core.model.BotInstance;
import org.l2jmobius.gameserver.bot.core.service.PotionData;

/**
 * pre:  POTION_READY, HP_LOW, HAS_POTIONS
 * eff:  HP_LOW=false  (optimistic — real HP recovery is async)
 * cost: 1.0
 */
public class DrinkPotionGoapAction extends AbstractGoapAction
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

	public DrinkPotionGoapAction()
	{
		super(PRECONDITIONS, EFFECTS);
	}

	@Override
	public float getCost(WorldState worldState)
	{
		return GoapTuning.COST_DRINK_POTION;
	}

	@Override
	public boolean isValid(BotContext ctx, BotInstance bot)
	{
		return ctx.potionReuseReady && (ctx.hpPercent < 60.0) && PotionData.hasPotions(bot.getPlayer());
	}

	@Override
	public void activate(BotInstance bot, long now)
	{
		bot.replaceQueue(new DrinkPotionAction());
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
