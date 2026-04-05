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
		DESIRED.set(Fact.HP_LOW, false);
		DESIRED.set(Fact.MP_LOW, false);
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
		if (worldState.get(Fact.LOOT_NEARBY))
		{
			return 0; // collect the drop first, then rest
		}
		// Only sit to recover if HP is genuinely low (< 60%) or MP is depleted.
		// HP_MID (< 99%) is too broad — bot would sit after every minor scratch.
		if (worldState.get(Fact.HP_LOW) || worldState.get(Fact.MP_LOW))
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
