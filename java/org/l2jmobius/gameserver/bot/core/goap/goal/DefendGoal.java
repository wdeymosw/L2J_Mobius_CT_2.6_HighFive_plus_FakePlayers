/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.goap.goal;

import org.l2jmobius.gameserver.bot.core.goap.Fact;
import org.l2jmobius.gameserver.bot.core.goap.GoapGoal;
import org.l2jmobius.gameserver.bot.core.goap.GoapTuning;
import org.l2jmobius.gameserver.bot.core.goap.WorldState;

/**
 * Priority 90 — neutralize threats when the bot is under attack.
 */
public class DefendGoal implements GoapGoal
{
	private static final WorldState DESIRED = new WorldState();

	static
	{
		DESIRED.set(Fact.THREAT_NEUTRALIZED, true);
	}

	@Override
	public WorldState getDesiredState()
	{
		return DESIRED;
	}

	@Override
	public int getPriority(WorldState worldState)
	{
		return worldState.get(Fact.UNDER_ATTACK) && !worldState.get(Fact.HP_CRITICAL) ? GoapTuning.PRIORITY_DEFEND : 0;
	}

	@Override
	public String getName()
	{
		return "DefendGoal";
	}
}
