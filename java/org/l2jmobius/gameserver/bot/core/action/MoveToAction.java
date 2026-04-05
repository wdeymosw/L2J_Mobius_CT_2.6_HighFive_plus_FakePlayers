/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.action;

import org.l2jmobius.gameserver.bot.core.exception.FatalBotException;
import org.l2jmobius.gameserver.bot.core.goap.GoapTuning;
import org.l2jmobius.gameserver.bot.core.exception.RecoverableBotException;
import org.l2jmobius.gameserver.bot.core.exception.ValidationBotException;
import org.l2jmobius.gameserver.bot.core.model.BotInstance;
import org.l2jmobius.gameserver.bot.core.service.PathService;
import org.l2jmobius.gameserver.model.Location;

/**
 * Moves the bot to a specific location using PathService.
 * <p>
 * For short distances (approaching a mob, stepping 2m) and as a
 * building block inside TRAVELING sequences (city walk waypoints).
 */
public class MoveToAction implements BotAction
{
	private static final int ARRIVAL_RADIUS = GoapTuning.ARRIVAL_RADIUS;

	private final Location _dest;
	private final int _arrivalRadius;

	public MoveToAction(Location dest)
	{
		_dest = dest;
		_arrivalRadius = ARRIVAL_RADIUS;
	}

	public MoveToAction(int x, int y, int z)
	{
		_dest = new Location(x, y, z);
		_arrivalRadius = ARRIVAL_RADIUS;
	}

	public MoveToAction(int x, int y, int z, int arrivalRadius)
	{
		_dest = new Location(x, y, z);
		_arrivalRadius = arrivalRadius;
	}

	@Override
	public void execute(BotInstance bot, long now) throws FatalBotException, ValidationBotException, RecoverableBotException
	{
		PathService.thinkMove(bot, _dest.getX(), _dest.getY(), _dest.getZ());
	}

	@Override
	public boolean isDone(BotInstance bot, long now)
	{
		return bot.getPlayer().isInsideRadius2D(_dest.getX(), _dest.getY(), _dest.getZ(), _arrivalRadius);
	}
}
