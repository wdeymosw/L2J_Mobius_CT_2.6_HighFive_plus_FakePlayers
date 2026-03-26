/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core;

import java.util.logging.Logger;

import org.l2jmobius.gameserver.bot.model.BotInstance;
import org.l2jmobius.gameserver.bot.model.BotProfile;
import org.l2jmobius.gameserver.bot.service.EquipService;
import org.l2jmobius.gameserver.bot.service.GearService;
import org.l2jmobius.gameserver.bot.service.SkillService;
import org.l2jmobius.gameserver.bot.service.SupplyService;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;

/**
 * Creates {@link BotInstance} objects from {@link BotProfile} templates.
 * <p>
 * This is the only place in the bot layer that calls {@code Player.load()}.
 * All other components receive a ready-made BotInstance.
 */
public class BotFactory
{
	private static final Logger LOGGER = Logger.getLogger(BotFactory.class.getName());

	private BotFactory()
	{
	}

	/**
	 * Loads a Player from the database and wraps it into a BotInstance.
	 * Sets client to null so all outgoing packets are silently discarded.
	 * Teleports the bot to the city home location and starts a brief city idle.
	 *
	 * @param profile the bot profile containing the character objectId
	 * @return a ready BotInstance, or null if the character could not be loaded
	 */
	public static BotInstance create(BotProfile profile)
	{
		final Player player = Player.load(profile.getCharacterObjectId());
		if (player == null)
		{
			LOGGER.warning("BotFactory: failed to load character objectId=" + profile.getCharacterObjectId());
			return null;
		}

		// No real network connection — packets are discarded by Player.sendPacket() null-check.
		player.setClient(null);

		// Bots always run.
		player.setRunning();

		final BotInstance bot = new BotInstance(player, profile);

		// One-time setup for brand-new bot characters (no weapon equipped = fresh).
		if (player.getInventory().getPaperdollItem(org.l2jmobius.gameserver.model.itemcontainer.Inventory.PAPERDOLL_RHAND) == null)
		{
			player.addAdena(ItemProcessType.REWARD, 50_000, null, false);
			LOGGER.info("BotFactory: gave starter 50k adena to " + player.getName());
			GearService.giveStarterGear(bot);
			LOGGER.info("BotFactory: gave starter D-gear to " + player.getName());
		}

		EquipService.equip(bot);
		SkillService.setup(bot);
		SupplyService.giveInitialSupplies(bot);

		// Bot spawns at its last saved DB position.
		// ThinkService handles navigation on the first tick:
		//   - in farm zone  → IDLE → starts farming
		//   - outside zone  → RETURNING → walks to farm zone
		//   - at home city  → CITY_IDLE (if cityIdleEndTime > now) or RETURNING

		return bot;
	}
}
