/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.goap.goal;

import org.l2jmobius.gameserver.bot.core.goap.Fact;
import org.l2jmobius.gameserver.bot.core.goap.GoapGoal;
import org.l2jmobius.gameserver.bot.core.goap.WorldState;

/**
 * Priority 40 — restore HP and MP when not in combat and not fully recovered.
 */
public class RestoreGoal implements GoapGoal
{
	private static final WorldState DESIRED = new WorldState();

	static
	{
		DESIRED.set(Fact.HP_FULL, true);
		DESIRED.set(Fact.MP_FULL, true);
	}

	@Override
	public WorldState getDesiredState()
	{
		return DESIRED;
	}

	@Override
	public int getPriority(WorldState worldState)
	{
		if (worldState.get(Fact.IN_COMBAT) || worldState.get(Fact.HP_CRITICAL))
		{
			return 0; // SurviveGoal handles critical; don't sit mid-combat
		}
		if (worldState.get(Fact.HP_MID) || worldState.get(Fact.MP_LOW))
		{
			return 40;
		}
		return 0;
	}

	@Override
	public String getName()
	{
		return "RestoreGoal";
	}
}
