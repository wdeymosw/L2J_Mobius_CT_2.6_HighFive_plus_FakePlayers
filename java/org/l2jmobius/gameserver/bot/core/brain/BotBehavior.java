/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.brain;

import org.l2jmobius.gameserver.bot.core.model.BotInstance;

/**
 * Strategy for bot tick-level behavior, selected by {@link org.l2jmobius.gameserver.bot.core.model.BotType}.
 * <p>
 * Receives an already-built {@link BotContext} from {@link org.l2jmobius.gameserver.bot.core.BotController}
 * and drives the bot for one tick.
 * <p>
 * Rules: must NOT change {@link org.l2jmobius.gameserver.bot.core.model.BotState} directly —
 * only through BotInstance mutators.
 */
public interface BotBehavior
{
	void think(BotInstance bot, BotContext ctx, long now);
}
