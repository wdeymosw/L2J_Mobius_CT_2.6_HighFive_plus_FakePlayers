/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.behaviour;

import org.l2jmobius.gameserver.bot.core.model.BotInstance;

/**
 * Base class providing shared timer utilities for all behaviours.
 * Subclasses call {@link #startTimer(long)} on enter and check
 * {@link #isTimerExpired()} in {@link #onTick(BotInstance, long)}
 * to decide when to request a transition.
 */
public abstract class AbstractBotBehaviour implements BotBehaviour
{
	private long _sessionEndTime = 0;

	/** Starts a one-shot timer that expires after {@code durationMs} milliseconds. */
	protected void startTimer(long durationMs)
	{
		_sessionEndTime = System.currentTimeMillis() + durationMs;
	}

	/** Returns {@code true} if the timer was started and has now expired. */
	protected boolean isTimerExpired()
	{
		return (_sessionEndTime > 0) && (System.currentTimeMillis() >= _sessionEndTime);
	}

	/** Cancels the running timer. */
	protected void cancelTimer()
	{
		_sessionEndTime = 0;
	}

	@Override
	public void onEnter(BotInstance bot, long now)
	{
	}

	@Override
	public void onExit(BotInstance bot)
	{
	}

	@Override
	public void onTick(BotInstance bot, long now)
	{
	}
}
