/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.goap.goal;

import org.l2jmobius.gameserver.bot.core.goap.AbstractGoapGoal;
import org.l2jmobius.gameserver.bot.core.goap.Fact;
import org.l2jmobius.gameserver.bot.core.goap.GoapTuning;
import org.l2jmobius.gameserver.bot.core.goap.WorldState;

/**
 * Priority 30 — go to city to sell loot and restock supplies.
 * Triggers when inventory is full or ammo runs out.
 */
public class RestockGoal extends AbstractGoapGoal
{
	private static final WorldState DESIRED = new WorldState();

	static
	{
		DESIRED.set(Fact.HAS_AMMO, true);
		DESIRED.set(Fact.INVENTORY_OK, true);
	}

	public RestockGoal()
	{
		super(DESIRED);
	}

	@Override
	public int getPriority(WorldState worldState)
	{
		if (!worldState.get(Fact.HAS_AMMO) || !worldState.get(Fact.INVENTORY_OK))
		{
			return GoapTuning.PRIORITY_RESTOCK;
		}
		return 0;
	}

	@Override
	public String getName()
	{
		return "RestockGoal";
	}
}
