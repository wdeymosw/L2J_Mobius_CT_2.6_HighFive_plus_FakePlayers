/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.behaviour;

import java.util.List;

import org.l2jmobius.gameserver.bot.core.goap.GoapAction;
import org.l2jmobius.gameserver.bot.core.goap.GoalSelector;
import org.l2jmobius.gameserver.bot.core.model.BotInstance;

/**
 * Strategic behaviour module. Provides the GOAP goals and actions available
 * for a specific bot mode (PvE farming, city idle, private shop, PvP, party).
 * <p>
 * Only one behaviour is active at a time. Active behaviour is managed by
 * {@link BehaviourController}. GoapAgent reads goals and actions from it
 * each tick — no static hardcoding.
 * <p>
 * <b>Stateless data</b> (goals, actions lists) may be shared across bot instances
 * via static fields. <b>Stateful data</b> (timers, flags) must be per-instance fields.
 */
public interface BotBehaviour
{
	/** Human-readable name used in logs and debug output. */
	String getName();

	/**
	 * GOAP goal selector for this behaviour.
	 * Evaluated each replan cycle and during interrupt checks.
	 */
	GoalSelector getGoalSelector();

	/**
	 * GOAP actions available in this behaviour.
	 * GoapAgent filters this list to only valid actions before planning.
	 */
	List<GoapAction> getActions();

	/**
	 * Called when this behaviour becomes active.
	 * Use to start timers or initialize per-entry state.
	 */
	void onEnter(BotInstance bot, long now);

	/**
	 * Called when another behaviour takes over.
	 * Use to cancel timers and clean up any persistent game state.
	 */
	void onExit(BotInstance bot);

	/**
	 * Called every tick by {@link BehaviourController}.
	 * Check timers here and call {@code bot.getBehaviourController().transition(...)}
	 * when a transition condition is met.
	 */
	void onTick(BotInstance bot, long now);

	/**
	 * Returns {@code true} while this behaviour is running a direct executor script
	 * (e.g. city walk sequence) and GOAP replanning should be suppressed.
	 * <p>
	 * When {@code true}, {@link org.l2jmobius.gameserver.bot.core.model.BotInstance#update}
	 * will tick the executor directly instead of delegating to GoapAgent.
	 */
	default boolean isScripted()
	{
		return false;
	}
}
