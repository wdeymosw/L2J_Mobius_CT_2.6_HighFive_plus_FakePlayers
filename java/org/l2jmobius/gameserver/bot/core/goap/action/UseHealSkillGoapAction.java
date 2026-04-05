/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.goap.action;

import org.l2jmobius.gameserver.bot.core.goap.Fact;
import org.l2jmobius.gameserver.bot.core.goap.GoapAction;
import org.l2jmobius.gameserver.bot.core.goap.GoapTuning;
import org.l2jmobius.gameserver.bot.core.goap.WorldState;
import org.l2jmobius.gameserver.bot.core.model.BotContext;
import org.l2jmobius.gameserver.bot.core.model.BotInstance;
import org.l2jmobius.gameserver.bot.core.service.SkillService;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.effects.EffectType;
import org.l2jmobius.gameserver.model.skill.Skill;
import org.l2jmobius.gameserver.model.skill.targets.TargetType;

/**
 * pre:  HAS_HEAL_SKILL, MP_OK
 * eff:  HP_LOW=false
 * cost: 1.5
 * <p>
 * One-shot: casts the best available self-heal skill.
 */
public class UseHealSkillGoapAction implements GoapAction
{
	private static final WorldState PRECONDITIONS = new WorldState();
	private static final WorldState EFFECTS = new WorldState();

	static
	{
		PRECONDITIONS.set(Fact.HAS_HEAL_SKILL, true);
		PRECONDITIONS.set(Fact.MP_OK, true);
		EFFECTS.set(Fact.HP_LOW, false);
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
		return GoapTuning.COST_HEAL_SKILL;
	}

	@Override
	public boolean isValid(BotContext ctx, BotInstance bot)
	{
		if (ctx.mpPercent < 40.0)
		{
			return false;
		}
		final Player player = bot.getPlayer();
		for (Skill skill : player.getAllSkills())
		{
			if ((skill.getTargetType() == TargetType.SELF) && skill.hasEffectType(EffectType.HEAL) && !player.isSkillDisabled(skill) && (player.getCurrentMp() >= skill.getMpConsume()))
			{
				return true;
			}
		}
		return false;
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
