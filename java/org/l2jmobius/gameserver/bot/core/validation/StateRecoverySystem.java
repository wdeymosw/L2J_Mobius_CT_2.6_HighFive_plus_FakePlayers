/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.validation;

import java.util.logging.Logger;

import org.l2jmobius.gameserver.bot.core.exception.FatalBotException;
import org.l2jmobius.gameserver.bot.core.exception.ValidationBotException;
import org.l2jmobius.gameserver.bot.core.logging.BotMetrics;
import org.l2jmobius.gameserver.bot.core.logging.StructuredBotLogger;
import org.l2jmobius.gameserver.bot.core.model.BotInstance;
import org.l2jmobius.gameserver.bot.core.model.BotPhase;

/**
 * State recovery system for corrupted or stuck bots.
 * <p>
 * Implements recovery strategies:
 * - Clear and reset state
 * - Repair partial corruption
 * - Re-establish valid starting state
 */
public class StateRecoverySystem
{
	private static final Logger LOGGER = Logger.getLogger(StateRecoverySystem.class.getName());

	private StateRecoverySystem()
	{
	}

	/**
	 * Recovers a bot from a corrupted or stuck state.
	 * <p>
	 * Recovery steps:
	 * 1. Clear action queue
	 * 2. Clear GOAP plan
	 * 3. Reset timing fields
	 * 4. Clear/repair targets
	 * 5. Validate player reference
	 * 6. Reset to FARMING phase
	 *
	 * @param bot the bot to recover
	 * @throws ValidationBotException if player reference is null during recovery
	 * @throws FatalBotException if recovery fails
	 */
	public static void recoverBot(BotInstance bot) throws FatalBotException, ValidationBotException
	{
		StructuredBotLogger.info(bot, "BOT_RECOVERY_START");

		try
		{
			// Step 1: Clear all pending actions
			bot.clearQueue();
			StructuredBotLogger.finest(bot, "RECOVERY_STEP", "step", "clear_queue");

			// Step 2: Clear GOAP plan
			bot.clearGoapPlan();
			StructuredBotLogger.finest(bot, "RECOVERY_STEP", "step", "clear_plan");

			// Step 3: Reset timing
			bot.setCurrentActionStartTime(0);
			bot.setCurrentActionTimeoutMs(0);
			StructuredBotLogger.finest(bot, "RECOVERY_STEP", "step", "reset_timing");

			// Step 4: Clear corrupted target
			if (bot.getTarget() != null && bot.getTarget().isDead())
			{
				bot.clearTarget();
				StructuredBotLogger.finest(bot, "RECOVERY_STEP", "step", "clear_dead_target");
			}

			// Step 5: Validate player
			if (bot.getPlayer() == null)
			{
				throw new ValidationBotException("Player reference is null during recovery", bot);
			}
			StructuredBotLogger.finest(bot, "RECOVERY_STEP", "step", "validate_player");

			// Step 6: Reset phase to FARMING
			final BotPhase oldPhase = bot.getPhase();
			if (oldPhase != BotPhase.FARMING)
			{
				bot.setPhase(BotPhase.FARMING);
				StructuredBotLogger.info(bot, "PHASE_TRANSITION", "old_phase", oldPhase, "new_phase", BotPhase.FARMING);
			}

			StructuredBotLogger.info(bot, "BOT_RECOVERY_COMPLETE");
			BotMetrics.recordActionFailed();
		}
		catch (Exception e)
		{
			StructuredBotLogger.severe(bot, "BOT_RECOVERY_FAILED", "reason", e.getMessage());
			throw new FatalBotException("Recovery failed: " + e.getMessage(), bot, e);
		}
	}

	/**
	 * Performs partial recovery (action-level only).
	 * <p>
	 * For use when only the current action is corrupted, not global state.
	 *
	 * @param bot the bot
	 */
	public static void recoverCurrentAction(BotInstance bot)
	{
		StructuredBotLogger.info(bot, "ACTION_RECOVERY_START");

		// Clear current action from queue
		bot.clearQueue();

		// Reset timing for next action
		bot.setCurrentActionStartTime(0);
		bot.setCurrentActionTimeoutMs(0);

		StructuredBotLogger.info(bot, "ACTION_RECOVERY_COMPLETE");
	}

	/**
	 * Emergency reset — clears everything and brings bot to safe state.
	 * <p>
	 * Used when state is severely corrupted.
	 *
	 * @param bot the bot
	 */
	public static void emergencyReset(BotInstance bot)
	{
		StructuredBotLogger.warning(bot, "EMERGENCY_RESET_TRIGGERED");

		try
		{
			// Clear everything
			bot.clearQueue();
			bot.clearGoapPlan();
			bot.clearTarget();
			bot.clearGlobalPath();
			bot.setCurrentActionStartTime(0);
			bot.setCurrentActionTimeoutMs(0);

			// Reset to FARMING
			bot.setPhase(BotPhase.FARMING);

			// Reset stuck detection
			bot.resetStuckCount();

			StructuredBotLogger.info(bot, "EMERGENCY_RESET_COMPLETE");
		}
		catch (Exception e)
		{
			StructuredBotLogger.severe(bot, "EMERGENCY_RESET_FAILED", "reason", e.getMessage());
		}
	}
}
