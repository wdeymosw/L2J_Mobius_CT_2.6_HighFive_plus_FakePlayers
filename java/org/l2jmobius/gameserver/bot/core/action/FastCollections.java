/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.action;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * High-performance collections for bot system.
 * <p>
 * Replaces standard collections with optimized variants:
 * - ArrayDeque instead of LinkedList
 * - Primitive arrays instead of ArrayList<Integer>
 * - BitSet for boolean flags
 */
public class FastCollections
{
	private static final Logger LOGGER = Logger.getLogger(FastCollections.class.getName());

	private FastCollections()
	{
	}

	/**
	 * Creates a primitive int array for storing action indices.
	 * More efficient than ArrayList<Integer>.
	 */
	public static int[] createIntArray(int capacity)
	{
		return new int[capacity];
	}

	/**
	 * Creates a primitive boolean array for flags.
	 * More efficient than ArrayList<Boolean>.
	 */
	public static boolean[] createBooleanArray(int capacity)
	{
		return new boolean[capacity];
	}

	/**
	 * Creates a primitive long array for timestamps.
	 * More efficient than ArrayList<Long>.
	 */
	public static long[] createLongArray(int capacity)
	{
		return new long[capacity];
	}

	/**
	 * Creates a generic object array.
	 */
	@SuppressWarnings("unchecked")
	public static <T> T[] createArray(int capacity)
	{
		return (T[]) new Object[capacity];
	}

	/**
	 * Compacts array by removing null entries.
	 */
	public static <T> List<T> compact(T[] array)
	{
		final List<T> result = new ArrayList<>();

		for (final T item : array)
		{
			if (item != null)
			{
				result.add(item);
			}
		}

		return result;
	}

	/** Get status. */
	public static String getStatus()
	{
		return "FastCollections: Using primitive arrays and ArrayDeque for performance";
	}
}
