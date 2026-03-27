/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.brain;

import java.util.concurrent.ThreadLocalRandom;

import org.l2jmobius.gameserver.bot.core.BotController;
import org.l2jmobius.gameserver.bot.core.action.AttackAction;
import org.l2jmobius.gameserver.bot.core.action.MoveToAction;
import org.l2jmobius.gameserver.bot.core.action.TravelReason;
import org.l2jmobius.gameserver.bot.core.action.WaitAction;
import org.l2jmobius.gameserver.bot.core.model.BotInstance;
import org.l2jmobius.gameserver.bot.core.model.BotState;
import org.l2jmobius.gameserver.bot.core.service.EquipService;
import org.l2jmobius.gameserver.bot.core.service.LootService;
import org.l2jmobius.gameserver.bot.core.service.SellService;
import org.l2jmobius.gameserver.bot.core.service.SkillService;
import org.l2jmobius.gameserver.bot.core.service.SupplyService;
import org.l2jmobius.gameserver.bot.core.service.TargetService;
import org.l2jmobius.gameserver.model.Location;

/**
 * Tick behavior for ACTIVE bots: farm mobs, loot, restock, sell.
 * <p>
 * Pipeline per tick:
 * <pre>
 *   BotBrain.decide → apply decision → executor.tick → onQueueCompleted
 * </pre>
 */
public class ActiveBehavior implements BotBehavior
{
	@Override
	public void think(BotInstance bot, BotContext ctx, long now)
	{
		final BotDecision decision = BotBrain.decide(ctx, bot.getState());
		apply(bot, ctx, decision, now);
		bot.getExecutor().tick(bot, now);
		if (bot.getExecutor().isIdle())
		{
			onQueueCompleted(bot, ctx, now);
		}
	}

	// -------------------------------------------------------------------------
	// Decision → action
	// -------------------------------------------------------------------------

	private static void apply(BotInstance bot, BotContext ctx, BotDecision decision, long now)
	{
		switch (decision)
		{
			case RETREAT:
			{
				if (bot.getState() != BotState.TRAVELING)
				{
					final TravelReason reason = ctx.lowHp ? TravelReason.RETURN_TO_FARM : TravelReason.GO_TO_CITY;
					BotController.enterTravel(bot, reason, now);
				}
				break;
			}
			case ATTACK_TARGET:
			{
				if (bot.getState() != BotState.ATTACKING)
				{
					bot.setState(BotState.ATTACKING);
					bot.getExecutor().clear();
					bot.getExecutor().add(new AttackAction());
				}
				break;
			}
			case SEARCH_TARGET:
			{
				if (bot.getState() != BotState.SEARCHING)
				{
					bot.setState(BotState.SEARCHING);
					bot.getExecutor().clear();
					bot.setNextSearchTime(0);
				}

				if (bot.getExecutor().isIdle() && (now >= bot.getNextSearchTime()))
				{
					if (!LootService.pickupNearest(bot))
					{
						TargetService.findTarget(bot);
						if (bot.hasTarget())
						{
							bot.setState(BotState.ATTACKING);
							bot.getExecutor().clear();
							bot.getExecutor().add(new AttackAction());
						}
						else
						{
							final Location wander = bot.getProfile().getZone().randomPointInside();
							bot.getExecutor().add(new WaitAction(3000 + ThreadLocalRandom.current().nextLong(3000)));
							bot.getExecutor().add(new MoveToAction(wander));
							bot.setNextSearchTime(now + 5000 + ThreadLocalRandom.current().nextLong(5000));
						}
					}
				}
				break;
			}
			case IDLE:
			default:
			{
				if (bot.getExecutor().isIdle())
				{
					bot.getExecutor().add(new WaitAction(2000 + ThreadLocalRandom.current().nextLong(2000)));
				}
				break;
			}
		}
	}

	// -------------------------------------------------------------------------
	// Queue completion
	// -------------------------------------------------------------------------

	private static void onQueueCompleted(BotInstance bot, BotContext ctx, long now)
	{
		switch (bot.getState())
		{
			case ATTACKING:
			{
				bot.clearTarget();
				bot.setState(BotState.SEARCHING);
				bot.setNextSearchTime(now + 1000);
				break;
			}
			case TRAVELING:
			{
				onArrived(bot, ctx, now);
				break;
			}
			default:
				break;
		}
	}

	private static void onArrived(BotInstance bot, BotContext ctx, long now)
	{
		if (bot.getTravelReason() == TravelReason.RETURN_TO_FARM)
		{
			bot.setTravelReason(null);
			bot.setState(BotState.SEARCHING);
		}
		else
		{
			SellService.sell(bot);
			EquipService.equip(bot);
			SupplyService.restock(bot);
			if (bot.hasLeveledUp())
			{
				SkillService.setup(bot);
			}
			BotController.enterResting(bot, now);
		}
	}
}
