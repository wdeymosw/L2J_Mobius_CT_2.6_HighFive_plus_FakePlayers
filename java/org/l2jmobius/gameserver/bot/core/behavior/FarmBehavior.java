/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.behavior;

import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

import org.l2jmobius.gameserver.bot.core.action.AttackAction;
import org.l2jmobius.gameserver.bot.core.action.MoveToAction;
import org.l2jmobius.gameserver.bot.core.action.WaitAction;
import org.l2jmobius.gameserver.bot.core.brain.BotIntention;
import org.l2jmobius.gameserver.bot.core.model.BotInstance;
import org.l2jmobius.gameserver.bot.core.model.BotContext;
import org.l2jmobius.gameserver.bot.core.model.BotPhase;
import org.l2jmobius.gameserver.bot.core.model.BotState;
import org.l2jmobius.gameserver.bot.core.service.BuffService;
import org.l2jmobius.gameserver.bot.core.service.EquipService;
import org.l2jmobius.gameserver.bot.core.service.LevelUpService;
import org.l2jmobius.gameserver.bot.core.service.LootService;
import org.l2jmobius.gameserver.bot.core.service.SellService;
import org.l2jmobius.gameserver.bot.core.service.ShopService;
import org.l2jmobius.gameserver.bot.core.service.SupplyService;
import org.l2jmobius.gameserver.bot.core.service.TargetService;
import org.l2jmobius.gameserver.config.custom.BotConfig;
import org.l2jmobius.gameserver.model.Location;
import org.l2jmobius.gameserver.model.actor.Creature;

/**
 * Behavioral style for ACTIVE (farming) bots.
 * <p>
 * Handles two concerns:
 * <ul>
 *   <li>{@link #think} — receives a {@link BotIntention} and queues the appropriate actions.</li>
 *   <li>{@link #onArrived} — handles city/farm arrival: sell, equip, restock, rest, or resume farming.</li>
 * </ul>
 * Does NOT change {@code BotState} directly — state transitions belong to
 * {@link org.l2jmobius.gameserver.bot.core.BotController}.
 * Exception: {@code onArrived} sets phase and state to transition into the next strategic phase.
 */
public class FarmBehavior implements BotBehavior
{
	private static final Logger LOGGER = Logger.getLogger(FarmBehavior.class.getName());

	@Override
	public void think(BotInstance bot, BotIntention intention, long now)
	{
		switch (intention)
		{
			case APPROACH_TARGET:
			{
				final Creature target = bot.getTarget();
				if ((target == null) || target.isDead())
				{
					return; // target lost between Brain eval and here — Brain fixes next tick
				}

				if (bot.isQueueIdle())
				{
					bot.queueAction(new MoveToAction(target.getX(), target.getY(), target.getZ(), bot.getPhysicalAttackRange()));
					bot.queueAction(new AttackAction());
				}
				break;
			}
			case ATTACK_TARGET:
			{
				if (!bot.hasTarget())
				{
					return; // target lost — Brain fixes next tick
				}

				if (bot.isQueueIdle())
				{
					bot.queueAction(new AttackAction());
				}
				break;
			}
			case SEARCH_TARGET:
			{
				if (!bot.isQueueIdle() || (now < bot.getNextSearchTime()))
				{
					break;
				}

				// 1. self-buff
				if (BuffService.tryBuffSelf(bot))
				{
					break;
				}

				// 2. loot
				if (LootService.pickupNearest(bot))
				{
					break;
				}

				// 3. find target
				TargetService.findTarget(bot);
				if (bot.hasTarget())
				{
					final Creature target = bot.getTarget();
					bot.clearQueue();
					bot.queueAction(new MoveToAction(target.getX(), target.getY(), target.getZ(), bot.getPhysicalAttackRange()));
					bot.queueAction(new AttackAction());
					break;
				}

				// 4. wander
				wander(bot, now);
				break;
			}
			case IDLE:
			default:
			{
				if (bot.isQueueIdle())
				{
					bot.queueAction(new WaitAction(2000 + ThreadLocalRandom.current().nextLong(2000)));
				}
				break;
			}
		}
	}

	/**
	 * Called when the bot arrives at its travel destination.
	 * <ul>
	 *   <li>Arriving at farm ({@code TRAVELING_OUT}): resume farming.</li>
	 *   <li>Arriving in city ({@code TRAVELING_BACK}): sell → equip → restock → levelUp → openShop → rest.</li>
	 * </ul>
	 *
	 * @param bot the bot that arrived
	 * @param ctx perception snapshot for this tick
	 * @param now current time in ms
	 */
	@Override
	public void onArrived(BotInstance bot, BotContext ctx, long now)
	{
		if (bot.getPhase() == BotPhase.TRAVELING_OUT)
		{
			bot.setPhase(BotPhase.FARMING);
			bot.setState(BotState.SEARCH_TARGET);
		}
		else
		{
			// Arrived in city: run the city pipeline.
			SellService.sell(bot);
			EquipService.equip(bot);
			SupplyService.restock(bot);
			LevelUpService.checkAndUpgrade(bot);
			ShopService.openShop(bot);

			// Enter resting phase.
			final long minMs = BotConfig.BOT_CITY_IDLE_MIN_SECONDS * 1000L;
			final long maxMs = BotConfig.BOT_CITY_IDLE_MAX_SECONDS * 1000L;
			final long idleMs = minMs + ThreadLocalRandom.current().nextLong(Math.max(1, maxMs - minMs));
			bot.setStateEndTime(now + idleMs);
			bot.setPhase(BotPhase.RESTING);
			bot.setState(BotState.IDLE);
			LOGGER.info("FarmBehavior: " + bot.getPlayer().getName() + " REST for " + (idleMs / 60_000) + " min");
		}
	}

	// -------------------------------------------------------------------------

	private static void wander(BotInstance bot, long now)
	{
		final Location wander = bot.getProfile().getZone().randomPointInside();
		bot.queueAction(new WaitAction(2000));
		bot.queueAction(new MoveToAction(wander));
		bot.setNextSearchTime(now + 4000);
	}
}
