/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.action;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.logging.Logger;

/**
 * Action ordering implementation.
 * <p>
 * Integrates action prioritization system into bot workflow.
 */
public class ActionOrderingImpl
{
	private static final Logger LOGGER = Logger.getLogger(ActionOrderingImpl.class.getName());

	private ActionOrderingImpl()
	{
	}

	/**
	 * Applies action ordering to a queue.
	 *
	 * @param queue action queue to reorder
	 */
	public static void orderQueue(Deque<Object> queue)
	{
		if (queue == null || queue.isEmpty())
		{
			return;
		}

		ActionOrderingSystem.prioritizeQueue(queue);
		ActionOrderingSystem.deduplicateQueue(queue);
	}

	/**
	 * Creates a new ordered queue from actions.
	 *
	 * @return new empty ordered deque
	 */
	public static Deque<Object> createOrderedQueue()
	{
		return new ArrayDeque<>();
	}

	/** Get status. */
	public static String getStatus()
	{
		return "ActionOrdering: Active (prioritization + deduplication enabled)";
	}
}
