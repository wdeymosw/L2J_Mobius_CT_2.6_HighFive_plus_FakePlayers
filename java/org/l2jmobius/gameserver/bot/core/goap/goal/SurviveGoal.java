/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.goap.goal;

import org.l2jmobius.gameserver.bot.core.goap.Fact;
import org.l2jmobius.gameserver.bot.core.goap.GoapGoal;
import org.l2jmobius.gameserver.bot.core.goap.WorldState;

/**
 * Priority 100 — escape to a safe place when HP is critical, then fully recover.
 * Overrides all other goals.
 * <p>
 * The desired state includes IN_SAFE_PLACE + HP_FULL + MP_FULL so the planner
 * builds the full plan in one shot: [TeleportToCityGoapAction, SitRestGoapAction].
 * This avoids a two-tick gap where the bot stands idle after teleporting but
 * before RestoreGoal kicks in.
 */
public class SurviveGoal implements GoapGoal
{
	private static final WorldState DESIRED = new WorldState();

	static
	{
		DESIRED.set(Fact.IN_SAFE_PLACE, true);
		DESIRED.set(Fact.HP_CRITICAL, false);
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
		return worldState.get(Fact.HP_CRITICAL) ? 100 : 0;
	}

	@Override
	public String getName()
	{
		return "SurviveGoal";
	}
}
