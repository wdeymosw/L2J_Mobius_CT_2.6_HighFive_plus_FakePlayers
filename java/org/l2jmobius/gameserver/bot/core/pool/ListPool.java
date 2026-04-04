/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.pool;

import java.util.ArrayList;
import java.util.List;

/**
 * Generic object pool for List instances.
 * <p>
 * Reuses ArrayList instances to reduce allocation overhead.
 */
public class ListPool extends ObjectPool<List<?>>
{
	private static final ListPool instance = new ListPool(50);

	private ListPool(int maxSize)
	{
		super("ListPool", maxSize);
	}

	@Override
	protected List<?> create()
	{
		return new ArrayList<>();
	}

	@Override
	protected void reset(List<?> obj)
	{
		obj.clear();
	}

	@SuppressWarnings("unchecked")
	public static <T> List<T> acquireList()
	{
		return (List<T>) instance.acquire();
	}

	public static void releaseList(List<?> obj)
	{
		instance.release(obj);
	}

	public static String getListPoolStatus()
	{
		return instance.getStatus();
	}
}
