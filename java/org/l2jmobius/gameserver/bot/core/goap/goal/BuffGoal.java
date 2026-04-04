/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.goap.goal;

import org.l2jmobius.gameserver.bot.core.goap.Fact;
import org.l2jmobius.gameserver.bot.core.goap.GoapGoal;
import org.l2jmobius.gameserver.bot.core.goap.WorldState;

/**
 * Priority 47 — apply all pending self-buffs before hunting.
 * <p>
 * Active only in the farm zone and outside combat so the bot does not
 * interrupt a fight to rebuff. The desired state {@code HAS_BUFF=true} is
 * satisfied by {@link org.l2jmobius.gameserver.bot.core.goap.action.UseBuffSkillGoapAction}.
 * <p>
 * Priority sits between {@link FarmGoal} (50) and {@link HuntGoal} (45):
 * kill any current target first, then ensure buffs are up, then hunt.
 */
public class BuffGoal implements GoapGoal
{
	private static final WorldState DESIRED = new WorldState();

	static
	{
		DESIRED.set(Fact.HAS_BUFF, true);
	}

	@Override
	public WorldState getDesiredState()
	{
		return DESIRED;
	}

	@Override
	public int getPriority(WorldState worldState)
	{
		if (!worldState.get(Fact.IN_FARM_ZONE))
		{
			return 0; // buffs only matter in the farm zone
		}
		if (worldState.get(Fact.IN_COMBAT) || worldState.get(Fact.HP_CRITICAL))
		{
			return 0; // never interrupt combat or survival
		}
		if (worldState.get(Fact.IS_DEAD))
		{
			return 0;
		}
		if (worldState.get(Fact.HAS_BUFF))
		{
			return 0; // already fully buffed
		}
		return 47;
	}

	@Override
	public String getName()
	{
		return "BuffGoal";
	}
}
