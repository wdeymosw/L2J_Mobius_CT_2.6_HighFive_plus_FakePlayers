/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.goap.goal;

import org.l2jmobius.gameserver.bot.core.goap.AbstractGoapGoal;
import org.l2jmobius.gameserver.bot.core.goap.Fact;
import org.l2jmobius.gameserver.bot.core.goap.WorldState;

/**
 * Priority 20 — travel to city (safe place).
 * Used by CityIdleBehaviour to move the bot from farm zone to city.
 * Satisfied when IN_SAFE_PLACE=true.
 */
public class GoToCityGoal extends AbstractGoapGoal
{
	private static final WorldState DESIRED = new WorldState();

	static
	{
		DESIRED.set(Fact.IN_SAFE_PLACE, true);
	}

	public GoToCityGoal()
	{
		super(DESIRED);
	}

	@Override
	public int getPriority(WorldState worldState)
	{
		// Below RestockGoal (30) so restock/sell happens first if needed,
		// but above WanderGoal (10) so city travel is preferred over farm return.
		return 20;
	}

	@Override
	public String getName()
	{
		return "GoToCityGoal";
	}
}
