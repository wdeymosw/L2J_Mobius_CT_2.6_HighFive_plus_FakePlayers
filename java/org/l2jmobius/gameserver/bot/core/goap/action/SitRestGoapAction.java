/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.goap.action;

import org.l2jmobius.gameserver.ai.Intention;
import org.l2jmobius.gameserver.bot.core.goap.Fact;
import org.l2jmobius.gameserver.bot.core.goap.GoapAction;
import org.l2jmobius.gameserver.bot.core.goap.WorldState;
import org.l2jmobius.gameserver.bot.core.model.BotContext;
import org.l2jmobius.gameserver.bot.core.model.BotInstance;
import org.l2jmobius.gameserver.model.actor.Player;

/**
 * pre:  IN_COMBAT=false, IS_DEAD=false
 * eff:  HP_FULL=true, MP_FULL=true
 * cost: 10.0  (high — last-resort regen, prefer potions/skills first)
 * <p>
 * Sits the bot down until HP and MP are fully restored, then stands up.
 */
public class SitRestGoapAction implements GoapAction
{
	private static final WorldState PRECONDITIONS = new WorldState();
	private static final WorldState EFFECTS = new WorldState();

	static
	{
		PRECONDITIONS.set(Fact.IN_COMBAT, false);
		PRECONDITIONS.set(Fact.IS_DEAD, false);
		EFFECTS.set(Fact.HP_FULL, true);
		EFFECTS.set(Fact.HP_MID, false);
		EFFECTS.set(Fact.HP_LOW, false);
		EFFECTS.set(Fact.MP_FULL, true);
		EFFECTS.set(Fact.MP_LOW, false);
	}

	@Override
	public WorldState getPreconditions()
	{
		return PRECONDITIONS;
	}

	@Override
	public WorldState getEffects()
	{
		return EFFECTS;
	}

	@Override
	public float getCost(WorldState worldState)
	{
		return 10.0f;
	}

	@Override
	public boolean isValid(BotContext ctx, BotInstance bot)
	{
		return !ctx.hasTarget() && (ctx.attackerCount == 0) && !bot.getPlayer().isDead();
	}

	@Override
	public void activate(BotInstance bot, long now)
	{
		bot.clearQueue();
		final Player player = bot.getPlayer();
		if (!player.isSitting())
		{
			player.sitDown();
		}
	}

	@Override
	public boolean isComplete(BotInstance bot, BotContext ctx, long now)
	{
		if (ctx.hasTarget() || (ctx.attackerCount > 0))
		{
			// Interrupted by combat — force stand up and signal done
			standUp(bot);
			return true;
		}
		if (ctx.hpPercent >= 99.0 && ctx.mpPercent >= 99.0)
		{
			standUp(bot);
			return true;
		}
		return false;
	}

	private static void standUp(BotInstance bot)
	{
		final Player player = bot.getPlayer();
		if (player.isSitting())
		{
			player.setSittingProgress(false);
			player.setSitting(false);
			player.getAI().setIntention(Intention.IDLE);
		}
	}

	@Override
	public String getName()
	{
		return "SitRestGoapAction";
	}
}
