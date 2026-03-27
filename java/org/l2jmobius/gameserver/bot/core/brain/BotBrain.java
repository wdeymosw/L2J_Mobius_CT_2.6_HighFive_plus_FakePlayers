/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.brain;

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
	public BotDecision decide(BotContext ctx, BotState state)
	{
		// Retreat — highest priority, overrides everything
		if (ctx.lowHp || ctx.inventoryFull || ctx.outOfAmmo)
		{
			return BotDecision.RETREAT;
		}

		// Not in farm zone — handled outside Brain (out-of-zone check in update loop)

		// TRAVELING or RESTING — do not interrupt with new combat decisions
		if ((state == BotState.TRAVELING) || (state == BotState.RESTING))
		{
			return BotDecision.IDLE;
		}

		// Valid target → attack
		if (ctx.hasTarget())
		{
			return BotDecision.ATTACK_TARGET;
		}

		// Default: look for something to do
		return BotDecision.SEARCH_TARGET;
	}
}
