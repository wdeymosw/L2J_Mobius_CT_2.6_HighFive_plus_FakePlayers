/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.behaviour;

import java.util.logging.Logger;

import org.l2jmobius.gameserver.bot.core.model.BotInstance;

/**
 * Manages the active {@link BotBehaviour} for one bot.
 * Handles lifecycle transitions: calls {@link BotBehaviour#onExit} on the
 * departing behaviour and {@link BotBehaviour#onEnter} on the incoming one.
 * <p>
 * One controller per {@link BotInstance}. Must not be shared.
 * <p>
 * <b>Not thread-safe.</b> All calls must be on the BotManager scheduler thread.
 */
public class BehaviourController
{
	private static final Logger LOGGER = Logger.getLogger(BehaviourController.class.getName());

	private BotBehaviour _active;

	/**
	 * Creates the controller with {@code initial} as the starting behaviour.
	 * Calls {@link BotBehaviour#onEnter} immediately.
	 */
	public BehaviourController(BotBehaviour initial, BotInstance bot, long now)
	{
		_active = initial;
		_active.onEnter(bot, now);
	}

	/** Returns the currently active behaviour. Never {@code null}. */
	public BotBehaviour getActive()
	{
		return _active;
	}

	/**
	 * Ticks the active behaviour — checks timers and transition conditions.
	 * Called every tick by {@link BotInstance#update(long)} before GoapAgent.
	 */
	public void tick(BotInstance bot, long now)
	{
		_active.onTick(bot, now);
	}

	/**
	 * Transitions to {@code next}.
	 * No-op if {@code next} is already the active behaviour.
	 * Calls {@link BotBehaviour#onExit} on the current and {@link BotBehaviour#onEnter} on next.
	 */
	public void transition(BotBehaviour next, BotInstance bot, long now)
	{
		if (_active == next)
		{
			return;
		}
		LOGGER.info("BehaviourController [" + bot.getPlayer().getName() + "]: " + _active.getName() + " → " + next.getName());
		_active.onExit(bot);
		_active = next;
		_active.onEnter(bot, now);
	}

	/**
	 * Dispatches an event to the active behaviour.
	 * The behaviour may call {@link #transition} in response.
	 *
	 * @param event   the event type
	 * @param bot     the bot receiving the event
	 * @param now     current time in ms
	 * @param payload optional event data (e.g. BotRole for PARTY_INVITE); may be null
	 */
	public void dispatch(BotBehaviourEvent event, BotInstance bot, long now, Object payload)
	{
		LOGGER.fine(() -> "BehaviourController [" + bot.getPlayer().getName() + "]: event=" + event + " active=" + _active.getName());
		// Future: delegate to active behaviour for event handling
	}
}
