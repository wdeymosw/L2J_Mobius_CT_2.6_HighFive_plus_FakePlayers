/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.goap.goal;

import org.l2jmobius.gameserver.bot.core.goap.AbstractGoapGoal;
import org.l2jmobius.gameserver.bot.core.goap.Fact;
import org.l2jmobius.gameserver.bot.core.goap.GoapTuning;
import org.l2jmobius.gameserver.bot.core.goap.WorldState;

/**
 * Priority 45 — find a target to attack.
 * <p>
 * Active when the bot is in the farm zone, healthy, and supplied but has no target.
 * When a target exists the desired state {@code TARGET_EXISTS=true} is already satisfied,
 * so this goal is skipped and {@link FarmGoal} takes over.
 */
public class HuntGoal extends AbstractGoapGoal
{
	private static final WorldState DESIRED = new WorldState();

	static
	{
		DESIRED.set(Fact.TARGET_EXISTS, true);
	}

	public HuntGoal()
	{
		super(DESIRED);
	}

	@Override
	public int getPriority(WorldState worldState)
	{
		if (!worldState.get(Fact.IN_FARM_ZONE))
		{
			return 0; // not in zone — WanderGoal will handle navigation
		}
		if (worldState.get(Fact.HP_CRITICAL) || worldState.get(Fact.HP_LOW))
		{
			return 0; // too hurt to hunt — RestoreGoal takes priority
		}
		if (!worldState.get(Fact.HAS_AMMO) || !worldState.get(Fact.INVENTORY_OK))
		{
			return 0; // need to restock first
		}
		return GoapTuning.PRIORITY_HUNT;
	}

	@Override
	public String getName()
	{
		return "HuntGoal";
	}
}
