/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.brain;

import org.l2jmobius.gameserver.bot.core.model.BotContext;
import org.l2jmobius.gameserver.bot.core.model.BotState;

/**
 * Pure decision engine — maps {@link BotState} + {@link BotContext} to {@link BotIntention}.
 * <p>
 * <b>Critical rules:</b>
 * <ul>
 *   <li>No actions here — only intentions.</li>
 *   <li>No direct access to {@code Player} — only via {@code BotContext}.</li>
 *   <li>No state transitions — BotController owns those.</li>
 * </ul>
 * <p>
 * Brain is only called during the {@link org.l2jmobius.gameserver.bot.core.model.BotPhase#FARMING} phase.
 * Strategic phases (TRAVELING_*, RESTING) are handled by BotController before Brain is invoked.
 *
 * <h3>Decision model</h3>
 * BotController evaluates the environment and sets the correct state <em>before</em> calling Brain.
 * Brain maps state → intention using perception only where needed within that state.
 * <pre>
 *   RETREATING     → RETREAT
 *   DEFENDING      → ATTACK / APPROACH / recovery()  (fight or, if attacker gone, recover)
 *   ATTACK         → ATTACK / APPROACH / SEARCH_TARGET
 *   SEARCH_TARGET  → recovery()
 *   IDLE           → recovery()
 * </pre>
 */
public class BotBrain
{
	/** HP % below which BotController sets RETREATING. Exposed so BotController can mirror it. */
	public static final double LOW_HP_THRESHOLD = 20.0;

	/** HP % below which the bot drinks a potion during recovery. */
	private static final double POTION_HP_THRESHOLD = 60.0;

	/** HP or MP % below which the bot sits down for faster regeneration. */
	private static final double SIT_THRESHOLD = 40.0;

	/**
	 * Maps the bot's current state and perception snapshot to a concrete intention.
	 * State is the primary axis — perception resolves the choice within that state.
	 *
	 * @param perception immutable snapshot of the bot's world state
	 * @param state      current bot state (already updated by BotController this tick)
	 * @return the intention for this tick
	 */
	public static BotIntention decide(BotContext perception, BotState state)
	{
		switch (state)
		{
			// ----------------------------------------------------------------
			// RETREATING — BotController detected a critical condition.
			// Always retreat; BotController will call enterTravel(TRAVELING_BACK).
			// ----------------------------------------------------------------
			case RETREATING:
				return BotIntention.RETREAT;

			// ----------------------------------------------------------------
			// DEFENDING — reacting to an unprovoked attack.
			// Target is already the attacker (set by BotController on entry).
			// Fight until dead; if the attacker escaped, fall through to recovery.
			// ----------------------------------------------------------------
			case DEFENDING:
			{
				if (perception.hasTarget())
				{
					return perception.canAttackTarget ? BotIntention.ATTACK_TARGET : BotIntention.APPROACH_TARGET;
				}
				return recovery(perception);
			}

			// ----------------------------------------------------------------
			// ATTACK — actively fighting a chosen target.
			// ----------------------------------------------------------------
			case ATTACK:
			{
				if (perception.hasTarget())
				{
					return perception.canAttackTarget ? BotIntention.ATTACK_TARGET : BotIntention.APPROACH_TARGET;
				}
				return BotIntention.SEARCH_TARGET;
			}

			// ----------------------------------------------------------------
			// SEARCH_TARGET / IDLE — between fights; full recovery logic.
			// ----------------------------------------------------------------
			case SEARCH_TARGET:
			case IDLE:
			default:
				return recovery(perception);
		}
	}

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	/**
	 * Recovery and search intentions used between fights.
	 * Applies when no active target is present; if a target exists, goes straight to combat.
	 *
	 * @param p current bot perception
	 * @return recovery or combat intention
	 */
	private static BotIntention recovery(BotContext p)
	{
		if (!p.hasTarget())
		{
			if (p.potionReuseReady && (p.hpPercent < POTION_HP_THRESHOLD))
			{
				return BotIntention.USE_POTION;
			}
			if ((p.hpPercent < SIT_THRESHOLD) || (p.mpPercent < SIT_THRESHOLD))
			{
				return BotIntention.RECOVER;
			}
			if (p.hpPercent < 99.0)
			{
				return BotIntention.IDLE; // standing regen
			}
		}
		if (p.hasTarget())
		{
			return p.canAttackTarget ? BotIntention.ATTACK_TARGET : BotIntention.APPROACH_TARGET;
		}
		return BotIntention.SEARCH_TARGET;
	}
}
