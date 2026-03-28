/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.service;

import java.util.logging.Logger;

import org.l2jmobius.gameserver.bot.core.model.BotInstance;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.effects.EffectType;
import org.l2jmobius.gameserver.model.skill.Skill;
import org.l2jmobius.gameserver.model.skill.targets.TargetType;

/**
 * Casts self-targeted buffs for bots between fights.
 * <p>
 * Called each tick during SEARCH_TARGET — one buff per tick (human-like pacing).
 * Toggle skills are included: once enabled they stay active and are skipped on
 * subsequent ticks via {@code isAffectedBySkill}.
 * <p>
 * Excluded skill types:
 * <ul>
 *   <li>Damage skills ({@code isDamage()}) — offensive, not buffs</li>
 *   <li>Heal skills ({@code hasEffectType(EffectType.HEAL)}) — handled by {@link SkillService}</li>
 *   <li>Skills already active on the caster ({@code isAffectedBySkill})</li>
 *   <li>Skills on cooldown or with insufficient MP</li>
 * </ul>
 */
public class BuffService
{
	private static final Logger LOGGER = Logger.getLogger(BuffService.class.getName());

	private BuffService()
	{
	}

	/**
	 * Tries to cast one ready self-buff.
	 * Prefers the highest-level eligible skill (proxy for power within a skill line).
	 *
	 * @param bot the bot that needs to buff itself
	 * @return {@code true} if a buff was cast, {@code false} if no buff was needed or available
	 */
	public static boolean tryBuffSelf(BotInstance bot)
	{
		final Player player = bot.getPlayer();
		Skill best = null;

		for (Skill skill : player.getAllSkills())
		{
			if (!skill.isActive())
			{
				continue;
			}
			if (skill.getTargetType() != TargetType.SELF)
			{
				continue;
			}
			if (skill.isDamage())
			{
				continue;
			}
			if (skill.hasEffectType(EffectType.HEAL))
			{
				continue;
			}
			if (player.isAffectedBySkill(skill.getId()))
			{
				continue; // already active — skip
			}
			if (player.isSkillDisabled(skill))
			{
				continue;
			}
			if (player.getCurrentMp() < skill.getMpConsume())
			{
				continue;
			}

			if ((best == null) || (skill.getLevel() > best.getLevel()))
			{
				best = skill;
			}
		}

		if (best == null)
		{
			return false;
		}

		LOGGER.fine("BuffService: " + player.getName() + " casting self-buff [" + best.getName() + " lv" + best.getLevel() + "]");
		player.useMagic(best, false, false);
		return true;
	}

	/**
	 * Returns {@code true} if the bot has at least one self-buff that is ready to cast.
	 * Useful for decisions that need to know whether buffing is pending without actually casting.
	 *
	 * @param bot the bot to check
	 * @return {@code true} if a self-buff is available
	 */
	public static boolean needsBuff(BotInstance bot)
	{
		final Player player = bot.getPlayer();
		for (Skill skill : player.getAllSkills())
		{
			if (!skill.isActive())
			{
				continue;
			}
			if (skill.getTargetType() != TargetType.SELF)
			{
				continue;
			}
			if (skill.isDamage())
			{
				continue;
			}
			if (skill.hasEffectType(EffectType.HEAL))
			{
				continue;
			}
			if (player.isAffectedBySkill(skill.getId()))
			{
				continue;
			}
			if (player.isSkillDisabled(skill))
			{
				continue;
			}
			if (player.getCurrentMp() < skill.getMpConsume())
			{
				continue;
			}
			return true;
		}
		return false;
	}
}
