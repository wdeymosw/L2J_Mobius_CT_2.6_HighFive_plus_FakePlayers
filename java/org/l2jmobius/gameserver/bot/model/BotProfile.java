/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.model;

import org.l2jmobius.gameserver.bot.zone.FarmZone;

/**
 * Immutable template describing a bot character.
 * Loaded from config/XML at startup — does not change at runtime.
 */
public class BotProfile
{
	private final int _characterObjectId;
	private final BotType _type;
	private final FarmZone _zone;
	private final float _mistakeRate;

	public BotProfile(int characterObjectId, BotType type, FarmZone zone, float mistakeRate)
	{
		_characterObjectId = characterObjectId;
		_type = type;
		_zone = zone;
		_mistakeRate = mistakeRate;
	}

	/** objectId in the characters table — used by Player.load() */
	public int getCharacterObjectId()
	{
		return _characterObjectId;
	}

	public BotType getType()
	{
		return _type;
	}

	public FarmZone getZone()
	{
		return _zone;
	}

	/** Probability [0.0 - 1.0] of making a human-like mistake each think cycle. */
	public float getMistakeRate()
	{
		return _mistakeRate;
	}
}
