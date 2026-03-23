/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.model;

import org.l2jmobius.gameserver.bot.zone.FarmZone;
import org.l2jmobius.gameserver.model.actor.Creature;
import org.l2jmobius.gameserver.model.actor.Player;

/**
 * Wraps a {@link Player} instance and tracks bot-specific state.
 * <p>
 * The bot layer never reaches into game internals directly —
 * all game interactions go through the Player's public API.
 */
public class BotInstance
{
	// --- Game object (controlled, not extended) ---
	private final Player _player;
	private final BotProfile _profile;

	// --- Runtime state ---
	private BotState _state = BotState.IDLE;
	private Creature _target;

	// --- Timing (epoch ms) ---
	private long _nextThinkTime = 0;
	private long _nextSearchTime = 0;
	private long _sessionEndTime = 0;    // 0 = no session limit (CORE bots)
	private long _targetExpireTime = 0;  // 0 = no limit; set when target is acquired

	// --- Stuck detection ---
	private int _lastX;
	private int _lastY;
	private long _lastMoveCheckTime = 0;

	public BotInstance(Player player, BotProfile profile)
	{
		_player = player;
		_profile = profile;
		_lastX = player.getX();
		_lastY = player.getY();
		// Initialize to now so stuck detection doesn't fire on the very first tick.
		_lastMoveCheckTime = System.currentTimeMillis();
	}

	// --- Accessors ---

	public Player getPlayer()
	{
		return _player;
	}

	public BotProfile getProfile()
	{
		return _profile;
	}

	public FarmZone getZone()
	{
		return _profile.getZone();
	}

	public BotState getState()
	{
		return _state;
	}

	public void setState(BotState state)
	{
		_state = state;
	}

	public Creature getTarget()
	{
		return _target;
	}

	public void setTarget(Creature target)
	{
		_target = target;
	}

	public void clearTarget()
	{
		_target = null;
		_targetExpireTime = 0;
	}

	public long getTargetExpireTime()
	{
		return _targetExpireTime;
	}

	public void setTargetExpireTime(long time)
	{
		_targetExpireTime = time;
	}

	// --- Timing ---

	public long getNextThinkTime()
	{
		return _nextThinkTime;
	}

	public void setNextThinkTime(long time)
	{
		_nextThinkTime = time;
	}

	public long getNextSearchTime()
	{
		return _nextSearchTime;
	}

	public void setNextSearchTime(long time)
	{
		_nextSearchTime = time;
	}

	/** Session end time for NOISE bots. 0 means no limit. */
	public long getSessionEndTime()
	{
		return _sessionEndTime;
	}

	public void setSessionEndTime(long time)
	{
		_sessionEndTime = time;
	}

	public boolean isSessionExpired()
	{
		return (_sessionEndTime > 0) && (System.currentTimeMillis() >= _sessionEndTime);
	}

	// --- Stuck detection ---

	public int getLastX()
	{
		return _lastX;
	}

	public int getLastY()
	{
		return _lastY;
	}

	public long getLastMoveCheckTime()
	{
		return _lastMoveCheckTime;
	}

	public void updatePositionSnapshot()
	{
		_lastX = _player.getX();
		_lastY = _player.getY();
		_lastMoveCheckTime = System.currentTimeMillis();
	}

	// --- Convenience delegates (thin wrappers — no game logic here) ---

	public boolean isDead()
	{
		return _player.isDead();
	}

	public boolean isInZone()
	{
		return _profile.getZone().contains(_player.getX(), _player.getY());
	}

	public float getMistakeRate()
	{
		return _profile.getMistakeRate();
	}
}
