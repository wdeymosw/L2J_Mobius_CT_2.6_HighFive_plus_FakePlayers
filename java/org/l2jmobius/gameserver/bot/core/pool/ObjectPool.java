/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.pool;

import java.util.Stack;
import java.util.logging.Logger;

/**
 * Generic object pool for reusing expensive-to-create objects.
 * <p>
 * Reduces GC pressure by reusing instances instead of creating new ones.
 * Thread-safe via Stack (synchronized).
 *
 * @param <T> type of object to pool
 */
public abstract class ObjectPool<T>
{
	private static final Logger LOGGER = Logger.getLogger(ObjectPool.class.getName());

	private final Stack<T> available = new Stack<>();
	private final String poolName;
	private final int maxSize;
	private int created = 0;
	private int reused = 0;

	/**
	 * Creates a new object pool.
	 *
	 * @param poolName name for diagnostics
	 * @param maxSize maximum pool size
	 */
	protected ObjectPool(String poolName, int maxSize)
	{
		this.poolName = poolName;
		this.maxSize = maxSize;
	}

	/**
	 * Gets or creates an object from the pool.
	 *
	 * @return reused object or newly created
	 */
	public T acquire()
	{
		T obj;

		synchronized (available)
		{
			if (available.isEmpty())
			{
				obj = create();
				created++;
			}
			else
			{
				obj = available.pop();
				reused++;
			}
		}

		return obj;
	}

	/**
	 * Returns an object to the pool.
	 *
	 * @param obj object to return
	 */
	public void release(T obj)
	{
		if (obj == null)
		{
			return;
		}

		reset(obj);

		synchronized (available)
		{
			if (available.size() < maxSize)
			{
				available.push(obj);
			}
		}
	}

	/** Factory method to create new instances. Must be implemented by subclass. */
	protected abstract T create();

	/** Reset object state before re-use. Optional; override if needed. */
	protected void reset(T obj)
	{
	}

	/** Get current pool size. */
	public int size()
	{
		synchronized (available)
		{
			return available.size();
		}
	}

	/** Get diagnostic info. */
	public String getStatus()
	{
		synchronized (available)
		{
			return String.format("%s: size=%d, created=%d, reused=%d, reuse_rate=%.1f%%", poolName, available.size(), created, reused, (reused * 100.0f) / Math.max(1, created + reused));
		}
	}

	/** Clear all pooled objects. */
	public void clear()
	{
		synchronized (available)
		{
			available.clear();
		}
	}

	/** Reset statistics. */
	public void resetStats()
	{
		created = 0;
		reused = 0;
	}
}
