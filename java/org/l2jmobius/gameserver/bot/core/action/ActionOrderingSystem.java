/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.action;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.logging.Logger;

/**
 * Action queue ordering and prioritization.
 * <p>
 * Reorders action queue based on:
 * - Priority (combat > survival > farming)
 * - Precondition likelihood
 * - Historical success rate
 */
public class ActionOrderingSystem
{
	private static final Logger LOGGER = Logger.getLogger(ActionOrderingSystem.class.getName());

	private ActionOrderingSystem()
	{
	}

	/**
	 * Reorders action queue by priority.
	 * <p>
	 * Higher priority actions move to front:
	 * 1. Emergency (HP critical, combat)
	 * 2. Recovery (healing, buff)
	 * 3. Standard (farming, movement)
	 *
	 * @param queue deque to reorder
	 */
	public static void prioritizeQueue(Deque<BotAction> queue)
	{
		if (queue == null || queue.isEmpty())
		{
			return;
		}

		final Deque<BotAction> emergency = new ArrayDeque<>();
		final Deque<BotAction> recovery = new ArrayDeque<>();
		final Deque<BotAction> standard = new ArrayDeque<>();

		// Categorize actions
		for (final BotAction action : queue)
		{
			final String actionName = getActionName(action);

			if (isEmergency(actionName))
			{
				emergency.addLast(action);
			}
			else if (isRecovery(actionName))
			{
				recovery.addLast(action);
			}
			else
			{
				standard.addLast(action);
			}
		}

		// Rebuild queue: emergency -> recovery -> standard
		queue.clear();
		queue.addAll(emergency);
		queue.addAll(recovery);
		queue.addAll(standard);
	}

	/** Check if action is emergency (combat, dodge). */
	private static boolean isEmergency(String actionName)
	{
		return actionName != null && (actionName.contains("Combat") || actionName.contains("Dodge") || actionName.contains("Escape"));
	}

	/** Check if action is recovery (heal, buff). */
	private static boolean isRecovery(String actionName)
	{
		return actionName != null && (actionName.contains("Heal") || actionName.contains("Buff") || actionName.contains("Revive"));
	}

	private static String getActionName(BotAction action)
	{
		if (action == null)
		{
			return null;
		}
		return action.getClass().getSimpleName();
	}

	/**
	 * Removes duplicate actions from queue (keep first occurrence).
	 *
	 * @param queue deque to deduplicate
	 */
	public static void deduplicateQueue(Deque<BotAction> queue)
	{
		if (queue == null || queue.size() < 2)
		{
			return;
		}

		final Deque<BotAction> seen = new ArrayDeque<>();
		queue.removeIf(action ->
		{
			final String name = getActionName(action);
			if (seen.stream().map(ActionOrderingSystem::getActionName).anyMatch(n -> n != null && n.equals(name)))
			{
				return true; // Remove duplicate
			}
			seen.add(action);
			return false;
		});
	}
}
