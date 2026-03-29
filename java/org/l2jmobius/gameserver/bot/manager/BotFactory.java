/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.manager;

import java.util.logging.Logger;

import org.l2jmobius.gameserver.bot.core.model.BotInstance;
import org.l2jmobius.gameserver.bot.core.model.BotProfile;
import org.l2jmobius.gameserver.bot.core.model.BotRole;
import org.l2jmobius.gameserver.bot.core.service.EquipService;
import org.l2jmobius.gameserver.bot.core.service.GearService;
import org.l2jmobius.gameserver.bot.core.service.PotionData;
import org.l2jmobius.gameserver.bot.core.service.SkillService;
import org.l2jmobius.gameserver.bot.core.service.SupplyService;
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
	 * Gives the bot a free initial stock of shots, arrows and potions at spawn.
	 * Uses REWARD (no adena cost) — this is a one-time startup gift, not a purchase.
	 */
	private static void giveInitialSupplies(BotInstance bot)
	{
		final Player player = bot.getPlayer();

		final int shotId = SupplyService.getShotId(bot);
		if (shotId > 0)
		{
			final long current = player.getInventory().getInventoryItemCount(shotId, -1);
			if (current < SupplyService.RESTOCK_THRESHOLD)
			{
				player.getInventory().addItem(ItemProcessType.REWARD, shotId, SupplyService.RESTOCK_TARGET - current, player, null);
				LOGGER.info("BotFactory: gave initial shots id=" + shotId + " to " + player.getName());
			}
		}

		if (bot.getRole() == BotRole.ARCHER)
		{
			// getShotId returns 0 for archers — use weapon grade for arrows via SupplyService helper
			final int arrowId = SupplyService.getArrowId(bot);
			if (arrowId > 0)
			{
				final long current = player.getInventory().getInventoryItemCount(arrowId, -1);
				if (current < SupplyService.RESTOCK_THRESHOLD)
				{
					player.getInventory().addItem(ItemProcessType.REWARD, arrowId, SupplyService.RESTOCK_TARGET - current, player, null);
					LOGGER.info("BotFactory: gave initial arrows id=" + arrowId + " to " + player.getName());
				}
			}
		}

		final long potionCount = player.getInventory().getInventoryItemCount(PotionData.HEAL_POTION_IDS[0], -1);
		if (potionCount < SupplyService.POTION_TARGET)
		{
			player.getInventory().addItem(ItemProcessType.REWARD, PotionData.HEAL_POTION_IDS[0], SupplyService.POTION_TARGET - potionCount, player, null);
			LOGGER.info("BotFactory: gave initial potions id=" + PotionData.HEAL_POTION_IDS[0] + " to " + player.getName());
		}
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
		giveInitialSupplies(bot);

		// Bot spawns at its last saved DB position.
		// ThinkService handles navigation on the first tick:
		//   - in farm zone  → IDLE → starts farming
		//   - outside zone  → RETURNING → walks to farm zone
		//   - at home city  → CITY_IDLE (if cityIdleEndTime > now) or RETURNING

		return bot;
	}
}
