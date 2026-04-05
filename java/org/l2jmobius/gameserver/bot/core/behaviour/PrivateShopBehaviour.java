/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.behaviour;

import java.util.List;
import java.util.logging.Logger;

import org.l2jmobius.gameserver.bot.core.goap.GoapAction;
import org.l2jmobius.gameserver.bot.core.goap.GoalSelector;
import org.l2jmobius.gameserver.bot.core.goap.action.SellItemsGoapAction;
import org.l2jmobius.gameserver.bot.core.goap.goal.RestockGoal;
import org.l2jmobius.gameserver.bot.core.model.BotInstance;
import org.l2jmobius.gameserver.config.custom.BotConfig;

/**
 * Private shop behaviour for PASSIVE bots.
 * <p>
 * The bot stands in city, sells items, and idles.
 * No farming, no combat. After {@link GoapTuning#BEHAVIOUR_SHOP_SESSION_MS}
 * the bot transitions back to {@link CityIdleBehaviour} which may then
 * send it back to farm depending on the rotation logic.
 * <p>
 * TODO: Add OpenPrivateShopGoal + OpenPrivateShopGoapAction when private
 * shop interaction is implemented.
 */
public class PrivateShopBehaviour extends AbstractBotBehaviour
{
	private static final Logger LOGGER = Logger.getLogger(PrivateShopBehaviour.class.getName());

	/** Minimal action set — only selling is supported in this phase. */
	private static final List<GoapAction> ACTIONS = List.of(
		new SellItemsGoapAction());

	/**
	 * Goals while in private shop mode.
	 * RestockGoal (30) handles sell-to-NPC if private shop is not yet open.
	 * TODO: Add OpenPrivateShopGoal with higher priority when implemented.
	 */
	private static final GoalSelector GOAL_SELECTOR = new GoalSelector(List.of(
		new RestockGoal())); // 30 — sell items to NPC

	@Override
	public String getName()
	{
		return "PrivateShop";
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
		startTimer(BotConfig.BOT_SHOP_SESSION_MS);
		LOGGER.info("[" + bot.getPlayer().getName() + "] PrivateShop: session started, duration=" + (BotConfig.BOT_SHOP_SESSION_MS / 60_000) + "min");
	}

	@Override
	public void onExit(BotInstance bot)
	{
		cancelTimer();
	}

	/** When shop session ends, switch back to city idle (which will handle farm return). */
	@Override
	public void onTick(BotInstance bot, long now)
	{
		if (isTimerExpired())
		{
			LOGGER.fine(() -> "[" + bot.getPlayer().getName() + "] PrivateShop: session expired, switching to CityIdle");
			bot.getBehaviourController().transition(new CityIdleBehaviour(), bot, now);
		}
	}
}
