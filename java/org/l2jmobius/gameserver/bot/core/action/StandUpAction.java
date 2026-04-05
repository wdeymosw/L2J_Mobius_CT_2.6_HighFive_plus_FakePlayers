/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.action;

import org.l2jmobius.gameserver.ai.Intention;
import org.l2jmobius.gameserver.bot.core.exception.FatalBotException;
import org.l2jmobius.gameserver.bot.core.exception.RecoverableBotException;
import org.l2jmobius.gameserver.bot.core.exception.ValidationBotException;
import org.l2jmobius.gameserver.bot.core.goap.GoapTuning;
import org.l2jmobius.gameserver.bot.core.model.BotInstance;
import org.l2jmobius.gameserver.model.actor.Player;

/**
 * Tells the bot to stand up from a sitting position.
 * <p>
 * Uses the same manual state reset as {@code SitRestGoapAction} to avoid
 * triggering combat-state checks that {@code player.standUp()} may fire.
 */
public class StandUpAction extends AbstractStateWaitAction
{
	@Override
	protected void onFirstExecute(BotInstance bot, long now) throws FatalBotException, ValidationBotException, RecoverableBotException
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
	protected boolean isStateReached(BotInstance bot, long now)
	{
		return !bot.getPlayer().isSitting();
	}

	@Override
	protected long getTimeoutMs()
	{
		return GoapTuning.ACTION_STAND_TIMEOUT_MS;
	}
}

