/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.goap.action;

import org.l2jmobius.gameserver.bot.core.goap.Fact;
import org.l2jmobius.gameserver.bot.core.goap.GoapAction;
import org.l2jmobius.gameserver.bot.core.goap.WorldState;
import org.l2jmobius.gameserver.bot.core.model.BotContext;
import org.l2jmobius.gameserver.bot.core.model.BotInstance;
import org.l2jmobius.gameserver.bot.core.service.LootService;

/**
 * pre:  IN_FARM_ZONE=true, LOOT_NEARBY=true
 * eff:  LOOT_NEARBY=false
 * cost: 1.0
 * <p>
 * Collects all ground items within scan radius one at a time.
 * {@link LootService#pickupNearest} handles both immediate pickup (within 70 units)
 * and approach movement (further away) via the AI intention directly.
 * The GoapAgent re-activates this action each tick until no loot remains.
 * <p>
 * Driven by {@link org.l2jmobius.gameserver.bot.core.goap.goal.PickupGoal}.
 */
public class PickupLootGoapAction implements GoapAction
{
	private static final WorldState PRECONDITIONS = new WorldState();
	private static final WorldState EFFECTS = new WorldState();

	static
	{
		PRECONDITIONS.set(Fact.IN_FARM_ZONE, true);
		PRECONDITIONS.set(Fact.LOOT_NEARBY, true);
		EFFECTS.set(Fact.LOOT_NEARBY, false);
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
		return ctx.isInFarmZone && LootService.hasNearbyLoot(bot);
	}

	@Override
	public void activate(BotInstance bot, long now)
	{
		bot.clearQueue();
		// LootService sets AI intention directly (MOVE_TO or doPickupItem).
		// The executor queue stays idle, triggering re-activate next tick.
		LootService.pickupNearest(bot);
	}

	@Override
	public boolean isComplete(BotInstance bot, BotContext ctx, long now)
	{
		return !LootService.hasNearbyLoot(bot);
	}

	@Override
	public String getName()
	{
		return "PickupLootGoapAction";
	}
}
