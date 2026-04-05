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
	private static final Logger LOGGER = Logger.getLogger(SkillService.class.getName());

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
	 * One-time setup called when a bot enters the world.
	 * Gives all class/level-appropriate skills and enables the correct auto-shot.
	 *
	 * @param bot the bot to set up
	 */
	public static void setup(BotInstance bot)
	{
		final Player player = bot.getPlayer();

		// Give every skill the character is eligible to learn at current level.
		final int learned = player.giveAvailableSkills(true, true, true);
		LOGGER.info("SkillService: gave " + learned + " skills to " + player.getName());

		// Enable auto-shot matching the equipped weapon (soulshot or spiritshot).
		final int shotId = SupplyService.getShotId(bot);
		if (shotId > 0)
		{
			player.addAutoSoulShot(shotId);
			LOGGER.info("SkillService: enabled auto-shot id=" + shotId + " for " + player.getName());
		}
	}

	/**
	 * Re-applies auto-shot after equipment changes (e.g. weapon grade upgrade).
	 * Clears any previously registered shot IDs first to avoid stale entries.
	 *
	 * @param bot the bot whose auto-shot should be refreshed
	 */
	public static void refreshAutoShot(BotInstance bot)
	{
		bot.getPlayer().getAutoSoulShot().clear();
		final int shotId = SupplyService.getShotId(bot);
		if (shotId > 0)
		{
			bot.getPlayer().addAutoSoulShot(shotId);
		}
	}

	/**
	 * Tries to cast a SELF-targeting heal skill.
	 * Called before retreating on low HP — one attempt per tick.
	 *
	 * @param bot the bot that needs healing
	 * @return {@code true} if a heal was cast, {@code false} if none available
	 */
	public static boolean tryHealSkill(BotInstance bot)
	{
		final Player player = bot.getPlayer();
		for (Skill skill : player.getAllSkills())
		{
			if (skill.getTargetType() != TargetType.SELF)
			{
				continue;
			}
			if (!skill.hasEffectType(EffectType.HEAL))
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
			player.useMagic(skill, false, false);
			return true;
		}
		return false;
	}

	/**
	 * Returns {@code true} if the bot has at least one damage skill ready to cast at its current target.
	 * Does not cast anything — use as a world-state fact check.
	 *
	 * @param bot the bot to check
	 * @return {@code true} if a usable damage skill exists
	 */
	public static boolean hasDamageSkill(BotInstance bot)
	{
		return getBestDamageSkill(bot) != null;
	}

	/**
	 * Returns the highest-level damage skill available for the bot's current target,
	 * or {@code null} if none is ready.
	 *
	 * @param bot the attacking bot
	 * @return best ready damage skill, or {@code null}
	 */
	public static Skill getBestDamageSkill(BotInstance bot)
	{
		final Player player = bot.getPlayer();
		if ((bot.getTarget() == null) || bot.getTarget().isDead())
		{
			return null;
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
		return best;
	}

	/**
	 * Tries to cast the best available damage skill at the bot's current target.
	 *
	 * @param bot the attacking bot
	 * @return {@code true} if a skill was cast, {@code false} if nothing was available
	 */
	public static boolean tryDamageSkill(BotInstance bot)
	{
		final Skill best = getBestDamageSkill(bot);
		if (best == null)
		{
			return false;
		}
		bot.getPlayer().useMagic(best, false, false);
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
