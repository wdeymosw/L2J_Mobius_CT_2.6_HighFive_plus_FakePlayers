/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.behavior;

import java.util.concurrent.ThreadLocalRandom;

import org.l2jmobius.gameserver.bot.core.action.AttackAction;
import org.l2jmobius.gameserver.bot.core.action.MoveToAction;
import org.l2jmobius.gameserver.bot.core.action.WaitAction;
import org.l2jmobius.gameserver.bot.core.brain.BotDecision;
import org.l2jmobius.gameserver.bot.core.model.BotInstance;
import org.l2jmobius.gameserver.bot.core.model.BotState;
import org.l2jmobius.gameserver.bot.core.service.BuffService;
import org.l2jmobius.gameserver.bot.core.service.LootService;
import org.l2jmobius.gameserver.bot.core.service.TargetService;
import org.l2jmobius.gameserver.model.Location;

/**
 * Behavior for ACTIVE (farming) bots.
 * <p>
 * Receives a {@link BotDecision} and queues the appropriate actions.
 * Does not tick the executor, does not manage state transitions beyond
 * setting ATTACK/SEARCH_TARGET when switching modes — cross-cutting
 * transitions (RETREAT, onArrived, onQueueCompleted) are owned by BotController.
 */
public class FarmBehavior implements BotBehavior
{
	@Override
	public void think(BotInstance bot, BotDecision decision, long now)
	{
		switch (decision)
		{
			case ATTACK_TARGET:
			{
				if (!bot.hasTarget())
				{
					bot.setState(BotState.SEARCH_TARGET);
					return;
				}

				if (bot.getState() != BotState.ATTACK)
				{
					bot.setState(BotState.ATTACK);
					bot.clearQueue();
				}

				if (bot.isQueueIdle())
				{
					bot.queueAction(new AttackAction());
				}

				break;
			}
			case SEARCH_TARGET:
			{
				if (bot.getState() != BotState.SEARCH_TARGET)
				{
					bot.setState(BotState.SEARCH_TARGET);
					bot.clearQueue();
					bot.setNextSearchTime(0);
				}

				if (!bot.isQueueIdle() || (now < bot.getNextSearchTime()))
				{
					break;
				}

				// 1. самобафф
				if (BuffService.tryBuffSelf(bot))
				{
					break;
				}

				// 2. лут
				if (LootService.pickupNearest(bot))
				{
					break;
				}

				// 3. таргет
				TargetService.findTarget(bot);
				if (bot.hasTarget())
				{
					bot.setState(BotState.ATTACK);
					bot.clearQueue();
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

	private static void wander(BotInstance bot, long now)
	{
		final Location wander = bot.getProfile().getZone().randomPointInside();
		bot.queueAction(new WaitAction(2000));
		bot.queueAction(new MoveToAction(wander));
		bot.setNextSearchTime(now + 4000);
	}
}
