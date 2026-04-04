/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.pool;

/**
 * Object pool for WorldState objects (extends generic ObjectPool).
 * <p>
 * Reduces allocation overhead for world state snapshots.
 */
public class WorldStatePool extends ObjectPool<Object>
{
	private static final WorldStatePool instance = new WorldStatePool(50);

	private WorldStatePool(int maxSize)
	{
		super("WorldStatePool", maxSize);
	}

	@Override
	protected Object create()
	{
		return new Object(); // Placeholder for actual WorldState
	}

	@Override
	protected void reset(Object obj)
	{
		// Reset world state (implementation depends on actual WorldState class)
	}

	public static Object acquireObject()
	{
		final Object obj = instance.acquire();
		PoolingMetrics.recordAcquisition(true);
		return obj;
	}

	public static void releaseObject(Object obj)
	{
		instance.release(obj);
	}

	public static String getPoolStatus()
	{
		return instance.getStatus();
	}
}
