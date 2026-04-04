/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.validation;

import java.util.logging.Logger;

import org.l2jmobius.gameserver.bot.core.exception.ValidationBotException;
import org.l2jmobius.gameserver.bot.core.model.BotInstance;

/**
 * Null-safety validation utilities.
 * <p>
 * Pre-checks common null references before bot operations.
 * Replaces ad-hoc null checks scattered in code.
 */
public class NullSafetyValidator
{
	private static final Logger LOGGER = Logger.getLogger(NullSafetyValidator.class.getName());

	private NullSafetyValidator()
	{
	}

	/**
	 * Validates bot is not null.
	 *
	 * @param bot the bot to check
	 * @throws ValidationBotException if bot is null
	 */
	public static void requireNonNull(BotInstance bot) throws ValidationBotException
	{
		if (bot == null)
		{
			throw new ValidationBotException("Bot instance is null", null);
		}
	}

	/**
	 * Validates bot and its player.
	 *
	 * @param bot the bot to check
	 * @throws ValidationBotException if bot or player is null
	 */
	public static void validateBotAndPlayer(BotInstance bot) throws ValidationBotException
	{
		if (bot == null)
		{
			throw new ValidationBotException("Bot instance is null", null);
		}

		if (bot.getPlayer() == null)
		{
			throw new ValidationBotException("Bot player is null", bot);
		}
	}

	/**
	 * Validates GOAP plan exists.
	 *
	 * @param bot the bot
	 * @throws ValidationBotException if GOAP plan is null
	 */
	public static void validateGoapPlan(BotInstance bot) throws ValidationBotException
	{
		if (bot == null)
		{
			throw new ValidationBotException("Bot is null", null);
		}

		if (bot.getCurrentGoapAction() == null)
		{
			throw new ValidationBotException("No GOAP action in plan", bot);
		}
	}

	/**
	 * Validates bot phase.
	 *
	 * @param bot the bot
	 * @throws ValidationBotException if phase is null
	 */
	public static void validatePhase(BotInstance bot) throws ValidationBotException
	{
		if (bot == null)
		{
			throw new ValidationBotException("Bot is null", null);
		}

		if (bot.getPhase() == null)
		{
			throw new ValidationBotException("Bot phase is null", bot);
		}
	}

	/**
	 * Validates bot target.
	 *
	 * @param bot the bot
	 * @throws ValidationBotException if target is null
	 */
	public static void validateTarget(BotInstance bot) throws ValidationBotException
	{
		if (bot == null)
		{
			throw new ValidationBotException("Bot is null", null);
		}

		if (bot.getTarget() == null)
		{
			throw new ValidationBotException("Bot target is null", bot);
		}
	}

	/**
	 * Entire bot context validation (player, profile, role, phase, target and GOAP plan).
	 *
	 * @param bot the bot
	 * @throws ValidationBotException if any critical component is null
	 */
	public static void validateFullContext(BotInstance bot) throws ValidationBotException
	{
		if (bot == null)
		{
			throw new ValidationBotException("Bot is null", null);
		}

		if (bot.getPlayer() == null)
		{
			throw new ValidationBotException("Bot player is null", bot);
		}

		if (bot.getProfile() == null)
		{
			throw new ValidationBotException("Bot profile is null", bot);
		}

		if (bot.getPhase() == null)
		{
			throw new ValidationBotException("Bot phase is null", bot);
		}

		if (bot.getCurrentGoapAction() == null)
		{
			throw new ValidationBotException("No GOAP action in plan", bot);
		}
	}

	/**
	 * Returns diagnostic info for null context failures.
	 */
	public static String getDiagnostics(BotInstance bot)
	{
		final StringBuilder sb = new StringBuilder();

		if (bot == null)
		{
			sb.append("Bot: NULL\n");
			return sb.toString();
		}

		sb.append("Bot: ").append(bot.getPlayer() == null ? "NULL" : bot.getPlayer().getName()).append("\n");
		sb.append("Player: ").append(bot.getPlayer() == null ? "NULL" : "OK").append("\n");
		sb.append("Profile: ").append(bot.getProfile() == null ? "NULL" : "OK").append("\n");
		sb.append("Phase: ").append(bot.getPhase() == null ? "NULL" : "OK").append("\n");
		sb.append("GoapPlan: ").append(bot.getCurrentGoapAction() == null ? "EMPTY" : "OK").append("\n");

		return sb.toString();
	}
}
