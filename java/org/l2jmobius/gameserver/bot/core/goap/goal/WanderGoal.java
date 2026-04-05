/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.goap.goal;

import org.l2jmobius.gameserver.bot.core.goap.AbstractGoapGoal;
import org.l2jmobius.gameserver.bot.core.goap.Fact;
import org.l2jmobius.gameserver.bot.core.goap.GoapTuning;
import org.l2jmobius.gameserver.bot.core.goap.WorldState;

/**
 * Priority 10 — fallback goal: just be in the farm zone.
 * Always active if nothing else takes priority.
 */
public class WanderGoal extends AbstractGoapGoal
{
	private static final WorldState DESIRED = new WorldState();

	static
	{
		DESIRED.set(Fact.IN_FARM_ZONE, true);
	}

	public WanderGoal()
	{
		super(DESIRED);
	}

	@Override
	public int getPriority(WorldState worldState)
	{
		return GoapTuning.PRIORITY_WANDER;
	}

	@Override
	public String getName()
	{
		return "WanderGoal";
	}
}
