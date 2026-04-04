/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.goap.goal;

import org.l2jmobius.gameserver.bot.core.goap.Fact;
import org.l2jmobius.gameserver.bot.core.goap.GoapGoal;
import org.l2jmobius.gameserver.bot.core.goap.WorldState;

/**
 * Priority 46 — collect nearby loot before searching for a new target.
 * <p>
 * Active when ground items are within scan radius in the farm zone.
 * The desired state {@code LOOT_NEARBY=false} is achieved by
 * {@link org.l2jmobius.gameserver.bot.core.goap.action.PickupLootGoapAction}.
 * <p>
 * Priority sits between {@link BuffGoal} (47) and {@link HuntGoal} (45):
 * buff up first, then clear loot, then hunt for new targets.
 */
public class PickupGoal implements GoapGoal
{
	private static final WorldState DESIRED = new WorldState();

	static
	{
		DESIRED.set(Fact.LOOT_NEARBY, false);
	}

	@Override
	public WorldState getDesiredState()
	{
		return DESIRED;
	}

	@Override
	public int getPriority(WorldState worldState)
	{
		if (!worldState.get(Fact.IN_FARM_ZONE))
		{
			return 0; // only pick up loot in the farm zone
		}
		if (worldState.get(Fact.HP_CRITICAL) || worldState.get(Fact.IS_DEAD))
		{
			return 0; // survival takes absolute precedence
		}
		if (!worldState.get(Fact.LOOT_NEARBY))
		{
			return 0; // no loot present — goal already satisfied
		}
		return 46;
	}

	@Override
	public String getName()
	{
		return "PickupGoal";
	}
}
