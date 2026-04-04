/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.logging;

import java.util.logging.Logger;

import org.l2jmobius.gameserver.bot.core.exception.BotRecoveryStrategy;
import org.l2jmobius.gameserver.bot.core.model.BotInstance;
import org.l2jmobius.gameserver.bot.core.timeout.ActionTimeoutManager;

/**
 * Metrics collection helper for GoapAgent.
 * <p>
 * Hooks key lifecycle events during tick() and records them to BotMetrics.
 * Keeps tick() logging separate from metrics recording for clarity.
 */
public class MetricsCollector
{
	private MetricsCollector()
	{
	}

	/** Records a successful plan execution. */
	public static void recordPlanExecuted(BotInstance bot, int planLength)
	{
		BotMetrics.recordPlanBuilt(0);
		if (planLength > 0)
		{
			BotMetrics.recordActionExecuted();
		}
	}

	/** Records a plan build event. */
	public static void recordPlanBuilt(BotInstance bot, int planLength)
	{
		if (planLength > 0)
		{
			BotMetrics.recordPlanBuilt(0);
		}
	}

	/** Records action start (tracked separately from execution). */
	public static void recordActionStart(BotInstance bot, String actionName, long timeoutMs)
	{
		BotMetrics.recordActionExecuted();
		ActionTimeoutManager.startTracking(bot, actionName, timeoutMs);
	}

	/** Records action completion. */
	public static void recordActionCompleted(BotInstance bot)
	{
		ActionTimeoutManager.stopTracking(bot);
	}

	/** Records action timeout. */
	public static void recordActionTimeout(BotInstance bot, String actionName)
	{
		BotMetrics.recordActionTimeout();
		ActionTimeoutManager.stopTracking(bot);
	}

	/** Records validation failure. */
	public static void recordValidationFailed(BotInstance bot, String reason)
	{
		BotMetrics.recordActionFailed();
	}

	/** Records exception handling. */
	public static void recordExceptionHandled(BotInstance bot, BotRecoveryStrategy strategy)
	{
		BotMetrics.recordActionFailed();
		switch (strategy)
		{
			case REPLAN:
				BotMetrics.recordExceptionRecoverable();
				break;
			case ADJUST_AND_REPLAN:
				BotMetrics.recordExceptionValidation();
				break;
			case DISABLE:
				BotMetrics.recordExceptionFatal();
				break;
		}
	}

	/** Records bot state transitions. */
	public static void recordPhaseTransition(BotInstance bot)
	{
		BotMetrics.recordPlanBuilt(0);
	}

	/** Get metrics summary for diagnostics. */
	public static String getSummary()
	{
		return BotMetrics.getSummary();
	}
}
