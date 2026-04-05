/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.behaviour;

import java.util.List;
import java.util.logging.Logger;

import org.l2jmobius.gameserver.bot.core.goap.GoapAction;
import org.l2jmobius.gameserver.bot.core.goap.GoalSelector;
import org.l2jmobius.gameserver.bot.core.goap.action.AttackGoapAction;
import org.l2jmobius.gameserver.bot.core.goap.action.DrinkPotionGoapAction;
import org.l2jmobius.gameserver.bot.core.goap.action.MoveToTargetGoapAction;
import org.l2jmobius.gameserver.bot.core.goap.action.PickupLootGoapAction;
import org.l2jmobius.gameserver.bot.core.goap.action.RestockGoapAction;
import org.l2jmobius.gameserver.bot.core.goap.action.SearchTargetGoapAction;
import org.l2jmobius.gameserver.bot.core.goap.action.SellItemsGoapAction;
import org.l2jmobius.gameserver.bot.core.goap.action.SitRestGoapAction;
import org.l2jmobius.gameserver.bot.core.goap.action.TeleportToCityGoapAction;
import org.l2jmobius.gameserver.bot.core.goap.action.TeleportToFarmGoapAction;
import org.l2jmobius.gameserver.bot.core.goap.action.UseBuffSkillGoapAction;
import org.l2jmobius.gameserver.bot.core.goap.action.UseDamageSkillGoapAction;
import org.l2jmobius.gameserver.bot.core.goap.action.UseHealSkillGoapAction;
import org.l2jmobius.gameserver.bot.core.goap.goal.BuffGoal;
import org.l2jmobius.gameserver.bot.core.goap.goal.DefendGoal;
import org.l2jmobius.gameserver.bot.core.goap.goal.DrinkPotionGoal;
import org.l2jmobius.gameserver.bot.core.goap.goal.FarmGoal;
import org.l2jmobius.gameserver.bot.core.goap.goal.HuntGoal;
import org.l2jmobius.gameserver.bot.core.goap.goal.PickupGoal;
import org.l2jmobius.gameserver.bot.core.goap.goal.RestockGoal;
import org.l2jmobius.gameserver.bot.core.goap.goal.RestoreGoal;
import org.l2jmobius.gameserver.bot.core.goap.goal.SurviveGoal;
import org.l2jmobius.gameserver.bot.core.goap.goal.WanderGoal;
import org.l2jmobius.gameserver.bot.core.model.BotInstance;
import org.l2jmobius.gameserver.config.custom.BotConfig;

/**
 * PvE farming behaviour — the full active combat loop.
 * <p>
 * Contains the complete action and goal set that was previously hardcoded
 * as static fields in {@code GoapAgent}. Used by ACTIVE bots in or heading
 * to the farm zone.
 * <p>
 * The action and goal lists are static (shared read-only data).
 * Per-bot state (session timer) lives in the instance fields of
 * {@link AbstractBotBehaviour}.
 * <p>
 * <b>Session timer:</b> after {@link GoapTuning#BEHAVIOUR_PVE_SESSION_MS} the bot
 * transitions to {@link CityIdleBehaviour} for restocking.
 */
public class PveBehaviour extends AbstractBotBehaviour
{
	private static final Logger LOGGER = Logger.getLogger(PveBehaviour.class.getName());

	/** All available GOAP actions for PvE. Shared, stateless singletons. */
	private static final List<GoapAction> ACTIONS = List.of(
		new UseDamageSkillGoapAction(), // 0.8 — skill attack (preferred over autoattack)
		new AttackGoapAction(),         // 1.0 — autoattack fallback
		new MoveToTargetGoapAction(),
		new SearchTargetGoapAction(),
		new UseBuffSkillGoapAction(),
		new PickupLootGoapAction(),
		new DrinkPotionGoapAction(),
		new UseHealSkillGoapAction(),
		new SitRestGoapAction(),
		new TeleportToFarmGoapAction(),
		new TeleportToCityGoapAction(),
		new SellItemsGoapAction(),
		new RestockGoapAction());

	/** Goals evaluated each replan cycle, in priority order. */
	private static final GoalSelector GOAL_SELECTOR = new GoalSelector(List.of(
		new SurviveGoal(),      // 100 — HP critical
		new DefendGoal(),       //  90 — under attack
		new DrinkPotionGoal(),  //  52 — HP low + potion ready (in and out of combat)
		new FarmGoal(),         //  50 — kill existing target
		new BuffGoal(),         //  47 — apply pending self-buffs
		new PickupGoal(),       //  46 — collect nearby loot
		new HuntGoal(),         //  45 — find a target (in zone, no target)
		new RestoreGoal(),      //  40 — HP/MP low (sit rest / heal skill)
		new RestockGoal(),      //  30 — out of supplies
		new WanderGoal()));     //  10 — fallback: get to farm zone

	@Override
	public String getName()
	{
		return "PvE";
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
		startTimer(BotConfig.BOT_PVE_SESSION_MS);
		LOGGER.info("[" + bot.getPlayer().getName() + "] PvE: session started, duration=" + (BotConfig.BOT_PVE_SESSION_MS / 60_000) + "min");
	}

	@Override
	public void onExit(BotInstance bot)
	{
		cancelTimer();
	}

	/** When the PvE session timer expires, transition to city for restocking. */
	@Override
	public void onTick(BotInstance bot, long now)
	{
		if (isTimerExpired())
		{
			LOGGER.info("[" + bot.getPlayer().getName() + "] PvE: session expired → CityIdle (FOR_PASSIVE)");
			bot.getBehaviourController().transition(new CityIdleBehaviour(CityIdleBehaviour.Mode.FOR_PASSIVE), bot, now);
		}
	}
}
