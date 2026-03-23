/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.service;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import org.l2jmobius.gameserver.bot.model.BotInstance;
import org.l2jmobius.gameserver.bot.model.BotRole;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.item.enums.BodyPart;
import org.l2jmobius.gameserver.model.item.enums.ItemGrade;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.model.item.type.WeaponType;
import org.l2jmobius.gameserver.model.itemcontainer.Inventory;

/**
 * Equips the best available gear from the bot's inventory at spawn.
 * <p>
 * Selection criteria:
 * <ul>
 *   <li>Score = {@code ItemGrade.ordinal() * 100 + enchantLevel} — grade is dominant.</li>
 *   <li>For weapons, only types valid for the bot's role are considered.</li>
 *   <li>Each paperdoll slot is processed independently; a candidate is equipped
 *       only when its score exceeds the currently equipped item.</li>
 * </ul>
 * Only the most common slots are handled (weapon, shield, armour, jewellery).
 * Talismans, agathions and pet items are skipped.
 */
public class EquipService
{
	private static final Logger LOGGER = Logger.getLogger(EquipService.class.getName());

	/**
	 * Maps a BodyPart to the canonical paperdoll slot index used for comparison.
	 * Special multi-slot body parts (LR_HAND, FULL_ARMOR, etc.) map to their
	 * primary slot so they can be compared against currently-equipped items.
	 */
	private static final Map<BodyPart, Integer> EFFECTIVE_SLOT = new HashMap<>();
	static
	{
		// Standard one-to-one mappings (paperdollSlot >= 0) are resolved at runtime
		// via BodyPart.getPaperdollSlot(). Only special cases need explicit entries.
		EFFECTIVE_SLOT.put(BodyPart.LR_HAND,    Inventory.PAPERDOLL_RHAND);
		EFFECTIVE_SLOT.put(BodyPart.FULL_ARMOR,  Inventory.PAPERDOLL_CHEST);
		EFFECTIVE_SLOT.put(BodyPart.ALLDRESS,    Inventory.PAPERDOLL_CHEST);
		EFFECTIVE_SLOT.put(BodyPart.LR_EAR,      Inventory.PAPERDOLL_REAR);
		EFFECTIVE_SLOT.put(BodyPart.LR_FINGER,   Inventory.PAPERDOLL_RFINGER);
		EFFECTIVE_SLOT.put(BodyPart.HAIRALL,     Inventory.PAPERDOLL_HAIR);
	}

	private EquipService()
	{
	}

	/**
	 * Scans the bot's inventory and equips the best item in every valid slot.
	 * Safe to call multiple times — e.g. at spawn and again before opening a
	 * private shop, so any looted upgrades get equipped before selling starts.
	 *
	 * @param bot the bot to equip
	 */
	public static void equip(BotInstance bot)
	{
		final Player player = bot.getPlayer();

		// slot index → best candidate found so far
		final Map<Integer, Item> best = new HashMap<>();

		for (Item item : player.getInventory().getItems())
		{
			if (item.isEquipped() || !item.getTemplate().isEquipable())
			{
				continue;
			}

			final BodyPart bodyPart = item.getTemplate().getBodyPart();
			if (bodyPart == BodyPart.NONE || bodyPart == BodyPart.DECO)
			{
				continue; // skip talismans and non-equipable items
			}

			// For weapons, only accept types appropriate for the bot's role.
			if (item.isWeapon() && !isRoleWeapon(bot.getRole(), (WeaponType) item.getItemType()))
			{
				continue;
			}

			final int slot = effectiveSlot(bodyPart);
			if (slot < 0)
			{
				continue; // pet items and other unmapped slots
			}

			final Item current = best.get(slot);
			if ((current == null) || (score(item) > score(current)))
			{
				best.put(slot, item);
			}
		}

		// Equip each best candidate if it beats the currently worn item.
		for (Map.Entry<Integer, Item> entry : best.entrySet())
		{
			final int slot = entry.getKey();
			final Item candidate = entry.getValue();
			final Item equipped = player.getInventory().getPaperdollItem(slot);

			if ((equipped == null) || (score(candidate) > score(equipped)))
			{
				player.useEquippableItem(candidate, false);
				LOGGER.info("EquipService: " + player.getName()
					+ " equipped " + candidate.getTemplate().getName()
					+ " +" + candidate.getEnchantLevel()
					+ " [" + candidate.getTemplate().getItemGrade() + "]");
			}
		}
	}

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	/**
	 * Higher is better. Grade dominates; enchant is the tiebreaker.
	 * ItemGrade ordinals: NONE=0, D=1, C=2, B=3, A=4, S=5.
	 * @param item the item to score
	 * @return numeric score
	 */
	private static int score(Item item)
	{
		final ItemGrade grade = item.getTemplate().getItemGrade();
		return (grade == null ? 0 : grade.ordinal()) * 100 + item.getEnchantLevel();
	}

	/**
	 * Returns the canonical paperdoll slot index for a body part.
	 * Returns -1 for slots we intentionally skip (pets, etc.).
	 * @param bp the body part
	 * @return paperdoll slot index, or -1
	 */
	private static int effectiveSlot(BodyPart bp)
	{
		final Integer override = EFFECTIVE_SLOT.get(bp);
		if (override != null)
		{
			return override;
		}
		return bp.getPaperdollSlot(); // -1 for unmapped specials
	}

	/**
	 * Returns {@code true} if the given weapon type is appropriate for the role.
	 * Prevents mages picking up swords or warriors equipping staves.
	 * @param role the bot role
	 * @param type the weapon type
	 * @return true if this weapon type is valid for the role
	 */
	private static boolean isRoleWeapon(BotRole role, WeaponType type)
	{
		switch (role)
		{
			case ARCHER:
				return type == WeaponType.BOW || type == WeaponType.CROSSBOW;

			case MAGE:
			case HEALER:
			case BUFFER:
			case SUMMONER:
				// Mage classes use blunt (staffs/rods) or ancient sword.
				return type == WeaponType.BLUNT || type == WeaponType.ANCIENTSWORD;

			case MELEE:
			case TANK:
			case CRAFTER:
			default:
				// Physical melee: any weapon except ranged.
				return type != WeaponType.BOW && type != WeaponType.CROSSBOW && type != WeaponType.FISHINGROD;
		}
	}
}
