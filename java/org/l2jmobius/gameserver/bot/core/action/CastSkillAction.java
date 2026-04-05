/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.action;

import java.util.logging.Logger;

import org.l2jmobius.gameserver.bot.core.exception.FatalBotException;
import org.l2jmobius.gameserver.bot.core.goap.GoapTuning;
import org.l2jmobius.gameserver.bot.core.exception.RecoverableBotException;
import org.l2jmobius.gameserver.bot.core.exception.ValidationBotException;
import org.l2jmobius.gameserver.bot.core.model.BotInstance;
import org.l2jmobius.gameserver.model.skill.Skill;

/**
 * Casts a specific skill and waits until the cast finishes.
 * <p>
 * {@code player.useMagic()} starts the cast asynchronously; we poll
 * {@code isCastingNow()} to detect completion. A safety timeout prevents
 * the action from hanging if the cast never started (e.g. range miss).
 */
public class CastSkillAction implements BotAction
{
	private static final Logger LOGGER = Logger.getLogger(CastSkillAction.class.getName());

	/** Maximum time to wait for a cast to start and finish. */
	private static final long CAST_TIMEOUT_MS = GoapTuning.ACTION_CAST_TIMEOUT_MS;

	private final Skill _skill;
	private boolean _executed = false;
	private long _startTime = 0;

	public CastSkillAction(Skill skill)
	{
		_skill = skill;
	}

	@Override
	public void execute(BotInstance bot, long now) throws FatalBotException, ValidationBotException, RecoverableBotException
	{
		if (_executed)
		{
			return;
		}
		_executed = true;
		_startTime = now;
		bot.getPlayer().useMagic(_skill, false, false);
		if (!bot.getPlayer().isCastingNow())
		{
			// Cast failed to start — disable skill briefly to avoid looping.
			bot.getPlayer().disableSkill(_skill, GoapTuning.ACTION_CAST_FAIL_DISABLE_MS);
			LOGGER.fine("CastSkillAction: cast failed [" + _skill.getName() + "], skipping 3s");
		}
	}

	@Override
	public boolean isDone(BotInstance bot, long now)
	{
		if (!_executed)
		{
			return false;
		}
		if (!bot.getPlayer().isCastingNow())
		{
			return true;
		}
		return (now - _startTime) > CAST_TIMEOUT_MS;
	}
}
