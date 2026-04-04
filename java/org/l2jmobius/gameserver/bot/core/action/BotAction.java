/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.action;

import org.l2jmobius.gameserver.bot.core.model.BotInstance;
import org.l2jmobius.gameserver.bot.core.exception.FatalBotException;
import org.l2jmobius.gameserver.bot.core.exception.ValidationBotException;
import org.l2jmobius.gameserver.bot.core.exception.RecoverableBotException;

/**
 * Atomic action executed by {@link org.l2jmobius.gameserver.bot.core.brain.BotExecutor}.
 * <p>
 * Each action runs every tick until {@link #isDone} returns {@code true},
 * at which point the executor removes it from the queue and starts the next one.
 */
public interface BotAction
{
	/**
	 * Performs one tick of work for this action.
	 *
	 * @param bot the bot executing the action
	 * @param now current time in ms
	 * @throws FatalBotException if action encounters fatal error
	 * @throws ValidationBotException if action state becomes invalid
	 * @throws RecoverableBotException if action encounters temporary error
	 */
	void execute(BotInstance bot, long now) throws FatalBotException, ValidationBotException, RecoverableBotException;

	/**
	 * @param bot the bot executing the action
	 * @param now current time in ms
	 * @return {@code true} when this action has finished and can be removed
	 */
	boolean isDone(BotInstance bot, long now);
}
