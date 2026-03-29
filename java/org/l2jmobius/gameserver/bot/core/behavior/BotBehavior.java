/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.behavior;

import org.l2jmobius.gameserver.bot.core.brain.BotIntention;
import org.l2jmobius.gameserver.bot.core.model.BotInstance;
import org.l2jmobius.gameserver.bot.core.model.BotContext;

/**
 * Strategic behavioral style of a bot, selected by {@link org.l2jmobius.gameserver.bot.core.model.BotType}.
 * <p>
 * Responsible for two things:
 * <ol>
 *   <li>{@link #think} — queues actions for the current tactical intention.</li>
 *   <li>{@link #onArrived} — handles arrival at the travel destination (city or farm).</li>
 * </ol>
 * Must not change {@code BotState} directly — cross-cutting state transitions belong to BotController.
 */
public interface BotBehavior
{
	/**
	 * Called every tick while in the {@link org.l2jmobius.gameserver.bot.core.model.BotPhase#FARMING} phase.
	 * Queues the appropriate actions for the given intention.
	 *
	 * @param bot       the bot to act on
	 * @param intention tactical intention from BotBrain
	 * @param now       current time in ms
	 */
	void think(BotInstance bot, BotIntention intention, long now);

	/**
	 * Called when the travel queue empties — the bot has arrived at its destination.
	 * The current {@link org.l2jmobius.gameserver.bot.core.model.BotPhase} tells the behavior
	 * whether the bot arrived at the farm ({@code TRAVELING_OUT}) or the city ({@code TRAVELING_BACK}).
	 *
	 * @param bot the bot that arrived
	 * @param ctx perception snapshot for this tick
	 * @param now current time in ms
	 */
	default void onArrived(BotInstance bot, BotContext ctx, long now)
	{
	}
}
