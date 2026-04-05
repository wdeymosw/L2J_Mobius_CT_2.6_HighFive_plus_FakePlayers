/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.goap.goal;

import org.l2jmobius.gameserver.bot.core.goap.AbstractGoapGoal;
import org.l2jmobius.gameserver.bot.core.goap.Fact;
import org.l2jmobius.gameserver.bot.core.goap.GoapTuning;
import org.l2jmobius.gameserver.bot.core.goap.WorldState;

/**
 * Priority 50 — kill the current target.
 * <p>
 * Desired state {@code TARGET_DEAD=true} is only unsatisfied when a living target exists,
 * so this goal naturally activates only when there is something to kill.
 * Finding a target is the job of {@link HuntGoal}.
 */
public class FarmGoal extends AbstractGoapGoal
{
	private static final WorldState DESIRED = new WorldState();

	static
	{
		DESIRED.set(Fact.TARGET_DEAD, true);
		// IN_FARM_ZONE intentionally omitted: killing a pulled mob outside the zone is fine.
		// WanderGoal handles "return to zone" after the fight.
	}

	public FarmGoal()
	{
		super(DESIRED);
	}

	@Override
	public int getPriority(WorldState worldState)
	{
		if (worldState.get(Fact.HP_CRITICAL))
		{
			return 0; // SurviveGoal handles critical — flee/teleport
		}
		if (!worldState.get(Fact.HAS_AMMO) || !worldState.get(Fact.INVENTORY_OK))
		{
			return 0;
		}
		if (!worldState.get(Fact.TARGET_EXISTS))
		{
			return 0; // No living target — let HuntGoal find one (priority 45)
		}
		// HP_LOW alone does NOT suppress FarmGoal: if the bot has a living target it
		// should finish the fight. RestoreGoal (priority 40) only wins when there is
		// no target (IN_COMBAT=false). SurviveGoal (priority 100) handles truly
		// dangerous situations regardless.
		return GoapTuning.PRIORITY_FARM;
	}

	@Override
	public String getName()
	{
		return "FarmGoal";
	}
}
