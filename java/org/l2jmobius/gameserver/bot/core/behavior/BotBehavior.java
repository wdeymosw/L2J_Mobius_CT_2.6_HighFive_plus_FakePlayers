/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.behavior;

import org.l2jmobius.gameserver.bot.core.brain.BotDecision;
import org.l2jmobius.gameserver.bot.core.model.BotInstance;

/**
 * Strategy for bot tick-level behavior, selected by {@link org.l2jmobius.gameserver.bot.core.model.BotType}.
 * <p>
 * Receives the current {@link BotDecision} from BotController and queues
 * the appropriate actions. Must not call enterTravel or enterResting —
 * cross-cutting state transitions are owned by BotController.
 */
public interface BotBehavior
{
	void think(BotInstance bot, BotDecision decision, long now);
}
