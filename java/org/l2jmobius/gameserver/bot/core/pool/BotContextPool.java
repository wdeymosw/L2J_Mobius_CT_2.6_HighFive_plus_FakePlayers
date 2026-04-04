/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.pool;

import org.l2jmobius.gameserver.bot.core.model.BotContext;

/**
 * Object pool for BotContext objects.
 * <p>
 * Note: BotContext is immutable and cannot be pooled effectively.
 * This class exists for API compatibility only.
 */
public final class BotContextPool
{
	private BotContextPool()
	{
	}

	// BotContext is immutable and created once per tick
	// No pooling possible - always create new instances
}
