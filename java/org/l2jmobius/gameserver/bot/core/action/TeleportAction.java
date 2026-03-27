/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.action;

import org.l2jmobius.gameserver.bot.manager.BotSpawner;
import org.l2jmobius.gameserver.bot.core.model.BotInstance;

/**
 * Instantly teleports the bot to the given coordinates.
 * Used for long-distance travel (farm ↔ city) where path-finding is impractical.
 */
public class TeleportAction implements BotAction
{
	private final int _x;
	private final int _y;
	private final int _z;
	private boolean _done = false;

	public TeleportAction(int x, int y, int z)
	{
		_x = x;
		_y = y;
		_z = z;
	}

	@Override
	public void execute(BotInstance bot, long now)
	{
		BotSpawner.teleportBot(bot, _x, _y, _z);
		_done = true;
	}

	@Override
	public boolean isDone(BotInstance bot, long now)
	{
		return _done;
	}
}
