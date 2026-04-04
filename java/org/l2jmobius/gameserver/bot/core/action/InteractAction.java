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
public class InteractAction implements BotAction
{
	private final Creature _target;
	private boolean _done = false;

	public InteractAction(Creature target)
	{
		_target = target;
	}

	@Override
	public void execute(BotInstance bot, long now) throws FatalBotException, ValidationBotException, RecoverableBotException
	{
		if (!_done)
		{
			bot.getPlayer().doInteract(_target);
			_done = true;
		}
	}

	@Override
	public boolean isDone(BotInstance bot, long now)
	{
		return _done;
	}
}
