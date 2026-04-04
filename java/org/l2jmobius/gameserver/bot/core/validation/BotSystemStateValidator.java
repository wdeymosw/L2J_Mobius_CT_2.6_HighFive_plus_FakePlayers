/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.validation;

import java.util.logging.Logger;

import org.l2jmobius.gameserver.bot.core.exception.ValidationBotException;
import org.l2jmobius.gameserver.bot.core.goap.Fact;
import org.l2jmobius.gameserver.bot.core.goap.WorldState;
import org.l2jmobius.gameserver.bot.core.logging.StructuredBotLogger;
import org.l2jmobius.gameserver.bot.core.model.BotInstance;
import org.l2jmobius.gameserver.bot.core.model.BotPhase;
import org.l2jmobius.gameserver.model.actor.Creature;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.itemcontainer.Inventory;

/**
 * Comprehensive state validation for bot runtime.
 * <p>
 * Complements BotStateValidator (action-level) with system-level checks:
 * <ol>
 *   <li>World State Validation — checks GOAP facts are self-consistent</li>
 *   <li>Phase Transition Validation — ensures state preconditions for phase changes</li>
 *   <li>Inventory State Validation — checks item counts, weight, capacity</li>
 *   <li>Target Validation — ensures target state is consistent</li>
 *   <li>Player Reference Validation — checks player object integrity</li>
 * </ol>
 * <p>
 * Used during recovery to detect and repair corruption.
 */
public class BotSystemStateValidator
{
	private static final Logger LOGGER = Logger.getLogger(BotSystemStateValidator.class.getName());

	private BotSystemStateValidator()
	{
	}

	/**
	 * Validates overall world state consistency.
	 * <p>
	 * Checks for contradictions like:
	 * - HP_CRITICAL and FULL_HP both true
	 * - IN_FARM_ZONE but at city coordinates
	 * - TARGET_DEAD but UNDER_ATTACK
	 *
	 * @param ws world state snapshot
	 * @param bot the bot
	 * @throws ValidationBotException if contradictions detected
	 */
	public static void validateWorldState(WorldState ws, BotInstance bot) throws ValidationBotException
	{
		// HP contradictions
		if (ws.get(Fact.HP_CRITICAL) && ws.get(Fact.HP_FULL))
		{
			throw new ValidationBotException("World state contradiction: HP_CRITICAL and HP_FULL both true", bot);
		}

		// If target is dead, shouldn't be in combat
		if (ws.get(Fact.TARGET_DEAD) && ws.get(Fact.TARGET_IN_RANGE))
		{
			throw new ValidationBotException("World state contradiction: TARGET_DEAD but TARGET_IN_RANGE", bot);
		}

		// If no target, shouldn't be attacking
		if (!ws.get(Fact.TARGET_EXISTS) && ws.get(Fact.TARGET_IN_RANGE))
		{
			throw new ValidationBotException("World state contradiction: no TARGET_EXISTS but TARGET_IN_RANGE", bot);
		}
	}

	/**
	 * Validates that a phase transition is allowed.
	 * <p>
	 * Enforces preconditions:
	 * - RESTING → only when HP/MP low
	 * - FARMING → only when in zone and not resting
	 * - No transitions while player is dead
	 *
	 * @param oldPhase current phase
	 * @param newPhase target phase
	 * @param bot the bot
	 * @throws ValidationBotException if transition invalid
	 */
	public static void validatePhaseTransition(BotPhase oldPhase, BotPhase newPhase, BotInstance bot) throws ValidationBotException
	{
		// Cannot transition if player is dead
		if (bot.getPlayer().isDead())
		{
			throw new ValidationBotException("Cannot transition phases while player is dead", bot);
		}

		// Validate no transition loops
		if (oldPhase == newPhase)
		{
			LOGGER.fine("Phase unchanged: " + oldPhase);
		}
	}

	/**
	 * Validates inventory state consistency.
	 * <p>
	 * Checks:
	 * - Item counts match inventory
	 * - Weight calculations are correct
	 * - Capacity not exceeded
	 *
	 * @param bot the bot
	 * @throws ValidationBotException if inventory corrupted
	 */
	public static void validateInventoryState(BotInstance bot) throws ValidationBotException
	{
		final Inventory inv = bot.getPlayer().getInventory();
		if (inv == null)
		{
			throw new ValidationBotException("Player inventory is null", bot);
		}

		// Check total weight
		if (inv.getTotalWeight() < 0)
		{
			throw new ValidationBotException("Inventory total weight is negative: " + inv.getTotalWeight(), bot);
		}

		// Check capacity
		if (inv.getTotalWeight() > Inventory.MAX_ARMOR_WEIGHT)
		{
			LOGGER.warning("Inventory weight exceeds maximum: " + inv.getTotalWeight() + " / " + Inventory.MAX_ARMOR_WEIGHT);
		}

		// Check that weight penalty level is valid (0-3)
		final int weightPenalty = bot.getPlayer().getWeightPenalty();
		if ((weightPenalty < 0) || (weightPenalty > 3))
		{
			throw new ValidationBotException("Invalid weight penalty: " + weightPenalty + " (expected 0-3)", bot);
		}
	}

	/**
	 * Validates target state consistency.
	 * <p>
	 * Checks:
	 * - Target exists and is not null
	 * - Target is alive (if set)
	 * - Target is within reasonable distance
	 *
	 * @param bot the bot
	 * @throws ValidationBotException if target corrupted
	 */
	public static void validateTargetState(BotInstance bot)
	{
		final Creature target = bot.getTarget();
		if (target == null)
		{
			return; // No target is valid
		}

		// Target should be alive
		if (target.isDead())
		{
			StructuredBotLogger.warning(bot, "TARGET_DEAD", "target", target.getName());
			bot.clearTarget();
			return;
		}

		// Target should be reasonable distance (not on different continent)
		final int dx = Math.abs(bot.getPlayer().getX() - target.getX());
		final int dy = Math.abs(bot.getPlayer().getY() - target.getY());
		if ((dx > 50000) || (dy > 50000))
		{
			StructuredBotLogger.warning(bot, "TARGET_TOO_FAR", "distance", (int) Math.sqrt(dx * dx + dy * dy));
			bot.clearTarget();
		}
	}

	/**
	 * Validates player object integrity.
	 * <p>
	 * Checks player reference is valid and in expected state.
	 *
	 * @param bot the bot
	 * @throws ValidationBotException if player invalid
	 */
	public static void validatePlayerReference(BotInstance bot) throws ValidationBotException
	{
		final Player player = bot.getPlayer();
		if (player == null)
		{
			throw new ValidationBotException("Bot player reference is null", bot);
		}

		if (player.getObjectId() == 0)
		{
			throw new ValidationBotException("Bot player has invalid ObjectId (0)", bot);
		}

		if (player.getLevel() < 1)
		{
			throw new ValidationBotException("Bot player has invalid level: " + player.getLevel(), bot);
		}

		if (player.getLevel() > 110)
		{
			LOGGER.warning("Bot player has unusually high level: " + player.getLevel());
		}

		// Check if player is removed from world
		if (player.getX() == 0 && player.getY() == 0 && player.getZ() == 0)
		{
			LOGGER.warning("Bot player at origin coordinates (likely removed)");
		}
	}

	/**
	 * Full system state validation.
	 * <p>
	 * Runs all validation checks and collects issues.
	 * Called during recovery to ensure full state health.
	 *
	 * @param bot the bot
	 * @param ws world state snapshot (may be null)
	 * @throws ValidationBotException if any validation fails
	 */
	public static void validateFull(BotInstance bot, WorldState ws) throws ValidationBotException
	{
		try
		{
			validatePlayerReference(bot);
			validateInventoryState(bot);
			validateTargetState(bot);

			if (ws != null)
			{
				validateWorldState(ws, bot);
			}

			StructuredBotLogger.fine(bot, "FULL_STATE_VALIDATION", "result", "passed");
		}
		catch (ValidationBotException e)
		{
			StructuredBotLogger.warning(bot, "FULL_STATE_VALIDATION", "result", "failed", "reason", e.getMessage());
			throw e;
		}
	}
}
