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
import org.l2jmobius.gameserver.bot.core.goap.goal.GoToCityGoal;
import org.l2jmobius.gameserver.bot.core.goap.goal.RestockGoal;
import org.l2jmobius.gameserver.bot.core.model.BotInstance;

/**
 * City idle behaviour — the rotation waiting room.
 * <p>
 * Used in two modes:
 * <ul>
 *   <li>{@link Mode#FOR_PASSIVE} — an ACTIVE bot finished its PvE session.
 *       Travels to city, then immediately marks itself {@code readyToPassive}
 *       so BotManager can swap it with a PASSIVE bot wanting to farm.</li>
 *   <li>{@link Mode#FOR_ACTIVE} — a PASSIVE bot finished its shop session.
 *       Travels to city, stands for {@link BotConfig#BOT_CITY_SESSION_MS},
 *       then marks itself {@code readyToActive} so BotManager can swap it
 *       with an ACTIVE bot wanting to rest.</li>
 * </ul>
 * GOAP handles travel: GoToCityGoal → TeleportToCityGoapAction.
 * Once the bot is no longer in farm zone, arrival is detected and the flag is set.
 */
public class CityIdleBehaviour extends AbstractBotBehaviour
{
	private static final Logger LOGGER = Logger.getLogger(CityIdleBehaviour.class.getName());

	/** Controls whether this bot waits to become PASSIVE or ACTIVE after city arrival. */
	public enum Mode
	{
		/** ACTIVE bot arrived in city — waits for manager to assign PASSIVE role. */
		FOR_PASSIVE,
		/** PASSIVE bot arrived in city — stands BotCitySessionMinutes, then waits for ACTIVE role. */
		FOR_ACTIVE
	}

	/** Action set: sell, restock, travel to city. No farm return — manager handles the next step. */
	private static final List<GoapAction> ACTIONS = List.of(
		new SellItemsGoapAction(),
		new RestockGoapAction(),
		new TeleportToCityGoapAction());

	/**
	 * Goals:
	 * RestockGoal  (30) — sell/restock if needed before idling.
	 * GoToCityGoal (20) — travel to safe zone if still in farm zone.
	 */
	private static final GoalSelector GOAL_SELECTOR = new GoalSelector(List.of(
		new RestockGoal(),    // 30 — sell + restock first
		new GoToCityGoal())); // 20 — teleport to city if not already there

	private final Mode _mode;
	/** True once the bot has left the farm zone (arrived in city). */
	private boolean _arrivedInCity = false;

	public CityIdleBehaviour(Mode mode)
	{
		_mode = mode;
	}

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
		_arrivedInCity = false;
		LOGGER.info("[" + bot.getPlayer().getName() + "] CityIdle: entered, mode=" + _mode);
	}

	@Override
	public void onExit(BotInstance bot)
	{
		cancelTimer();
	}

	@Override
	public void onTick(BotInstance bot, long now)
	{
		if (!_arrivedInCity)
		{
			// Detect arrival: bot is no longer inside the farm zone radius.
			if (!bot.isInZone())
			{
				_arrivedInCity = true;
				if (_mode == Mode.FOR_PASSIVE)
				{
					bot.markReadyToPassive(now);
					LOGGER.info("[" + bot.getPlayer().getName() + "] CityIdle: arrived in city → readyToPassive");
				}
				else
				{
					// FOR_ACTIVE: signal immediately — city time was already spent in PrivateShopBehaviour.
					bot.markReadyToActive(now);
					LOGGER.info("[" + bot.getPlayer().getName() + "] CityIdle: arrived in city → readyToActive");
				}
			}
		}
	}
}
