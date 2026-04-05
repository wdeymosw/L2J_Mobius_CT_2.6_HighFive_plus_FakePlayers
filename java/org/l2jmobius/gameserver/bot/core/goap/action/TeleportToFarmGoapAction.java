/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.goap.action;

import org.l2jmobius.gameserver.bot.core.action.MoveToAction;
import org.l2jmobius.gameserver.bot.core.action.TeleportAction;
import org.l2jmobius.gameserver.bot.core.action.WaitAction;
import org.l2jmobius.gameserver.bot.core.goap.Fact;
import org.l2jmobius.gameserver.bot.core.goap.GoapAction;
import org.l2jmobius.gameserver.bot.core.goap.GoapTuning;
import org.l2jmobius.gameserver.bot.core.goap.WorldState;
import org.l2jmobius.gameserver.bot.core.model.BotContext;
import org.l2jmobius.gameserver.bot.core.model.BotInstance;
import org.l2jmobius.gameserver.bot.core.service.ShopService;
import org.l2jmobius.gameserver.bot.core.zone.BotZoneData;
import org.l2jmobius.gameserver.bot.core.zone.FarmZone;
import org.l2jmobius.gameserver.model.Location;

/**
 * pre:  (none — always possible)
 * eff:  IN_FARM_ZONE=true, IN_SAFE_PLACE=false
 * cost: 5.0
 * <p>
 * Walks to the gatekeeper NPC in the city (if configured), waits there briefly
 * to simulate NPC interaction, then teleports to the farm zone center.
 */
public class TeleportToFarmGoapAction implements GoapAction
{
	/**
	 * If the bot is within this distance of the farm zone center, walk directly
	 * instead of routing through the city gatekeeper. This avoids a 40-50km
	 * city round-trip when the bot merely drifted a few steps outside the zone.
	 */
	private static final int MAX_WALK_BACK_DISTANCE = GoapTuning.MAX_WALK_BACK_DISTANCE;

	/** Wait at the gatekeeper before teleporting — simulates NPC interaction. */
	private static final long GATEKEEPER_WAIT_MS = GoapTuning.GATEKEEPER_WAIT_MS;
	/** Short settle pause after arriving in the farm zone. */
	private static final long SETTLE_MS = GoapTuning.TELEPORT_SETTLE_MS;

	private static final WorldState PRECONDITIONS = new WorldState();
	private static final WorldState EFFECTS = new WorldState();

	static
	{
		EFFECTS.set(Fact.IN_FARM_ZONE, true);
		EFFECTS.set(Fact.IN_SAFE_PLACE, false);
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
		return GoapTuning.COST_TELEPORT_FARM;
	}

	@Override
	public boolean isValid(BotContext ctx, BotInstance bot)
	{
		return !ctx.isInFarmZone;
	}

	@Override
	public void activate(BotInstance bot, long now)
	{
		ShopService.closeShop(bot);
		bot.clearQueue();
		bot.clearTarget();
		bot.clearGlobalPath();

		final FarmZone zone = bot.getProfile().getZone();
		final Location farm = zone.getCenter();

		// If the bot is close enough to the zone, just walk back directly.
		// Avoids a 40-50 km city detour when the bot drifted slightly outside zone.
		final int dx = bot.getPlayer().getX() - farm.getX();
		final int dy = bot.getPlayer().getY() - farm.getY();
		final long distSq = (long) dx * dx + (long) dy * dy;
		if (distSq <= (long) MAX_WALK_BACK_DISTANCE * MAX_WALK_BACK_DISTANCE)
		{
			bot.queueAction(new MoveToAction(farm));
			bot.queueAction(new WaitAction(SETTLE_MS));
			return;
		}

		// Far from zone — walk to the gatekeeper NPC (if configured) then teleport.
		final BotZoneData cityData = zone.getCityData();
		if (cityData != null)
		{
			final Location gatekeeper = cityData.getGatekeeper();
			bot.queueAction(new MoveToAction(gatekeeper));
			bot.queueAction(new WaitAction(GATEKEEPER_WAIT_MS));
		}

		// Teleport to the farm zone center and wait for the world to load.
		bot.queueAction(new TeleportAction(farm.getX(), farm.getY(), farm.getZ()));
		bot.queueAction(new WaitAction(SETTLE_MS));
	}

	@Override
	public boolean isComplete(BotInstance bot, BotContext ctx, long now)
	{
		return ctx.isInFarmZone && bot.isQueueIdle();
	}

	@Override
	public String getName()
	{
		return "TeleportToFarmGoapAction";
	}
}

