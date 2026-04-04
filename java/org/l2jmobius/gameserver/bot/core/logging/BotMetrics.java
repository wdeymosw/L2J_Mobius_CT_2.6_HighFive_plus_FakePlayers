/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.logging;

import java.util.concurrent.atomic.AtomicLong;

import org.l2jmobius.gameserver.bot.core.model.BotInstance;

/**
 * Runtime metrics collection for bot system diagnostics.
 * <p>
 * Thread-safe counters for:
 * - Action executions and timeouts
 * - Exception handling
 * - State validation failures
 * - Plan builds and changes
 */
public class BotMetrics
{
	// =========================================================================
	// Action Metrics
	// =========================================================================
	private static final AtomicLong ACTION_EXECUTED = new AtomicLong(0);
	private static final AtomicLong ACTION_COMPLETED = new AtomicLong(0);
	private static final AtomicLong ACTION_TIMEOUT = new AtomicLong(0);
	private static final AtomicLong ACTION_FAILED = new AtomicLong(0);

	// =========================================================================
	// Exception Metrics
	// =========================================================================
	private static final AtomicLong EXCEPTION_RECOVERABLE = new AtomicLong(0);
	private static final AtomicLong EXCEPTION_VALIDATION = new AtomicLong(0);
	private static final AtomicLong EXCEPTION_FATAL = new AtomicLong(0);
	private static final AtomicLong EXCEPTION_OTHER = new AtomicLong(0);

	// =========================================================================
	// Planning Metrics
	// =========================================================================
	private static final AtomicLong PLAN_BUILT = new AtomicLong(0);
	private static final AtomicLong PLAN_CLEARED = new AtomicLong(0);
	private static final AtomicLong PLAN_INTERRUPTED = new AtomicLong(0);

	// =========================================================================
	// Bot Lifecycle Metrics
	// =========================================================================
	private static final AtomicLong BOT_CREATED = new AtomicLong(0);
	private static final AtomicLong BOT_DISABLED = new AtomicLong(0);
	private static final AtomicLong BOT_REVIVED = new AtomicLong(0);

	// =========================================================================
	// Timing Metrics (in milliseconds)
	// =========================================================================
	private static final AtomicLong PLAN_TIME_TOTAL = new AtomicLong(0);
	private static final AtomicLong PLAN_COUNT = new AtomicLong(0);

	private BotMetrics()
	{
	}

	// =========================================================================
	// Action Metrics Recording
	// =========================================================================

	/** Record action execution. */
	public static void recordActionExecuted()
	{
		ACTION_EXECUTED.incrementAndGet();
	}

	/** Record action completion. */
	public static void recordActionCompleted()
	{
		ACTION_COMPLETED.incrementAndGet();
	}

	/** Record action timeout. */
	public static void recordActionTimeout()
	{
		ACTION_TIMEOUT.incrementAndGet();
	}

	/** Record action failure. */
	public static void recordActionFailed()
	{
		ACTION_FAILED.incrementAndGet();
	}

	// =========================================================================
	// Exception Metrics Recording
	// =========================================================================

	/** Record recoverable exception. */
	public static void recordExceptionRecoverable()
	{
		EXCEPTION_RECOVERABLE.incrementAndGet();
	}

	/** Record validation exception. */
	public static void recordExceptionValidation()
	{
		EXCEPTION_VALIDATION.incrementAndGet();
	}

	/** Record fatal exception. */
	public static void recordExceptionFatal()
	{
		EXCEPTION_FATAL.incrementAndGet();
	}

	/** Record other exception. */
	public static void recordExceptionOther()
	{
		EXCEPTION_OTHER.incrementAndGet();
	}

	// =========================================================================
	// Planning Metrics Recording
	// =========================================================================

	/** Record plan build (with optional duration). */
	public static void recordPlanBuilt(long durationMs)
	{
		PLAN_BUILT.incrementAndGet();
		PLAN_TIME_TOTAL.addAndGet(durationMs);
		PLAN_COUNT.incrementAndGet();
	}

	/** Record plan cleared. */
	public static void recordPlanCleared()
	{
		PLAN_CLEARED.incrementAndGet();
	}

	/** Record plan interrupted. */
	public static void recordPlanInterrupted()
	{
		PLAN_INTERRUPTED.incrementAndGet();
	}

	// =========================================================================
	// Bot Lifecycle Recording
	// =========================================================================

	/** Record bot creation. */
	public static void recordBotCreated()
	{
		BOT_CREATED.incrementAndGet();
	}

	/** Record bot disabled. */
	public static void recordBotDisabled()
	{
		BOT_DISABLED.incrementAndGet();
	}

	/** Record bot revived. */
	public static void recordBotRevived()
	{
		BOT_REVIVED.incrementAndGet();
	}

	// =========================================================================
	// Metrics Queries
	// =========================================================================

	public static long getActionExecutedCount()
	{
		return ACTION_EXECUTED.get();
	}

	public static long getActionCompletedCount()
	{
		return ACTION_COMPLETED.get();
	}

	public static long getActionTimeoutCount()
	{
		return ACTION_TIMEOUT.get();
	}

	public static long getActionFailedCount()
	{
		return ACTION_FAILED.get();
	}

	public static long getExceptionRecoverableCount()
	{
		return EXCEPTION_RECOVERABLE.get();
	}

	public static long getExceptionValidationCount()
	{
		return EXCEPTION_VALIDATION.get();
	}

	public static long getExceptionFatalCount()
	{
		return EXCEPTION_FATAL.get();
	}

	public static long getExceptionOtherCount()
	{
		return EXCEPTION_OTHER.get();
	}

	public static long getPlanBuiltCount()
	{
		return PLAN_BUILT.get();
	}

	public static long getPlanClearedCount()
	{
		return PLAN_CLEARED.get();
	}

	public static long getPlanInterruptedCount()
	{
		return PLAN_INTERRUPTED.get();
	}

	public static long getBotCreatedCount()
	{
		return BOT_CREATED.get();
	}

	public static long getBotDisabledCount()
	{
		return BOT_DISABLED.get();
	}

	public static long getBotReivedCount()
	{
		return BOT_REVIVED.get();
	}

	/** Average plan build time in ms. */
	public static double getAveragePlanTime()
	{
		final long count = PLAN_COUNT.get();
		if (count == 0)
		{
			return 0.0;
		}
		return (double) PLAN_TIME_TOTAL.get() / count;
	}

	/** Total plan time in ms. */
	public static long getTotalPlanTime()
	{
		return PLAN_TIME_TOTAL.get();
	}

	// =========================================================================
	// Comprehensive Summary
	// =========================================================================

	/**
	 * Returns a formatted summary of all metrics.
	 */
	public static String getSummary()
	{
		final StringBuilder sb = new StringBuilder();
		sb.append("=== BOT METRICS SUMMARY ===\n");
		sb.append("Actions: executed=").append(ACTION_EXECUTED.get());
		sb.append(" completed=").append(ACTION_COMPLETED.get());
		sb.append(" timeout=").append(ACTION_TIMEOUT.get());
		sb.append(" failed=").append(ACTION_FAILED.get()).append("\n");

		sb.append("Exceptions: recoverable=").append(EXCEPTION_RECOVERABLE.get());
		sb.append(" validation=").append(EXCEPTION_VALIDATION.get());
		sb.append(" fatal=").append(EXCEPTION_FATAL.get());
		sb.append(" other=").append(EXCEPTION_OTHER.get()).append("\n");

		sb.append("Plans: built=").append(PLAN_BUILT.get());
		sb.append(" cleared=").append(PLAN_CLEARED.get());
		sb.append(" interrupted=").append(PLAN_INTERRUPTED.get());
		sb.append(" avg_time=").append(String.format("%.2f", getAveragePlanTime())).append("ms\n");

		sb.append("Bots: created=").append(BOT_CREATED.get());
		sb.append(" disabled=").append(BOT_DISABLED.get());
		sb.append(" revived=").append(BOT_REVIVED.get()).append("\n");

		return sb.toString();
	}

	/** Reset all metrics (useful for benchmarking). */
	public static void resetAll()
	{
		ACTION_EXECUTED.set(0);
		ACTION_COMPLETED.set(0);
		ACTION_TIMEOUT.set(0);
		ACTION_FAILED.set(0);

		EXCEPTION_RECOVERABLE.set(0);
		EXCEPTION_VALIDATION.set(0);
		EXCEPTION_FATAL.set(0);
		EXCEPTION_OTHER.set(0);

		PLAN_BUILT.set(0);
		PLAN_CLEARED.set(0);
		PLAN_INTERRUPTED.set(0);

		BOT_CREATED.set(0);
		BOT_DISABLED.set(0);
		BOT_REVIVED.set(0);

		PLAN_TIME_TOTAL.set(0);
		PLAN_COUNT.set(0);
	}
}
