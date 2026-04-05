/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.goap.goal;

import org.l2jmobius.gameserver.bot.core.goap.AbstractGoapGoal;
import org.l2jmobius.gameserver.bot.core.goap.Fact;
import org.l2jmobius.gameserver.bot.core.goap.GoapTuning;
import org.l2jmobius.gameserver.bot.core.goap.WorldState;

/**
 * Priority 48 — collect nearby loot immediately after a kill, before buffs or hunting.
 * <p>
 * Active when ground items are within scan radius in the farm zone and combat is over.
 * The desired state {@code LOOT_NEARBY=false} is achieved by
 * {@link org.l2jmobius.gameserver.bot.core.goap.action.PickupLootGoapAction}.
 * <p>
 * Priority sits between {@link FarmGoal} (50) and {@link BuffGoal} (47):
 * kill the target first, then collect the drop, then buff up, then hunt again.
 * The bot must not have an active target ({@code TARGET_EXISTS=false}) so loot
 * pickup never interrupts an ongoing fight.
 */
public class PickupGoal extends AbstractGoapGoal
{
	private static final WorldState DESIRED = new WorldState();

	static
	{
		DESIRED.set(Fact.LOOT_NEARBY, false);
	}

	public PickupGoal()
	{
		super(DESIRED);
	}

	@Override
	public int getPriority(WorldState worldState)
	{
		if (!worldState.get(Fact.IN_FARM_ZONE))
		{
			return 0; // only pick up loot in the farm zone
		}
		if (worldState.get(Fact.IS_DEAD) || worldState.get(Fact.HP_CRITICAL))
		{
			return 0; // can't loot while dead; survival takes absolute precedence
		}
		if (worldState.get(Fact.TARGET_EXISTS))
		{
			return 0; // combat still ongoing — never interrupt a fight to loot
		}
		if (!worldState.get(Fact.LOOT_NEARBY))
		{
			return 0; // no loot present — goal already satisfied
		}
		return GoapTuning.PRIORITY_PICKUP; // above BuffGoal: kill → loot → buff → hunt
	}

	@Override
	public String getName()
	{
		return "PickupGoal";
	}
}
