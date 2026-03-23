/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.service;

import org.l2jmobius.gameserver.bot.model.BotInstance;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.skill.Skill;
import org.l2jmobius.gameserver.model.skill.targets.TargetType;

/**
 * Selects and casts the best combat skill for a bot each tick.
 * <p>
 * Strategy:
 * <ul>
 *   <li>Scan the player's active damage skills; skip skills on cooldown or with
 *       insufficient MP.</li>
 *   <li>Among ready skills, prefer the one with the highest level (proxy for
 *       power within a skill line).</li>
 *   <li>Cast via {@code Player.useMagic()} which handles range, conditions and
 *       sets the AI CAST intention.</li>
 * </ul>
 * SELF / PARTY / CLAN / support target types are excluded here —
 * self-buffs are handled separately in a future BuffService.
 */
public class SkillService
{
	/** Target types that indicate an offensive single/area skill we can use in combat. */
	private static final TargetType[] COMBAT_TARGET_TYPES =
	{
		TargetType.ONE,
		TargetType.AREA,
		TargetType.FRONT_AREA,
		TargetType.BEHIND_AREA,
		TargetType.AURA,
		TargetType.FRONT_AURA,
		TargetType.BEHIND_AURA,
		TargetType.UNDEAD,
		TargetType.AREA_UNDEAD,
	};

	private SkillService()
	{
	}

	/**
	 * Tries to cast the best available damage skill at the bot's current target.
	 *
	 * @param bot the attacking bot
	 * @return {@code true} if a skill was cast, {@code false} if nothing was available
	 */
	public static boolean tryDamageSkill(BotInstance bot)
	{
		final Player player = bot.getPlayer();
		if ((bot.getTarget() == null) || bot.getTarget().isDead())
		{
			return false;
		}

		Skill best = null;
		for (Skill skill : player.getAllSkills())
		{
			if (!skill.isActive() || !skill.isDamage())
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
			if (!isCombatTargetType(skill.getTargetType()))
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

		player.useMagic(best, false, false);
		return true;
	}

	private static boolean isCombatTargetType(TargetType type)
	{
		for (TargetType t : COMBAT_TARGET_TYPES)
		{
			if (t == type)
			{
				return true;
			}
		}
		return false;
	}
}
