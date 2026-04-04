/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.goap.action;

import java.util.concurrent.ThreadLocalRandom;

import org.l2jmobius.gameserver.bot.core.action.MoveToAction;
import org.l2jmobius.gameserver.bot.core.action.WaitAction;
import org.l2jmobius.gameserver.bot.core.goap.Fact;
import org.l2jmobius.gameserver.bot.core.goap.GoapAction;
import org.l2jmobius.gameserver.bot.core.goap.WorldState;
import org.l2jmobius.gameserver.bot.core.model.BotContext;
import org.l2jmobius.gameserver.bot.core.model.BotInstance;
import org.l2jmobius.gameserver.bot.core.service.TargetService;
import org.l2jmobius.gameserver.model.Location;

/**
 * pre:  IN_FARM_ZONE
 * eff:  TARGET_EXISTS
 * cost: 3.0
 * <p>
 * Searches for the nearest attackable mob. If none is found, wanders
 * to a random point in the farm zone to trigger respawns.
 * <p>
 * Buffing is now handled by {@link UseBuffSkillGoapAction} via {@link org.l2jmobius.gameserver.bot.core.goap.goal.BuffGoal}.
 * Loot pickup is now handled by {@link PickupLootGoapAction} via {@link org.l2jmobius.gameserver.bot.core.goap.goal.PickupGoal}.
 * These goals activate at higher priorities (47 and 46) so the bot always
 * buffs and loots before this action runs.
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

		// Search for a target
		TargetService.findTarget(bot);
		if (bot.hasTarget())
		{
			return; // GoapAgent will replan next tick: MoveToTarget + Attack
		}

		// Nothing found — wander to a random zone point
		final Location wander = bot.getProfile().getZone().randomPointInside();
		bot.queueAction(new WaitAction(1000 + ThreadLocalRandom.current().nextInt(1000)));
		bot.queueAction(new MoveToAction(wander));
	}

	@Override
	public boolean isComplete(BotInstance bot, BotContext ctx, long now)
	{
		return ctx.hasTarget() || bot.isQueueIdle();
	}

	@Override
	public String getName()
	{
		return "SearchTargetGoapAction";
	}
}
