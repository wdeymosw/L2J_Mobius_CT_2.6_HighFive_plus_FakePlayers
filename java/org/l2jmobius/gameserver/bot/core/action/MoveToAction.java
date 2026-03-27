/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.action;

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
	private static final int ARRIVAL_RADIUS = 150;

	private final Location _dest;

	public MoveToAction(Location dest)
	{
		_dest = dest;
	}

	public MoveToAction(int x, int y, int z)
	{
		_dest = new Location(x, y, z);
	}

	@Override
	public void execute(BotInstance bot, long now)
	{
		PathService.thinkMove(bot, _dest.getX(), _dest.getY(), _dest.getZ());
	}

	@Override
	public boolean isDone(BotInstance bot, long now)
	{
		return bot.getPlayer().isInsideRadius2D(_dest.getX(), _dest.getY(), _dest.getZ(), ARRIVAL_RADIUS);
	}
}
