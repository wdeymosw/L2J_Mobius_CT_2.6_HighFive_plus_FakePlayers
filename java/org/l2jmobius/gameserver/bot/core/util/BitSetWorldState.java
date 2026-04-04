/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.util;

import java.util.BitSet;
import java.util.logging.Logger;

/**
 * BitSet-based world state representation.
 * <p>
 * More memory-efficient than object-based state:
 * - Packed boolean flags into bits
 * - Fast bitwise operations
 * - Reduced GC pressure
 */
public class BitSetWorldState
{
	private static final Logger LOGGER = Logger.getLogger(BitSetWorldState.class.getName());

	private final BitSet bits;
	private final int capacity;

	public BitSetWorldState(int capacity)
	{
		this.capacity = capacity;
		this.bits = new BitSet(capacity);
	}

	/** Set a state flag. */
	public void set(int index, boolean value)
	{
		if (index >= 0 && index < capacity)
		{
			bits.set(index, value);
		}
	}

	/** Get a state flag. */
	public boolean get(int index)
	{
		if (index >= 0 && index < capacity)
		{
			return bits.get(index);
		}
		return false;
	}

	/** Clear all flags. */
	public void clear()
	{
		bits.clear();
	}

	/** Get cardinality (number of set bits). */
	public int cardinality()
	{
		return bits.cardinality();
	}

	/** Create a copy. */
	public BitSetWorldState copy()
	{
		final BitSetWorldState copy = new BitSetWorldState(capacity);
		copy.bits.or(bits);
		return copy;
	}

	/** Get memory usage estimate. */
	public long getMemoryUsage()
	{
		return bits.size() / 8; // Approximate bytes
	}

	/** Get status. */
	public String getStatus()
	{
		return String.format("BitSetWorldState: bits=%d, set=%d, memory=%d bytes", capacity, cardinality(), getMemoryUsage());
	}
}
