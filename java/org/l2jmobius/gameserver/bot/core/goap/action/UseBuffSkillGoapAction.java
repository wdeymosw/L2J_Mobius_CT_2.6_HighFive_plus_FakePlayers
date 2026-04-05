/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.goap.action;

import org.l2jmobius.gameserver.bot.core.action.WaitAction;
import org.l2jmobius.gameserver.bot.core.goap.Fact;
import org.l2jmobius.gameserver.bot.core.goap.GoapAction;
import org.l2jmobius.gameserver.bot.core.goap.GoapTuning;
import org.l2jmobius.gameserver.bot.core.goap.WorldState;
import org.l2jmobius.gameserver.bot.core.model.BotContext;
import org.l2jmobius.gameserver.bot.core.model.BotInstance;
import org.l2jmobius.gameserver.bot.core.service.BuffService;

/**
 * pre:  IN_COMBAT=false, IS_DEAD=false
 * eff:  HAS_BUFF=true
 * cost: 1.0
 * <p>
 * Casts one self-buff per activation; the GoapAgent re-activates this action
 * while {@link BuffService#needsBuff} returns {@code true}, applying all pending
 * buffs one at a time in a human-like paced sequence.
 * <p>
 * Driven by {@link org.l2jmobius.gameserver.bot.core.goap.goal.BuffGoal}.
 */
public class UseBuffSkillGoapAction implements GoapAction
{
	private static final WorldState PRECONDITIONS = new WorldState();
	private static final WorldState EFFECTS = new WorldState();

	static
	{
		PRECONDITIONS.set(Fact.IN_COMBAT, false);
		PRECONDITIONS.set(Fact.IS_DEAD, false);
		EFFECTS.set(Fact.HAS_BUFF, true);
	}

	@Override
	public WorldState getPreconditions()
	{
		return PRECONDITIONS;
	}

	@Override
	public WorldState getEffects()
	{
		return EFFECTS;
	}

	@Override
	public float getCost(WorldState worldState)
	{
		return GoapTuning.COST_USE_BUFF;
	}

	@Override
	public boolean isValid(BotContext ctx, BotInstance bot)
	{
		return !bot.getPlayer().isDead() && BuffService.needsBuff(bot);
	}

	@Override
	public void activate(BotInstance bot, long now)
	{
		bot.clearQueue();
		if (BuffService.tryBuffSelf(bot))
		{
			// Give the cast animation time to play before checking again.
			bot.queueAction(new WaitAction(500));
		}
	}

	@Override
	public boolean isComplete(BotInstance bot, BotContext ctx, long now)
	{
		// Done only when ALL pending self-buffs have been applied.
		// The re-activate mechanism fires when the queue goes idle while this
		// action is still current, so each buff cast triggers a new activate().
		return !BuffService.needsBuff(bot);
	}

	@Override
	public String getName()
	{
		return "UseBuffSkillGoapAction";
	}
}
