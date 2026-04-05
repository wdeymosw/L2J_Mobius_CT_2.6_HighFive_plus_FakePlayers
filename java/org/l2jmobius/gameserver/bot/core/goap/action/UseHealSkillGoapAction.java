/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.goap.action;

import org.l2jmobius.gameserver.bot.core.goap.AbstractGoapAction;
import org.l2jmobius.gameserver.bot.core.goap.Fact;
import org.l2jmobius.gameserver.bot.core.goap.GoapTuning;
import org.l2jmobius.gameserver.bot.core.goap.WorldState;
import org.l2jmobius.gameserver.bot.core.model.BotContext;
import org.l2jmobius.gameserver.bot.core.model.BotInstance;
import org.l2jmobius.gameserver.bot.core.service.SkillService;

/**
 * pre:  HAS_HEAL_SKILL, MP_OK
 * eff:  HP_LOW=false
 * cost: 1.5
 * <p>
 * One-shot: casts the best available self-heal skill.
 */
public class UseHealSkillGoapAction extends AbstractGoapAction
{
	private static final WorldState PRECONDITIONS = new WorldState();
	private static final WorldState EFFECTS = new WorldState();

	static
	{
		PRECONDITIONS.set(Fact.HAS_HEAL_SKILL, true);
		PRECONDITIONS.set(Fact.MP_OK, true);
		EFFECTS.set(Fact.HP_LOW, false);
	}

	public UseHealSkillGoapAction()
	{
		super(PRECONDITIONS, EFFECTS);
	}

	@Override
	public float getCost(WorldState worldState)
	{
		return GoapTuning.COST_HEAL_SKILL;
	}

	@Override
	public boolean isValid(BotContext ctx, BotInstance bot)
	{
		return SkillService.hasHealSkill(bot);
	}

	@Override
	public void activate(BotInstance bot, long now)
	{
		SkillService.tryHealSkill(bot);
	}

	@Override
	public boolean isComplete(BotInstance bot, BotContext ctx, long now)
	{
		return true; // one-shot — skill is cast immediately in activate()
	}

	@Override
	public String getName()
	{
		return "UseHealSkillGoapAction";
	}
}
