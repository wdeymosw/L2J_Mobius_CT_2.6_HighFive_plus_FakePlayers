/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.behaviour;

import java.util.List;
import java.util.logging.Logger;

import org.l2jmobius.gameserver.bot.core.action.MoveToAction;
import org.l2jmobius.gameserver.bot.core.action.SayAction;
import org.l2jmobius.gameserver.bot.core.action.WaitAction;
import org.l2jmobius.gameserver.bot.core.zone.BotZoneData;
import org.l2jmobius.gameserver.bot.core.goap.GoapAction;
import org.l2jmobius.gameserver.bot.core.goap.GoalSelector;
import org.l2jmobius.gameserver.bot.core.goap.action.RestockGoapAction;
import org.l2jmobius.gameserver.bot.core.goap.action.SellItemsGoapAction;
import org.l2jmobius.gameserver.bot.core.goap.action.TeleportToCityGoapAction;
import org.l2jmobius.gameserver.bot.core.goap.goal.GoToCityGoal;
import org.l2jmobius.gameserver.bot.core.goap.goal.RestockGoal;
import org.l2jmobius.gameserver.bot.core.model.BotInstance;
import org.l2jmobius.gameserver.bot.core.phrases.BotPhrases;
import org.l2jmobius.gameserver.bot.core.service.SellService;
import org.l2jmobius.gameserver.bot.core.service.SupplyService;
import org.l2jmobius.gameserver.bot.core.zone.BotZoneData;
import org.l2jmobius.gameserver.model.Location;

/**
 * City idle behaviour — travel to city, run activity script, signal rotation.
 * <p>
 * <b>FOR_PASSIVE</b> — an ACTIVE bot finished its PvE session. Teleports to city,
 * then walks a scripted route: Grocer → Guild → Gatekeeper area → city center.
 * Sells junk and restocks supplies at the Grocer stop. Only marks
 * {@code readyToPassive} after the full script completes.
 * <p>
 * <b>FOR_ACTIVE</b> — a PASSIVE bot finished its private-shop session. Teleports
 * to city and immediately marks {@code readyToActive} (city time was already
 * spent in PrivateShopBehaviour).
 * <p>
 * GOAP handles travel to city (GoToCityGoal → TeleportToCityGoapAction).
 * Once the bot is no longer in the farm zone, the city script starts and
 * {@link #isScripted()} returns {@code true}, suppressing further GOAP replanning.
 */
public class CityIdleBehaviour extends AbstractBotBehaviour
{
	private static final Logger LOGGER = Logger.getLogger(CityIdleBehaviour.class.getName());

	/** Controls whether this bot waits to become PASSIVE or ACTIVE after city arrival. */
	public enum Mode
	{
		/** ACTIVE bot arrived in city — runs city script then waits for PASSIVE assignment. */
		FOR_PASSIVE,
		/** PASSIVE bot arrived in city — signals readyToActive immediately. */
		FOR_ACTIVE
	}

	// -----------------------------------------------------------------------
	// City script steps (FOR_PASSIVE only)
	// -----------------------------------------------------------------------

	private enum ScriptStep
	{
		/** Bot just arrived; queue the Grocer stop on next idle tick. */
		GROCER,
		/** Grocer stop done; queue the Guildmaster stop. */
		GUILD,
		/** Guild done; queue the Gatekeeper/armor-shop area stop. */
		ARMOR,
		/** Armor stop done; walk to city-path entry point and set flag. */
		CENTER,
		/** Script complete — readyToPassive already set. */
		DONE
	}

	// -----------------------------------------------------------------------
	// GOAP config (used only while traveling to city, before script starts)
	// -----------------------------------------------------------------------

	private static final List<GoapAction> ACTIONS = List.of(
		new SellItemsGoapAction(),
		new RestockGoapAction(),
		new TeleportToCityGoapAction());

	private static final GoalSelector GOAL_SELECTOR = new GoalSelector(List.of(
		new RestockGoal(),
		new GoToCityGoal()));

	// -----------------------------------------------------------------------
	// Per-instance state
	// -----------------------------------------------------------------------

	private final Mode _mode;
	private boolean _arrivedInCity = false;
	private ScriptStep _step = ScriptStep.GROCER;

	public CityIdleBehaviour(Mode mode)
	{
		_mode = mode;
	}

	@Override
	public String getName()
	{
		return "CityIdle";
	}

	@Override
	public GoalSelector getGoalSelector()
	{
		return GOAL_SELECTOR;
	}

	@Override
	public List<GoapAction> getActions()
	{
		return ACTIONS;
	}

	/**
	 * While the city script is running GOAP replanning is suppressed.
	 * BotInstance.update() will call tickExecutor() directly instead.
	 */
	@Override
	public boolean isScripted()
	{
		return _arrivedInCity && (_mode == Mode.FOR_PASSIVE) && (_step != ScriptStep.DONE);
	}

	@Override
	public void onEnter(BotInstance bot, long now)
	{
		_arrivedInCity = false;
		_step = ScriptStep.GROCER;

		// If bot is still in the farm zone — teleport to city immediately.
		// Without this the bot would either walk on foot or wait for GOAP to trigger GoToCityGoal.
		if (bot.isInZone())
		{
			final BotZoneData city = bot.getZone().getCityData();
			final Location dest;
			if (city != null)
			{
				final List<Location> path = city.getCityPath();
				dest = path.isEmpty() ? city.getGatekeeper() : path.get(0);
			}
			else
			{
				dest = bot.getProfile().getZone().getHomeLocation();
			}
			bot.clearQueue();
			bot.teleport(dest.getX(), dest.getY(), dest.getZ());
			_arrivedInCity = true;

			if (_mode == Mode.FOR_ACTIVE)
			{
				bot.markReadyToActive(now);
				LOGGER.info("[" + bot.getPlayer().getName() + "] CityIdle: teleported to city → readyToActive");
			}
			else
			{
				LOGGER.info("[" + bot.getPlayer().getName() + "] CityIdle: teleported to city → starting city script");
			}
		}
		else
		{
			LOGGER.info("[" + bot.getPlayer().getName() + "] CityIdle: entered mode=" + _mode + " (already outside farm zone)");
		}
	}

	@Override
	public void onExit(BotInstance bot)
	{
		cancelTimer();
		bot.clearQueue();
	}

	@Override
	public void onTick(BotInstance bot, long now)
	{
		if (!_arrivedInCity)
		{
			if (!bot.isInZone())
			{
				_arrivedInCity = true;
				bot.clearQueue();

				if (_mode == Mode.FOR_ACTIVE)
				{
					bot.markReadyToActive(now);
					LOGGER.info("[" + bot.getPlayer().getName() + "] CityIdle: arrived → readyToActive");
				}
				else
				{
					LOGGER.info("[" + bot.getPlayer().getName() + "] CityIdle: arrived → starting city script");
				}
			}
			return;
		}

		// FOR_ACTIVE is done after signaling — nothing more to do.
		if (_mode == Mode.FOR_ACTIVE)
		{
			return;
		}

		// FOR_PASSIVE: advance the city script step-by-step when the executor is idle.
		if (!bot.isQueueIdle())
		{
			return;
		}

		advanceScript(bot, now);
	}

	// -----------------------------------------------------------------------
	// City script state machine
	// -----------------------------------------------------------------------

	private void advanceScript(BotInstance bot, long now)
	{
		final BotZoneData city = bot.getZone().getCityData();
		if (city == null)
		{
			// No city data for this zone — skip straight to done.
			LOGGER.warning("[" + bot.getPlayer().getName() + "] CityIdle: no cityData, skipping script");
			bot.markReadyToPassive(now);
			_step = ScriptStep.DONE;
			return;
		}

		switch (_step)
		{
			case GROCER:
			{
				final Location shop = city.getShop();
				bot.queueAction(new MoveToAction(shop.getX(), shop.getY(), shop.getZ(), 150));
				bot.queueAction(new SayAction(BotPhrases.random("grocer")));
				bot.queueAction(new WaitAction(2_000));
				// Inline sell + restock: instant service call, then short pause for effect.
				bot.queueAction(new org.l2jmobius.gameserver.bot.core.action.AbstractOneTimeAction()
				{
					@Override
					protected void doExecute(BotInstance b, long t)
					{
						SellService.sell(b);
						SupplyService.restock(b);
					}
				});
				bot.queueAction(new WaitAction(8_000));
				_step = ScriptStep.GUILD;
				break;
			}
			case GUILD:
			{
				final Location guild = city.getGuildmaster();
				bot.queueAction(new MoveToAction(guild.getX(), guild.getY(), guild.getZ(), 150));
				bot.queueAction(new SayAction(BotPhrases.random("guild")));
				bot.queueAction(new WaitAction(10_000));
				_step = ScriptStep.ARMOR;
				break;
			}
			case ARMOR:
			{
				// Gatekeeper is always near the weapon/armor shop in L2 cities.
				final Location gate = city.getGatekeeper();
				bot.queueAction(new MoveToAction(gate.getX(), gate.getY(), gate.getZ(), 200));
				bot.queueAction(new WaitAction(10_000));
				_step = ScriptStep.CENTER;
				break;
			}
			case CENTER:
			{
				final List<Location> path = city.getCityPath();
				final Location center = path.isEmpty() ? city.getGatekeeper() : path.get(0);
				bot.queueAction(new MoveToAction(center.getX(), center.getY(), center.getZ(), 150));
				bot.queueAction(new WaitAction(2_000));
				bot.markReadyToPassive(now);
				_step = ScriptStep.DONE;
				LOGGER.info("[" + bot.getPlayer().getName() + "] CityIdle: script done → readyToPassive");
				break;
			}
			case DONE:
				break;
		}
	}
}

