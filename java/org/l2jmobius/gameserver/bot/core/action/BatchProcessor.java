/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.action;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Batch processing system for actions.
 * <p>
 * Accumulates actions and processes in batches for:
 * - Reduced context switching
 * - Better CPU cache usage
 * - Parallelization efficiency
 */
public class BatchProcessor
{
	private static final Logger LOGGER = Logger.getLogger(BatchProcessor.class.getName());

	private static final int DEFAULT_BATCH_SIZE = 10;
	private static final List<Object> batch = new ArrayList<>();

	private BatchProcessor()
	{
	}

	/**
	 * Adds action to batch.
	 *
	 * @param action action to process
	 * @return true if batch is ready for processing
	 */
	public static boolean addToBatch(Object action)
	{
		synchronized (batch)
		{
			batch.add(action);
			return batch.size() >= DEFAULT_BATCH_SIZE;
		}
	}

	/**
	 * Gets current batch for processing.
	 *
	 * @return list of actions, or empty list
	 */
	public static List<Object> getBatch()
	{
		synchronized (batch)
		{
			if (batch.isEmpty())
			{
				return new ArrayList<>();
			}

			final List<Object> result = new ArrayList<>(batch);
			batch.clear();
			return result;
		}
	}

	/**
	 * Forces batch processing regardless of size.
	 *
	 * @return current batch
	 */
	public static List<Object> flushBatch()
	{
		return getBatch();
	}

	/** Get current batch size. */
	public static int getBatchSize()
	{
		synchronized (batch)
		{
			return batch.size();
		}
	}

	/** Check if batch is ready. */
	public static boolean isBatchReady()
	{
		synchronized (batch)
		{
			return batch.size() >= DEFAULT_BATCH_SIZE;
		}
	}

	/** Clear batch. */
	public static void clear()
	{
		synchronized (batch)
		{
			batch.clear();
		}
	}
}
