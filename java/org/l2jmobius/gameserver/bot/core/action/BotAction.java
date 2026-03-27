/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.action;

import org.l2jmobius.gameserver.bot.core.model.BotInstance;

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
	 */
	void execute(BotInstance bot, long now);

	/**
	 * @param bot the bot executing the action
	 * @param now current time in ms
	 * @return {@code true} when this action has finished and can be removed
	 */
	boolean isDone(BotInstance bot, long now);
}
