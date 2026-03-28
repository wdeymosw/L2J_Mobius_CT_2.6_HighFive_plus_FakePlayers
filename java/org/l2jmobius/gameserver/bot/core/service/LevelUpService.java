/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.service;

import java.util.Set;
import java.util.logging.Logger;

import org.l2jmobius.gameserver.bot.core.model.BotInstance;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.enums.player.PlayerClass;
import org.l2jmobius.gameserver.model.item.enums.ItemGrade;

/**
 * Performs automatic class transfers and gear upgrades when a bot reaches the
 * required level.
 * <p>
 * Transfer thresholds (mirrors retail HighFive):
 * <ul>
 *   <li>Level 20 → 1st class transfer → C-grade gear</li>
 *   <li>Level 40 → 2nd class transfer → B-grade gear</li>
 *   <li>Level 76 → 3rd class transfer → A-grade gear</li>
 * </ul>
 * When multiple next-class options exist the first entry from
 * {@link PlayerClass#getNextClasses()} is chosen — sufficient for bot purposes.
 * <p>
 * Call {@link #checkAndUpgrade(BotInstance)} once per city visit from
 * {@code BotController.onArrived}.
 */
public class LevelUpService
{
	private static final Logger LOGGER = Logger.getLogger(LevelUpService.class.getName());

	private static final int FIRST_CLASS_LEVEL  = 20;
	private static final int SECOND_CLASS_LEVEL = 40;
	private static final int THIRD_CLASS_LEVEL  = 76;

	private LevelUpService()
	{
	}

	/**
	 * Called on every city visit.
	 * <ul>
	 *   <li>If the bot has not levelled up since last check — returns immediately.</li>
	 *   <li>If eligible for a class transfer — performs it and upgrades gear.</li>
	 *   <li>Always refreshes skills and auto-shot after a level-up.</li>
	 * </ul>
	 *
	 * @param bot the bot to check
	 */
	public static void checkAndUpgrade(BotInstance bot)
	{
		if (!bot.hasLeveledUp())
		{
			return;
		}

		final Player player = bot.getPlayer();
		final int level = player.getLevel();
		final PlayerClass current = player.getPlayerClass();

		// Try class transfer first.
		final PlayerClass next = findNextClass(current, level);
		if (next != null)
		{
			LOGGER.info("LevelUpService: " + player.getName() + " transferring " + current.name() + " → " + next.name() + " at level " + level);
			player.setPlayerClass(next.getId()); // internally calls rewardSkills()
			GearService.giveGearSet(bot, gradeForClassLevel(next.level()));
			EquipService.equip(bot);
		}

		// Refresh skills and auto-shot (also needed for non-transfer level-ups).
		SkillService.setup(bot);
	}

	// -------------------------------------------------------------------------

	private static PlayerClass findNextClass(PlayerClass current, int level)
	{
		final int threshold;
		switch (current.level())
		{
			case 0: threshold = FIRST_CLASS_LEVEL;  break;
			case 1: threshold = SECOND_CLASS_LEVEL; break;
			case 2: threshold = THIRD_CLASS_LEVEL;  break;
			default: return null; // already at 3rd class
		}

		if (level < threshold)
		{
			return null;
		}

		final Set<PlayerClass> next = current.getNextClasses();
		return next.isEmpty() ? null : next.iterator().next();
	}

	private static ItemGrade gradeForClassLevel(int classLevel)
	{
		switch (classLevel)
		{
			case 3:  return ItemGrade.A;
			case 2:  return ItemGrade.B;
			case 1:  return ItemGrade.C;
			default: return ItemGrade.D;
		}
	}
}
