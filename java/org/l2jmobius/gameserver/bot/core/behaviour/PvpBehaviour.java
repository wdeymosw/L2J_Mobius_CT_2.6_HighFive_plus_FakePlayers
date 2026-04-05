/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.behaviour;

import java.util.List;
import java.util.logging.Logger;

import org.l2jmobius.gameserver.bot.core.goap.GoapAction;
import org.l2jmobius.gameserver.bot.core.goap.GoalSelector;
import org.l2jmobius.gameserver.bot.core.goap.GoapTuning;
import org.l2jmobius.gameserver.bot.core.goap.action.AttackGoapAction;
import org.l2jmobius.gameserver.bot.core.goap.action.MoveToTargetGoapAction;
import org.l2jmobius.gameserver.bot.core.goap.action.TeleportToCityGoapAction;
import org.l2jmobius.gameserver.bot.core.goap.action.UseDamageSkillGoapAction;
import org.l2jmobius.gameserver.bot.core.goap.goal.DefendGoal;
import org.l2jmobius.gameserver.bot.core.goap.goal.SurviveGoal;
import org.l2jmobius.gameserver.bot.core.model.BotInstance;

/**
 * PvP behaviour — reactive combat against flagged players.
 * <p>
 * Triggered via {@link BotBehaviourEvent#PVP_ATTACKED} dispatched from
 * {@code BotManager} when a rare-aggressor event fires or when the bot
 * is attacked by a flagged player.
 * <p>
 * <b>Auto-exit conditions:</b>
 * <ul>
 *   <li>Timer expires (configurable — default 5 minutes)</li>
 *   <li>HP drops below critical threshold (SurviveGoal fires → city teleport)</li>
 * </ul>
 * After exit the bot returns to whichever behaviour was active before PvP.
 * <p>
 * TODO: Add AttackPlayerGoal when player-targeting logic is implemented.
 */
public class PvpBehaviour extends AbstractBotBehaviour
{
	private static final Logger LOGGER = Logger.getLogger(PvpBehaviour.class.getName());

	/** PvP session duration — 5 minutes before bot retreats to city. */
	private static final long PVP_SESSION_MS = 5 * 60 * 1000L;

	/**
	 * Limited action set for PvP:
	 * damage skills, autoattack, movement, escape teleport.
	 * No farming/loot/rest actions during combat.
	 */
	private static final List<GoapAction> ACTIONS = List.of(
		new UseDamageSkillGoapAction(),
		new AttackGoapAction(),
		new MoveToTargetGoapAction(),
		new TeleportToCityGoapAction());

	/**
	 * Goals during PvP:
	 * SurviveGoal (100) — flee to city if HP critical.
	 * DefendGoal   (90) — engage the attacker.
	 * TODO: Add AttackPlayerGoal (70) when player targeting is implemented.
	 */
	private static final GoalSelector GOAL_SELECTOR = new GoalSelector(List.of(
		new SurviveGoal(),  // 100 — escape if dying
		new DefendGoal())); //  90 — fight the attacker

	/** The behaviour to return to after PvP ends. Stored on enter. */
	private BotBehaviour _previousBehaviour;

	@Override
	public String getName()
	{
		return "PvP";
	}

	@Override
	public GoalSelector getGoalSelector()
	{
		return GOAL_SELECTOR;
	}

	@Override
	public List<GoapAction> getActions()
	{
		return ACTIONS;
	}

	@Override
	public void onEnter(BotInstance bot, long now)
	{
		_previousBehaviour = bot.getBehaviourController().getActive();
		startTimer(PVP_SESSION_MS);
		LOGGER.fine(() -> "[" + bot.getPlayer().getName() + "] PvP: entered, will return to " + (_previousBehaviour != null ? _previousBehaviour.getName() : "PvE") + " after " + PVP_SESSION_MS + "ms");
	}

	@Override
	public void onExit(BotInstance bot)
	{
		cancelTimer();
		_previousBehaviour = null;
	}

	/** When PvP session expires, return to the previous behaviour (or PvE as fallback). */
	@Override
	public void onTick(BotInstance bot, long now)
	{
		if (isTimerExpired())
		{
			LOGGER.fine(() -> "[" + bot.getPlayer().getName() + "] PvP: session expired, returning to previous behaviour");
			final BotBehaviour returnTo = (_previousBehaviour != null) ? _previousBehaviour : new PveBehaviour();
			bot.getBehaviourController().transition(returnTo, bot, now);
		}
	}
}
