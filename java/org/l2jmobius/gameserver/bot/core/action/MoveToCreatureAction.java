/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.action;

import java.util.logging.Logger;

import org.l2jmobius.gameserver.ai.Intention;
import org.l2jmobius.gameserver.bot.core.goap.GoapTuning;
import org.l2jmobius.gameserver.bot.core.exception.FatalBotException;
import org.l2jmobius.gameserver.bot.core.exception.RecoverableBotException;
import org.l2jmobius.gameserver.bot.core.exception.ValidationBotException;
import org.l2jmobius.gameserver.bot.core.model.BotInstance;
import org.l2jmobius.gameserver.bot.core.service.PathService;
import org.l2jmobius.gameserver.bot.core.service.TargetService;
import org.l2jmobius.gameserver.geoengine.pathfinding.GeoLocation;
import org.l2jmobius.gameserver.model.actor.Creature;
import org.l2jmobius.gameserver.model.actor.Player;

/**
 * Moves the bot toward a live creature using smooth waypoint-based tracking.
 * <p>
 * Unlike {@link MoveToAction} (static destination), this action follows a live target:
 * <ol>
 *   <li>On first execution PathService builds the full A* path to the creature's position.</li>
 *   <li>All intermediate waypoints are stable — the bot moves through them without stopping.</li>
 *   <li>Only the <b>last waypoint</b> is refreshed to the creature's current position
 *       once the bot is within {@value #LAST_WP_UPDATE_RANGE} units of it.
 *       This steers smoothly toward a moving mob without rebuilding the whole path.</li>
 *   <li>If the creature moves more than {@value #REPATH_THRESHOLD} units from the stored
 *       navigation target, the global path is cleared so PathService rebuilds it.</li>
 * </ol>
 */
public class MoveToCreatureAction implements BotAction
{
	private static final Logger LOGGER = Logger.getLogger(MoveToCreatureAction.class.getName());

	/** Distance to last waypoint below which we refresh it to the creature's live position. */
	private static final int LAST_WP_UPDATE_RANGE = GoapTuning.MOB_LAST_WP_UPDATE_RANGE;

	/** Mob displacement that triggers a global path reset (coarse repath for wandering mobs). */
	private static final int REPATH_THRESHOLD = GoapTuning.MOB_REPATH_THRESHOLD;

	/** After this many consecutive geo-errors, give up (target in unreachable geo area). */
	private static final int MAX_GEO_ERRORS = GoapTuning.MAX_GEO_ERRORS;

	/**
	 * Maximum accumulated flee ticks before the target is abandoned.
	 * The counter decays (not resets) on non-flee ticks, so intermittent mob pauses
	 * don't erase evidence of fleeing.
	 */
	private static final int MAX_FLEE_CHECKS = GoapTuning.MAX_FLEE_CHECKS;

	/** Minimum squared-distance increase per tick to count as "mob is moving away". */
	private static final int FLEE_DELTA_SQ = GoapTuning.MOB_FLEE_DELTA * GoapTuning.MOB_FLEE_DELTA;

	/**
	 * Chase ticks after which net-progress is evaluated.
	 * If the bot is no closer than at chase start, the mob is kiting — abandon.
	 */
	private static final int NO_PROGRESS_TICKS = GoapTuning.MOB_NO_PROGRESS_TICKS;

	private final Creature _target;
	private final int _arrivalRadius;
	private int _geoErrorCount = 0;
	private int _fleeCount = 0;
	/** Distance² to the target at the start of this chase. Set on the first tick. */
	private int _initialDistSq = -1;
	/** Distance² observed on the previous tick. Used for consecutive flee detection. */
	private int _lastDistSq = 0;
	/** Total ticks spent chasing this target. Used for net-progress check. */
	private int _chaseTicks = 0;

	/**
	 * Navigation target passed to PathService — snapshot of mob position, updated only
	 * on significant mob movement. Prevents global-path rebuild on every tick.
	 */
	private int _navX;
	private int _navY;
	private int _navZ;

	public MoveToCreatureAction(Creature target, int arrivalRadius)
	{
		_target = target;
		_arrivalRadius = arrivalRadius;
		_navX = target.getX();
		_navY = target.getY();
		_navZ = target.getZ();
	}

	@Override
	public void execute(BotInstance bot, long now) throws FatalBotException, ValidationBotException, RecoverableBotException
	{
		if ((_target == null) || _target.isDead())
		{
			return;
		}

		// --- If attacked by a different mob while moving: stop, switch target.
		// GoapAgent will fire shouldInterrupt() next tick and replan toward the attacker. ---
		if (TargetService.isAttackedByDifferentMob(bot))
		{
			bot.clearGlobalPath();
			bot.clearPath();
			bot.getPlayer().getAI().setIntention(Intention.IDLE);
			TargetService.switchToNearestAttacker(bot);
			return;
		}

		final int tx = _target.getX();
		final int ty = _target.getY();
		final int tz = _target.getZ();

		// --- Fleeing mob detection ---
		// Two complementary checks:
		//   1. Accumulated flee ticks: each tick the mob moves further adds 1; each tick it
		//      doesn't subtracts 1 (decay). Abandons when accumulated count ≥ MAX_FLEE_CHECKS.
		//      Decay (not reset) means intermittent mob pauses don't erase flee evidence.
		//   2. Net-progress check: if after NO_PROGRESS_TICKS ticks the bot is no closer
		//      than when the chase started, the mob is kiting — abandon immediately.
		{
			final Player player = bot.getPlayer();
			final int fdx = tx - player.getX();
			final int fdy = ty - player.getY();
			final int distSq = (fdx * fdx) + (fdy * fdy);

			if (_initialDistSq < 0)
			{
				// First tick: establish baseline; skip flee check this tick.
				_initialDistSq = distSq;
				_lastDistSq = distSq;
			}
			else
			{
				_chaseTicks++;

				// Accumulated flee counter (decay instead of hard reset).
				if (distSq > (_lastDistSq + FLEE_DELTA_SQ))
				{
					_fleeCount++;
				}
				else
				{
					_fleeCount = Math.max(0, _fleeCount - 1);
				}

				if (_fleeCount >= MAX_FLEE_CHECKS)
				{
					bot.clearTarget();
					bot.clearGlobalPath();
					bot.clearPath();
					player.getAI().setIntention(Intention.IDLE);
					return;
				}

				// Net-progress check: if the bot has spent enough ticks chasing
				// but is no closer than the starting distance, abandon.
				if ((_chaseTicks >= NO_PROGRESS_TICKS) && (distSq >= _initialDistSq))
				{
					bot.clearTarget();
					bot.clearGlobalPath();
					bot.clearPath();
					player.getAI().setIntention(Intention.IDLE);
					return;
				}

				_lastDistSq = distSq;
			}
		}

		// --- When close to the last waypoint, refresh it to mob's live position.
		// This steers the bot toward the mob as it closes in without stopping. ---
		if (bot.hasPath())
		{
			final GeoLocation lastWp = bot.getLastWaypoint();
			if (lastWp != null)
			{
				final Player player = bot.getPlayer();
				final int dx = player.getX() - lastWp.getX();
				final int dy = player.getY() - lastWp.getY();
				if ((dx * dx) + (dy * dy) < (LAST_WP_UPDATE_RANGE * LAST_WP_UPDATE_RANGE))
				{
					bot.updateLastWaypoint(tx, ty, tz);
					_navX = tx;
					_navY = ty;
					_navZ = tz;
				}
			}
		}

		// --- If mob moved far from stored nav target, clear global path so PathService
		// rebuilds the segment layout toward the new position. ---
		final int ndx = tx - _navX;
		final int ndy = ty - _navY;
		if ((ndx * ndx) + (ndy * ndy) > (REPATH_THRESHOLD * REPATH_THRESHOLD))
		{
			bot.clearGlobalPath();
			_navX = tx;
			_navY = ty;
			_navZ = tz;
		}

		try
		{
			PathService.thinkMove(bot, _navX, _navY, _navZ);
			_geoErrorCount = 0; // reset on success
		}
		catch (ArrayIndexOutOfBoundsException e)
		{
			// Target is in a geodata area that is outside map bounds.
			// After MAX_GEO_ERRORS consecutive failures, abandon this target.
			_geoErrorCount++;
			final Player player = bot.getPlayer();
			LOGGER.warning("[" + player.getName() + "] GeoData out-of-bounds at [" + player.getX() + "," + player.getY() + "," + player.getZ() + "] (errors=" + _geoErrorCount + "): " + e.getMessage());
			if (_geoErrorCount >= MAX_GEO_ERRORS)
			{
				bot.clearTarget();
			}
		}
	}

	@Override
	public boolean isDone(BotInstance bot, long now)
	{
		if ((_target == null) || _target.isDead())
		{
			return true;
		}
		// Target was cleared because it's in an unreachable geo area.
		if (bot.getTarget() == null)
		{
			return true;
		}
		return bot.getPlayer().isInsideRadius2D(_target.getX(), _target.getY(), _target.getZ(), _arrivalRadius);
	}
}
