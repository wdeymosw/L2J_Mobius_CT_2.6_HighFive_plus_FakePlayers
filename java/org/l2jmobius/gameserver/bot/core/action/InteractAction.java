/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.action;

import org.l2jmobius.gameserver.bot.core.exception.FatalBotException;
import org.l2jmobius.gameserver.bot.core.exception.RecoverableBotException;
import org.l2jmobius.gameserver.bot.core.exception.ValidationBotException;
import org.l2jmobius.gameserver.bot.core.model.BotInstance;
import org.l2jmobius.gameserver.model.actor.Creature;

/**
 * Opens a dialog or triggers an interaction with an NPC (shop, quest giver, etc.).
 * <p>
 * {@code doInteract()} is asynchronous — the server processes the interaction
 * event on its own thread. This action fires once and completes immediately;
 * the caller should queue a {@link WaitAction} afterward if a response delay
 * is needed before the next action.
 */
public class InteractAction extends AbstractOneTimeAction
{
	private final Creature _target;

	public InteractAction(Creature target)
	{
		_target = target;
	}

	@Override
	protected void doExecute(BotInstance bot, long now) throws FatalBotException, ValidationBotException, RecoverableBotException
	{
		bot.getPlayer().doInteract(_target);
	}
}

