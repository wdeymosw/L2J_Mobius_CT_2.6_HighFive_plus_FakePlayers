/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.behavior;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.l2jmobius.gameserver.bot.core.action.MoveToAction;
import org.l2jmobius.gameserver.bot.core.action.WaitAction;
import org.l2jmobius.gameserver.bot.core.brain.BotIntention;
import org.l2jmobius.gameserver.bot.core.model.BotInstance;
import org.l2jmobius.gameserver.bot.core.zone.BotZoneData;
import org.l2jmobius.gameserver.model.Location;

/**
 * Behavioral style for PASSIVE bots: walk between city POIs to create ambient population.
 * <p>
 * Passive bots never farm or attack — they exist solely to populate city areas.
 * The bot cycles through available POIs (shop, guildmaster, gatekeeper) with
 * randomised wait times at each stop to mimic human behaviour.
 * <p>
 * State is always kept at {@code IDLE} — the out-of-zone check in BotController
 * only fires for {@code SEARCH_TARGET} and {@code ATTACK} states, so passive bots
 * are never accidentally sent to the farm zone.
 */
public class PassiveBehavior implements BotBehavior
{
	private static final long WALK_WAIT_MIN = 1000;
	private static final long WALK_WAIT_MAX = 3000;
	private static final long STAY_WAIT_MIN = 5_000;
	private static final long STAY_WAIT_MAX = 15_000;

	@Override
	public void think(BotInstance bot, BotIntention intention, long now)
	{
		// All intentions collapse to city-walking; no state transitions ever.
		if (bot.isQueueIdle())
		{
			cityWalk(bot);
		}
	}

	// -------------------------------------------------------------------------

	private static void cityWalk(BotInstance bot)
	{
		final BotZoneData city = bot.getProfile().getZone().getCityData();
		if (city == null)
		{
			bot.queueAction(new WaitAction(STAY_WAIT_MIN + ThreadLocalRandom.current().nextLong(STAY_WAIT_MAX - STAY_WAIT_MIN)));
			return;
		}

		final Location dest = pickPoi(city);
		if (dest == null)
		{
			bot.queueAction(new WaitAction(STAY_WAIT_MIN + ThreadLocalRandom.current().nextLong(STAY_WAIT_MAX - STAY_WAIT_MIN)));
			return;
		}

		bot.queueAction(new WaitAction(WALK_WAIT_MIN + ThreadLocalRandom.current().nextLong(WALK_WAIT_MAX - WALK_WAIT_MIN)));
		bot.queueAction(new MoveToAction(dest));
		bot.queueAction(new WaitAction(STAY_WAIT_MIN + ThreadLocalRandom.current().nextLong(STAY_WAIT_MAX - STAY_WAIT_MIN)));
	}

	private static Location pickPoi(BotZoneData city)
	{
		final List<Location> pois = new ArrayList<>(3);
		if (city.getShop() != null)
		{
			pois.add(city.getShop());
		}
		if (city.getGuildmaster() != null)
		{
			pois.add(city.getGuildmaster());
		}
		if (city.getGatekeeper() != null)
		{
			pois.add(city.getGatekeeper());
		}
		if (pois.isEmpty())
		{
			return null;
		}
		return pois.get(ThreadLocalRandom.current().nextInt(pois.size()));
	}
}
