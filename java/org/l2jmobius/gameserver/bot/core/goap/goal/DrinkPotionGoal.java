/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.goap.goal;

import org.l2jmobius.gameserver.bot.core.goap.AbstractGoapGoal;
import org.l2jmobius.gameserver.bot.core.goap.Fact;
import org.l2jmobius.gameserver.bot.core.goap.GoapTuning;
import org.l2jmobius.gameserver.bot.core.goap.WorldState;

/**
 * Priority 52 — drink a healing potion when HP is low and a potion is ready.
 * <p>
 * Fires both in and out of combat. Priority 52 is above FarmGoal (50) so the
 * planner will choose this goal mid-fight when the conditions are met, allowing
 * GoapAgent.shouldInterrupt() to break the active plan and drink immediately.
 * <p>
 * Desired state: {@code HP_LOW=false}
 * Resolved by: {@link org.l2jmobius.gameserver.bot.core.goap.action.DrinkPotionGoapAction}
 */
public class DrinkPotionGoal extends AbstractGoapGoal
{
	private static final WorldState DESIRED = new WorldState();

	static
	{
		DESIRED.set(Fact.HP_LOW, false);
	}

	public DrinkPotionGoal()
	{
		super(DESIRED);
	}

	@Override
	public int getPriority(WorldState worldState)
	{
		if (worldState.get(Fact.HP_CRITICAL))
		{
			return 0; // SurviveGoal handles critical — teleport out
		}
		if (worldState.get(Fact.HP_LOW) && worldState.get(Fact.POTION_READY) && worldState.get(Fact.HAS_POTIONS))
		{
			return GoapTuning.PRIORITY_DRINK_POTION;
		}
		return 0;
	}

	@Override
	public String getName()
	{
		return "DrinkPotionGoal";
	}
}
