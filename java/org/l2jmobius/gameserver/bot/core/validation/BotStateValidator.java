/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.validation;

import java.util.logging.Logger;

import org.l2jmobius.gameserver.bot.core.exception.ValidationBotException;
import org.l2jmobius.gameserver.bot.core.goap.GoapAction;
import org.l2jmobius.gameserver.bot.core.goap.WorldState;
import org.l2jmobius.gameserver.bot.core.model.BotInstance;
import org.l2jmobius.gameserver.bot.core.model.BotPhase;

/**
 * Validates bot state and action execution constraints.
 * <p>
 * Performs three types of validation:
 * <ol>
 *   <li><strong>Precondition Check:</strong> Before action.activate(), verify world state matches what the action requires</li>
 *   <li><strong>Effect Check:</strong> After action.isComplete(), verify world state contains what the action promised</li>
 *   <li><strong>Phase Transition Check:</strong> Verify state changes are legal</li>
 * </ol>
 * <p>
 * When validation fails, throws {@link ValidationBotException} which triggers recovery.
 */
public class BotStateValidator
{
	private static final Logger LOGGER = Logger.getLogger(BotStateValidator.class.getName());

	private BotStateValidator()
	{
	}

	/**
	 * Validates that the current world state meets the action's preconditions.
	 * <p>
	 * Called before action.activate() to catch mismatches early.
	 * If preconditions not met, this indicates a planner bug (invalid action selected).
	 *
	 * @param action the action about to execute
	 * @param ws current world state
	 * @param bot the bot
	 * @throws ValidationBotException if preconditions not met
	 */
	public static void validatePreconditions(GoapAction action, WorldState ws, BotInstance bot) throws ValidationBotException
	{
		final WorldState required = action.getPreconditions();

		// Check each fact in preconditions
		for (final var entry : required.getMask().entrySet())
		{
			final var fact = entry.getKey();
			final boolean requiredValue = entry.getValue();
			final boolean actualValue = ws.get(fact);

			if (requiredValue != actualValue)
			{
				final String msg = "Precondition failed for " + action.getName() + ": " + fact + "=" + requiredValue + " (actual=" + actualValue + ")";
				LOGGER.warning(msg);
				throw new ValidationBotException(msg, bot);
			}
		}
	}

	/**
	 * Validates that the world state now contains the action's promised effects.
	 * <p>
	 * Called after action.isComplete() returns true to verify the action did what it claimed.
	 * If effects not present, action may have failed silently or world state corrupted.
	 *
	 * @param action the completed action
	 * @param ws new world state
	 * @param bot the bot
	 * @throws ValidationBotException if effects not present
	 */
	public static void validateEffects(GoapAction action, WorldState ws, BotInstance bot) throws ValidationBotException
	{
		final WorldState promised = action.getEffects();

		// Check each fact in effects
		for (final var entry : promised.getMask().entrySet())
		{
			final var fact = entry.getKey();
			final boolean promisedValue = entry.getValue();
			final boolean actualValue = ws.get(fact);

			if (promisedValue != actualValue)
			{
				final String msg = "Effect failed for " + action.getName() + ": " + fact + "=" + promisedValue + " (actual=" + actualValue + ")";
				LOGGER.warning(msg);
				throw new ValidationBotException(msg, bot);
			}
		}
	}

	/**
	 * Validates that a phase transition is legal.
	 * <p>
	 * Checks state preconditions for the new phase.
	 * For example: cannot go to RESTING if HP is full.
	 *
	 * @param oldPhase previous phase
	 * @param newPhase target phase
	 * @param bot the bot
	 * @throws ValidationBotException if transition not allowed
	 */
	public static void validatePhaseTransition(BotPhase oldPhase, BotPhase newPhase, BotInstance bot) throws ValidationBotException
	{
		// Cannot transition if bot is dead (regardless of old/new phase)
		if (bot.isDead())
		{
			final String msg = "Invalid phase transition: " + oldPhase + " → " + newPhase + " (bot is dead)";
			LOGGER.warning(msg);
			throw new ValidationBotException(msg, bot);
		}

		// Future: Add more phase-specific validations as needed
	}

	/**
	 * Attempts to repair corrupted or invalid state.
	 * <p>
	 * Called from exception handlers to salvage a bot when state validation fails.
	 * Resets critical fields and flags for a fresh replan.
	 *
	 * @param bot the bot to repair
	 */
	public static void repair(BotInstance bot)
	{
		LOGGER.info("Repairing bot state for " + bot.getPlayer().getName());

		// Clear action queue to reset action state machine
		bot.clearQueue();

		// Clear current action timing to force clean action startup
		bot.setCurrentActionStartTime(0);
		bot.setCurrentActionTimeoutMs(0);

		// Clear target if corrupted
		if ((bot.getTarget() != null) && bot.getTarget().isDead())
		{
			bot.clearTarget();
		}

		// Clear path if bot at destination
		if (bot.hasPath())
		{
			final var waypoint = bot.getCurrentWaypoint();
			if (waypoint != null)
			{
				final int dx = Math.abs(bot.getPlayer().getX() - waypoint.getX());
				final int dy = Math.abs(bot.getPlayer().getY() - waypoint.getY());
				if ((dx < 100) && (dy < 100))
				{
					bot.clearPath();
				}
			}
		}

		LOGGER.info("Bot state repaired, ready for replan");
	}
}
