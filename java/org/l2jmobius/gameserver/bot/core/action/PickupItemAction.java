/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.action;

import org.l2jmobius.gameserver.ai.Intention;
import org.l2jmobius.gameserver.bot.core.exception.FatalBotException;
import org.l2jmobius.gameserver.bot.core.exception.RecoverableBotException;
import org.l2jmobius.gameserver.bot.core.exception.ValidationBotException;
import org.l2jmobius.gameserver.bot.core.model.BotInstance;
import org.l2jmobius.gameserver.model.item.instance.Item;

/**
 * Picks up a specific ground item.
 * <p>
 * If the item is within pickup range, {@code doPickupItem} is called immediately.
 * Otherwise the bot moves toward it; on subsequent ticks the action retries
 * until the item is in range or has already been picked up (despawned).
 */
public class PickupItemAction implements BotAction
{
	/** Distance at which doPickupItem is called instead of moving closer. */
	private static final int PICKUP_RADIUS = 70;

	/** Safety timeout to avoid getting stuck chasing a disappearing item. */
	private static final long TIMEOUT_MS = 8000;

	private final Item _item;
	private long _startTime = 0;
	private boolean _done = false;

	public PickupItemAction(Item item)
	{
		_item = item;
	}

	@Override
	public void execute(BotInstance bot, long now) throws FatalBotException, ValidationBotException, RecoverableBotException
	{
		if (_startTime == 0)
		{
			_startTime = now;
		}

		if (!_item.isSpawned())
		{
			_done = true;
			return;
		}

		final double dist = bot.getPlayer().calculateDistance2D(_item);
		if (dist <= PICKUP_RADIUS)
		{
			bot.getPlayer().doPickupItem(_item);
			_done = true;
		}
		else
		{
			bot.getPlayer().getAI().setIntention(Intention.MOVE_TO, _item);
		}
	}

	@Override
	public boolean isDone(BotInstance bot, long now)
	{
		if (_done || !_item.isSpawned())
		{
			return true;
		}
		return (_startTime > 0) && ((now - _startTime) > TIMEOUT_MS);
	}
}
