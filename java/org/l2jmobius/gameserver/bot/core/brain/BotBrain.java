/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.brain;

import org.l2jmobius.gameserver.bot.core.model.BotContext;
import org.l2jmobius.gameserver.bot.core.model.BotState;

/**
 * Pure decision engine — reads {@link BotContext}, returns {@link BotDecision}.
 * <p>
 * <b>Critical rules:</b>
 * <ul>
 *   <li>No actions here — only decisions.</li>
 *   <li>No direct access to {@code Player} — only via {@code BotContext}.</li>
 * </ul>
 */
public class BotBrain
{
	/**
	 * Evaluates the bot's current context and state, and returns what to do next.
	 *
	 * @param ctx   snapshot of the bot's world state
	 * @param state current bot state
	 * @return the decision for this tick
	 */
	public static BotDecision decide(BotContext ctx, BotState state)
{
    if (ctx.lowHp || ctx.inventoryFull || ctx.outOfAmmo || ctx.overweight)
        return BotDecision.RETREAT;

    if ((state == BotState.MOVE_TO_TARGET) || (state == BotState.REST))
        return BotDecision.IDLE;

    if (state == BotState.ATTACK && !ctx.hasTarget())
        return BotDecision.SEARCH_TARGET;

    if (ctx.hasTarget())
    {
        if (!ctx.canAttackTarget)
            return BotDecision.SEARCH_TARGET;

        return BotDecision.ATTACK_TARGET;
    }

    return BotDecision.SEARCH_TARGET;
}
}
