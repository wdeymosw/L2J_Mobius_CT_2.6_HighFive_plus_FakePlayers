/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.behaviour;

import java.util.List;
import java.util.logging.Logger;

import org.l2jmobius.gameserver.bot.core.goap.GoapAction;
import org.l2jmobius.gameserver.bot.core.goap.GoalSelector;
import org.l2jmobius.gameserver.bot.core.goap.action.RestockGoapAction;
import org.l2jmobius.gameserver.bot.core.goap.action.SellItemsGoapAction;
import org.l2jmobius.gameserver.bot.core.goap.action.TeleportToCityGoapAction;
import org.l2jmobius.gameserver.bot.core.goap.action.TeleportToFarmGoapAction;
import org.l2jmobius.gameserver.bot.core.goap.goal.GoToCityGoal;
import org.l2jmobius.gameserver.bot.core.goap.goal.RestockGoal;
import org.l2jmobius.gameserver.bot.core.model.BotInstance;
import org.l2jmobius.gameserver.config.custom.BotConfig;

/**
 * City idle behaviour — handles the bot's rest period in the city.
 * <p>
 * Active while the bot is in the safe zone between farm sessions.
 * Reduced goal/action set: sell items, restock supplies, then return to farm.
 * No combat goals — bot will not try to fight while in city.
 * <p>
 * <b>Session timer:</b> after {@link GoapTuning#BEHAVIOUR_CITY_SESSION_MS} the bot
 * transitions back to {@link PveBehaviour}.
 */
public class CityIdleBehaviour extends AbstractBotBehaviour
{
	private static final Logger LOGGER = Logger.getLogger(CityIdleBehaviour.class.getName());

	/** Slim action set — sell, restock, travel to city, return to farm. */
	private static final List<GoapAction> ACTIONS = List.of(
		new SellItemsGoapAction(),
		new RestockGoapAction(),
		new TeleportToCityGoapAction(),
		new TeleportToFarmGoapAction());

	/**
	 * Goals in city:
	 * RestockGoal  (30) — sell/restock if needed (may also trigger city travel).
	 * GoToCityGoal (20) — travel to safe zone if not already there.
	 */
	private static final GoalSelector GOAL_SELECTOR = new GoalSelector(List.of(
		new RestockGoal(),    // 30 — sell + restock before idling
		new GoToCityGoal())); // 20 — teleport to city if still in farm zone

	@Override
	public String getName()
	{
		return "CityIdle";
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
		startTimer(BotConfig.BOT_CITY_SESSION_MS);
		LOGGER.info("[" + bot.getPlayer().getName() + "] CityIdle: started, duration=" + (BotConfig.BOT_CITY_SESSION_MS / 60_000) + "min");
	}

	@Override
	public void onExit(BotInstance bot)
	{
		cancelTimer();
	}

	/** When the city session timer expires, transition back to PvE farming. */
	@Override
	public void onTick(BotInstance bot, long now)
	{
		if (isTimerExpired())
		{
			LOGGER.info("[" + bot.getPlayer().getName() + "] CityIdle: session expired → PvE");
			bot.getBehaviourController().transition(new PveBehaviour(), bot, now);
		}
	}
}
