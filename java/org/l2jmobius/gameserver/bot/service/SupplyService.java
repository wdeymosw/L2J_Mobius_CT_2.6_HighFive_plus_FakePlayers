/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.service;

import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

import org.l2jmobius.gameserver.bot.model.BotInstance;
import org.l2jmobius.gameserver.bot.model.BotRole;
import org.l2jmobius.gameserver.bot.model.BotState;
import org.l2jmobius.gameserver.config.custom.BotConfig;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.item.enums.ItemGrade;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.model.itemcontainer.Inventory;

/**
 * Handles consumable restocking for bots (shots, arrows, potions).
 * <p>
 * Buying is simulated directly — items are added to the inventory and adena is
 * deducted without requiring a real NPC interaction. This keeps the service
 * simple and reliable; a full NPC-walk flow can be layered on top later.
 * <p>
 * After restocking, the bot enters {@link BotState#CITY_IDLE} for a random
 * 5–10 minute window before heading back to the farming zone.
 */
public class SupplyService
{
	private static final Logger LOGGER = Logger.getLogger(SupplyService.class.getName());

	// --- Shot item IDs by grade (index = ItemGrade.ordinal()) ---
	// NONE=0, D=1, C=2, B=3, A=4, S=5
	private static final int[] SOULSHOT_IDS    = { 1835, 1463, 1464, 1465, 1466, 1467 };
	private static final int[] SPIRITSHOT_IDS  = { 2509, 2510, 2511, 2512, 2513, 2514 };

	// Arrows: NONE→wooden(17), D→bone(1341), C→steel(1342), B→silver(1343), A→mithril(1344), S→shining(1345)
	private static final int[] ARROW_IDS       = { 17, 1341, 1342, 1343, 1344, 1345 };

	/** Restock when remaining quantity drops below this threshold. */
	private static final long RESTOCK_THRESHOLD = 500;

	/** Target quantity to maintain after restocking. */
	private static final long RESTOCK_TARGET = 3000;

	// City idle duration is read from BotConfig at runtime (BotCityIdleMinSeconds / BotCityIdleMaxSeconds).

	private SupplyService()
	{
	}

	/**
	 * Returns {@code true} if the bot needs to restock consumables.
	 * Checked each tick so the bot can interrupt farming when supplies run out.
	 *
	 * @param bot the bot to check
	 * @return true if shots or arrows are below the restock threshold
	 */
	public static boolean needsRestock(BotInstance bot)
	{
		final Player player = bot.getPlayer();
		final int shotId = getShotId(bot);
		if (shotId > 0)
		{
			return player.getInventory().getInventoryItemCount(shotId, -1) < RESTOCK_THRESHOLD;
		}
		// Archers check arrows.
		if (bot.getRole() == BotRole.ARCHER)
		{
			final int arrowId = getArrowId(bot);
			if (arrowId > 0)
			{
				return player.getInventory().getInventoryItemCount(arrowId, -1) < RESTOCK_THRESHOLD;
			}
		}
		return false;
	}

	/**
	 * Buys consumables up to {@code RESTOCK_TARGET} and transitions the bot to
	 * {@link BotState#CITY_IDLE} with a random 5–10 minute wait.
	 *
	 * @param bot the bot to restock
	 */
	public static void restock(BotInstance bot)
	{
		final Player player = bot.getPlayer();
		boolean bought = false;

		// --- Shots / Spiritshots ---
		final int shotId = getShotId(bot);
		if (shotId > 0)
		{
			bought |= buyUpTo(player, shotId, RESTOCK_TARGET);
		}

		// --- Arrows (archers only) ---
		if (bot.getRole() == BotRole.ARCHER)
		{
			final int arrowId = getArrowId(bot);
			if (arrowId > 0)
			{
				bought |= buyUpTo(player, arrowId, RESTOCK_TARGET);
			}
		}

		if (bought)
		{
			LOGGER.info("SupplyService: " + player.getName() + " restocked consumables.");
		}

		final long minMs = BotConfig.BOT_CITY_IDLE_MIN_SECONDS * 1000L;
		final long maxMs = BotConfig.BOT_CITY_IDLE_MAX_SECONDS * 1000L;
		final long idleMs = minMs + ThreadLocalRandom.current().nextLong(Math.max(1, maxMs - minMs));
		bot.setCityIdleEndTime(System.currentTimeMillis() + idleMs);
		bot.setState(BotState.CITY_IDLE);
		LOGGER.info("SupplyService: " + player.getName() + " heading to city, idle for " + (idleMs / 60000) + " min.");
	}

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	/**
	 * Buys enough of the given item to bring the player's stock up to {@code target}.
	 * Deducts adena proportionally to the item's reference price.
	 *
	 * @param player the buyer
	 * @param itemId the item to buy
	 * @param target desired quantity after purchase
	 * @return true if at least one item was purchased
	 */
	private static boolean buyUpTo(Player player, int itemId, long target)
	{
		final long current = player.getInventory().getInventoryItemCount(itemId, -1);
		if (current >= target)
		{
			return false;
		}

		long needed = target - current;
		final long priceEach = getPrice(itemId);
		if (priceEach > 0)
		{
			final long canAfford = player.getAdena() / priceEach;
			needed = Math.min(needed, canAfford);
		}

		if (needed <= 0)
		{
			return false;
		}

		final long cost = needed * priceEach;
		if (cost > 0)
		{
			player.reduceAdena(ItemProcessType.BUY, cost, null, false);
		}
		player.getInventory().addItem(ItemProcessType.BUY, itemId, needed, player, null);
		return true;
	}

	/**
	 * Returns the appropriate shot item ID for the bot's role and equipped weapon grade.
	 * Public so other services (e.g. SkillService) can enable auto-shot for the same item.
	 * Returns 0 if no shot applies (e.g. unarmed or unknown role).
	 *
	 * @param bot the bot
	 * @return shot item ID, or 0
	 */
	public static int getShotId(BotInstance bot)
	{
		final int gradeIdx = weaponGradeIndex(bot);
		if (gradeIdx < 0)
		{
			return 0;
		}
		switch (bot.getRole())
		{
			case MAGE:
			case HEALER:
			case SUMMONER:
			case BUFFER:
				return SPIRITSHOT_IDS[gradeIdx];
			case MELEE:
			case TANK:
			case CRAFTER:
				return SOULSHOT_IDS[gradeIdx];
			default:
				return 0;
		}
	}

	/**
	 * Returns the arrow item ID matching the equipped bow's grade.
	 * Returns 0 if the bot isn't an archer or has no bow equipped.
	 *
	 * @param bot the bot
	 * @return arrow item ID, or 0
	 */
	private static int getArrowId(BotInstance bot)
	{
		final int gradeIdx = weaponGradeIndex(bot);
		return gradeIdx >= 0 ? ARROW_IDS[gradeIdx] : 0;
	}

	/**
	 * Returns the ordinal of the equipped weapon's {@link ItemGrade}, or 0 (no-grade)
	 * if no weapon is equipped.
	 *
	 * @param bot the bot
	 * @return grade ordinal (0–5)
	 */
	private static int weaponGradeIndex(BotInstance bot)
	{
		final Item weapon = bot.getPlayer().getInventory().getPaperdollItem(Inventory.PAPERDOLL_RHAND);
		if (weapon == null)
		{
			return 0; // no-grade fallback
		}
		final ItemGrade grade = weapon.getTemplate().getItemGrade();
		return grade == null ? 0 : grade.ordinal();
	}

	/**
	 * Returns the reference (NPC shop) price of the given item, or 0 if unknown.
	 *
	 * @param itemId the item ID
	 * @return price in adena
	 */
	private static long getPrice(int itemId)
	{
		final org.l2jmobius.gameserver.model.item.ItemTemplate template =
			org.l2jmobius.gameserver.data.xml.ItemData.getInstance().getTemplate(itemId);
		return template != null ? template.getReferencePrice() : 0;
	}
}
