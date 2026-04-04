/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.validation;

import java.util.logging.Logger;

import org.l2jmobius.gameserver.bot.core.exception.ValidationBotException;
import org.l2jmobius.gameserver.bot.core.model.BotContext;
import org.l2jmobius.gameserver.bot.core.model.BotInstance;

/**
 * Runtime precondition checks for bot actions.
 * <p>
 * Validates bot state before executing an action:
 * <ul>
 *   <li>Player alive</li>
 *   <li>Inventory has space (or not full for certain actions)</li>
 *   <li>Target alive and in range (if needed)</li>
 *   <li>Resources available (potions, ammo, etc.)</li>
 * </ul>
 * <p>
 * Complements GOAP world state facts with runtime checks.
 * Throws {@link ValidationBotException} when preconditions fail.
 */
public class PreconditionValidator
{
	private static final Logger LOGGER = Logger.getLogger(PreconditionValidator.class.getName());

	private PreconditionValidator()
	{
	}

	/**
	 * Checks that the bot is alive and in a valid state to act.
	 *
	 * @param bot the bot to check
	 * @throws ValidationBotException if bot is dead or invalid
	 */
	public static void checkBotAlive(BotInstance bot) throws ValidationBotException
	{
		if (bot.isDead())
		{
			throw new ValidationBotException("Bot is dead, cannot execute action", bot);
		}

		if (bot.getPlayer() == null)
		{
			throw new ValidationBotException("Bot has no player reference", bot);
		}

		if (bot.getPlayer().getLevel() < 1)
		{
			throw new ValidationBotException("Bot player has invalid level " + bot.getPlayer().getLevel(), bot);
		}
	}

	/**
	 * Checks that the target is alive and exists.
	 *
	 * @param bot the bot
	 * @param ctx current context snapshot
	 * @throws ValidationBotException if target is dead or missing
	 */
	public static void checkTargetAlive(BotInstance bot, BotContext ctx) throws ValidationBotException
	{
		if (!ctx.hasTarget())
		{
			throw new ValidationBotException("Target is dead or missing, cannot attack", bot);
		}
	}

	/**
	 * Checks that the target is in physical attack range.
	 * <p>
	 * Uses the bot's calculated physical attack range (including weapon range).
	 *
	 * @param bot the bot
	 * @param ctx current context snapshot
	 * @throws ValidationBotException if target out of range
	 */
	public static void checkTargetInRange(BotInstance bot, BotContext ctx) throws ValidationBotException
	{
		if (!ctx.canAttackTarget)
		{
			final String msg = "Target out of range (range=" + bot.getPhysicalAttackRange() + ")";
			throw new ValidationBotException(msg, bot);
		}
	}

	/**
	 * Checks that the inventory has space for new items.
	 * <p>
	 * Complements world state {@code INVENTORY_FULL} with runtime check.
	 *
	 * @param bot the bot
	 * @param ctx current context snapshot
	 * @throws ValidationBotException if inventory is full
	 */
	public static void checkInventorySpace(BotInstance bot, BotContext ctx) throws ValidationBotException
	{
		if (ctx.inventoryFull)
		{
			throw new ValidationBotException("Inventory is full (≥90% capacity)", bot);
		}
	}

	/**
	 * Checks that the bot has ammunition/shots available.
	 * <p>
	 * Required before using ranged attacks.
	 *
	 * @param bot the bot
	 * @param ctx current context snapshot
	 * @throws ValidationBotException if out of ammo
	 */
	public static void checkHasAmmo(BotInstance bot, BotContext ctx) throws ValidationBotException
	{
		if (ctx.outOfAmmo)
		{
			throw new ValidationBotException("Out of ammunition or arrows", bot);
		}
	}

	/**
	 * Checks that the bot is not under heavy weight penalty.
	 * <p>
	 * Used for movement actions to prevent slow/overloaded behavior.
	 *
	 * @param bot the bot
	 * @param ctx current context snapshot
	 * @throws ValidationBotException if severely overweight
	 */
	public static void checkWeightOkay(BotInstance bot, BotContext ctx) throws ValidationBotException
	{
		if (ctx.weightPenalty >= 3)
		{
			throw new ValidationBotException("Bot is severely overloaded (weight penalty 3)", bot);
		}
	}

	/**
	 * Checks that bot is in its assigned farm zone.
	 *
	 * @param bot the bot
	 * @param ctx current context snapshot
	 * @throws ValidationBotException if out of zone
	 */
	public static void checkInZone(BotInstance bot, BotContext ctx) throws ValidationBotException
	{
		if (!ctx.isInFarmZone)
		{
			throw new ValidationBotException("Bot is outside assigned farm zone", bot);
		}
	}

	/**
	 * Checks that potion reuse timer is ready.
	 * <p>
	 * Required before drinking potions.
	 *
	 * @param bot the bot
	 * @param ctx current context snapshot
	 * @throws ValidationBotException if potion still on cooldown
	 */
	public static void checkPotionReady(BotInstance bot, BotContext ctx) throws ValidationBotException
	{
		if (!ctx.potionReuseReady)
		{
			throw new ValidationBotException("Potion is still on cooldown", bot);
		}
	}

	/**
	 * Checks that the bot is not under attack (or attacks are minimal).
	 * <p>
	 * Used for non-combat actions like buffs or restocking.
	 *
	 * @param bot the bot
	 * @param ctx current context snapshot
	 * @throws ValidationBotException if heavily attacked
	 */
	public static void checkNotUnderAttack(BotInstance bot, BotContext ctx) throws ValidationBotException
	{
		if (ctx.attackerCount > 2)
		{
			throw new ValidationBotException("Bot is under heavy attack (" + ctx.attackerCount + " attackers), cannot do this action", bot);
		}
	}

	/**
	 * Checks that HP is above the minimum threshold for the action.
	 * <p>
	 * Example: cannot meditate for recovery if HP is critically low.
	 *
	 * @param bot the bot
	 * @param ctx current context snapshot
	 * @param minHpPercent minimum HP percentage required (0–100)
	 * @throws ValidationBotException if HP too low
	 */
	public static void checkHpAbove(BotInstance bot, BotContext ctx, double minHpPercent) throws ValidationBotException
	{
		if (ctx.hpPercent < minHpPercent)
		{
			throw new ValidationBotException("HP is too low (" + ctx.hpPercent + "%, need " + minHpPercent + "%)", bot);
		}
	}

	/**
	 * Checks that HP is below the maximum threshold for the action.
	 * <p>
	 * Example: cannot rest if HP is full (BotPhase.RESTING only if HP ≤ 80%).
	 *
	 * @param bot the bot
	 * @param ctx current context snapshot
	 * @param maxHpPercent maximum HP percentage allowed (0–100)
	 * @throws ValidationBotException if HP too high
	 */
	public static void checkHpBelow(BotInstance bot, BotContext ctx, double maxHpPercent) throws ValidationBotException
	{
		if (ctx.hpPercent > maxHpPercent)
		{
			throw new ValidationBotException("HP is too high (" + ctx.hpPercent + "%, need ≤ " + maxHpPercent + "%)", bot);
		}
	}

	/**
	 * Checks that MP is above the minimum threshold for the action.
	 * <p>
	 * Example: cannot cast a skill if MP is too low.
	 *
	 * @param bot the bot
	 * @param ctx current context snapshot
	 * @param minMpPercent minimum MP percentage required (0–100)
	 * @throws ValidationBotException if MP too low
	 */
	public static void checkMpAbove(BotInstance bot, BotContext ctx, double minMpPercent) throws ValidationBotException
	{
		if (ctx.mpPercent < minMpPercent)
		{
			throw new ValidationBotException("MP is too low (" + ctx.mpPercent + "%, need " + minMpPercent + "%)", bot);
		}
	}
}
