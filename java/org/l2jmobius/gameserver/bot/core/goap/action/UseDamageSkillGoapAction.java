/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.goap.action;

import org.l2jmobius.gameserver.bot.core.action.CastSkillAction;
import org.l2jmobius.gameserver.bot.core.goap.Fact;
import org.l2jmobius.gameserver.bot.core.goap.GoapAction;
import org.l2jmobius.gameserver.bot.core.goap.WorldState;
import org.l2jmobius.gameserver.bot.core.model.BotContext;
import org.l2jmobius.gameserver.bot.core.model.BotInstance;
import org.l2jmobius.gameserver.bot.core.service.SkillService;
import org.l2jmobius.gameserver.model.skill.Skill;

/**
 * pre:  TARGET_EXISTS, TARGET_IN_RANGE, HAS_DAMAGE_SKILL
 * eff:  TARGET_DEAD
 * cost: 0.8  (cheaper than AttackGoapAction — prefer skills over autoattack)
 * <p>
 * Casts the best available damage skill at the current target via
 * {@link CastSkillAction}. The planner selects this over
 * {@link AttackGoapAction} when a skill is available (lower cost).
 */
public class UseDamageSkillGoapAction implements GoapAction
{
	private static final WorldState PRECONDITIONS = new WorldState();
	private static final WorldState EFFECTS = new WorldState();

	static
	{
		PRECONDITIONS.set(Fact.TARGET_EXISTS, true);
		PRECONDITIONS.set(Fact.TARGET_IN_RANGE, true);
		PRECONDITIONS.set(Fact.HAS_DAMAGE_SKILL, true);
		EFFECTS.set(Fact.TARGET_DEAD, true);
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
		return 0.8f;
	}

	@Override
	public boolean isValid(BotContext ctx, BotInstance bot)
	{
		return ctx.hasTarget() && ctx.canAttackTarget && SkillService.hasDamageSkill(bot);
	}

	@Override
	public void activate(BotInstance bot, long now)
	{
		bot.clearQueue();
		final Skill best = SkillService.getBestDamageSkill(bot);
		if (best != null)
		{
			bot.queueAction(new CastSkillAction(best));
		}
	}

	@Override
	public boolean isComplete(BotInstance bot, BotContext ctx, long now)
	{
		return !ctx.hasTarget() || bot.isQueueIdle();
	}

	@Override
	public String getName()
	{
		return "UseDamageSkillGoapAction";
	}
}
