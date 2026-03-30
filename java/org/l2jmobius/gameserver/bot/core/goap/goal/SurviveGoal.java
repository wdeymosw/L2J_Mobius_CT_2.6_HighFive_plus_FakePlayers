/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.goap.goal;

import org.l2jmobius.gameserver.bot.core.goap.Fact;
import org.l2jmobius.gameserver.bot.core.goap.GoapGoal;
import org.l2jmobius.gameserver.bot.core.goap.WorldState;

/**
 * Priority 100 — escape to a safe place when HP is critical.
 * Overrides all other goals.
 */
public class SurviveGoal implements GoapGoal
{
	private static final WorldState DESIRED = new WorldState();

	static
	{
		DESIRED.set(Fact.IN_SAFE_PLACE, true);
	}

	@Override
	public WorldState getDesiredState()
	{
		return DESIRED;
	}

	@Override
	public int getPriority(WorldState worldState)
	{
		return worldState.get(Fact.HP_CRITICAL) ? 100 : 0;
	}

	@Override
	public String getName()
	{
		return "SurviveGoal";
	}
}
