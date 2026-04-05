/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.behaviour;

import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

import org.l2jmobius.gameserver.bot.core.goap.GoapAction;
import org.l2jmobius.gameserver.bot.core.goap.GoalSelector;
import org.l2jmobius.gameserver.bot.core.model.BotInstance;
import org.l2jmobius.gameserver.bot.core.model.BotRole;

/**
 * Party behaviour — role-based cooperative play.
 * <p>
 * <b>Architecture scaffold: implementation deferred.</b>
 * <p>
 * Triggered via {@link BotBehaviourEvent#PARTY_INVITE} dispatched from
 * {@code BotManager.onPartyInvite(bot, role)}.
 * The event payload carries the assigned {@link BotRole} which determines
 * which goals and actions this behaviour exposes:
 * <ul>
 *   <li>TANK    → AggroGoal, TauntAction (TODO)</li>
 *   <li>HEALER  → HealPartyGoal, UseHealSkillGoapAction (TODO)</li>
 *   <li>BUFFER  → BuffPartyGoal, UseBuffSkillGoapAction (TODO)</li>
 *   <li>DPS     → FarmGoal + full damage action set</li>
 * </ul>
 * <p>
 * Exits on {@link BotBehaviourEvent#PARTY_DISSOLVE} or when the party leader
 * leaves. Returns to PveBehaviour.
 * <p>
 * TODO: Implement goals and actions per BotRole.
 * TODO: Implement party event hooks in BotManager.
 */
public class PartyBehaviour extends AbstractBotBehaviour
{
	private static final Logger LOGGER = Logger.getLogger(PartyBehaviour.class.getName());

	/** The role assigned when joining the party. */
	private BotRole _partyRole = BotRole.UNKNOWN;

	@Override
	public String getName()
	{
		return "Party[" + _partyRole + "]";
	}

	@Override
	public GoalSelector getGoalSelector()
	{
		// TODO: return role-specific GoalSelector
		return new GoalSelector(Collections.emptyList());
	}

	@Override
	public List<GoapAction> getActions()
	{
		// TODO: return role-specific action set
		return Collections.emptyList();
	}

	@Override
	public void onEnter(BotInstance bot, long now)
	{
		LOGGER.info("[" + bot.getPlayer().getName() + "] Party: joined as " + _partyRole);
	}

	@Override
	public void onExit(BotInstance bot)
	{
		LOGGER.info("[" + bot.getPlayer().getName() + "] Party: left/dissolved");
		_partyRole = BotRole.UNKNOWN;
	}

	/**
	 * Sets the party role before this behaviour is activated.
	 * Called by {@code BotManager.onPartyInvite()} before {@code transition()}.
	 */
	public void setPartyRole(BotRole role)
	{
		_partyRole = role;
	}

	public BotRole getPartyRole()
	{
		return _partyRole;
	}
}
