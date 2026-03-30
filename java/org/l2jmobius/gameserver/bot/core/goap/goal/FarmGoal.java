/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.goap.goal;

import org.l2jmobius.gameserver.bot.core.goap.Fact;
import org.l2jmobius.gameserver.bot.core.goap.GoapGoal;
import org.l2jmobius.gameserver.bot.core.goap.WorldState;

/**
 * Priority 50 — kill the current target.
 * <p>
 * Desired state {@code TARGET_DEAD=true} is only unsatisfied when a living target exists,
 * so this goal naturally activates only when there is something to kill.
 * Finding a target is the job of {@link HuntGoal}.
 */
public class FarmGoal implements GoapGoal
{
	private static final WorldState DESIRED = new WorldState();

	static
	{
		DESIRED.set(Fact.TARGET_DEAD, true);
		// IN_FARM_ZONE intentionally omitted: killing a pulled mob outside the zone is fine.
		// WanderGoal handles "return to zone" after the fight.
	}

	@Override
	public WorldState getDesiredState()
	{
		return DESIRED;
	}

	@Override
	public int getPriority(WorldState worldState)
	{
		if (worldState.get(Fact.HP_CRITICAL) || worldState.get(Fact.HP_LOW))
		{
			return 0;
		}
		if (!worldState.get(Fact.HAS_AMMO) || !worldState.get(Fact.INVENTORY_OK))
		{
			return 0;
		}
		return 50;
	}

	@Override
	public String getName()
	{
		return "FarmGoal";
	}
}
