/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.manager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.bot.core.behaviour.BotBehaviourEvent;
import org.l2jmobius.gameserver.bot.core.behaviour.PartyBehaviour;
import org.l2jmobius.gameserver.bot.core.model.BotInstance;
import org.l2jmobius.gameserver.bot.core.model.BotProfile;
import org.l2jmobius.gameserver.bot.core.model.BotRole;
import org.l2jmobius.gameserver.bot.core.model.BotType;
import org.l2jmobius.gameserver.bot.core.zone.FarmZone;
import org.l2jmobius.gameserver.bot.core.zone.ZoneRegistry;
import org.l2jmobius.gameserver.config.custom.BotConfig;

/**
 * Central registry and scheduler for all active bots.
 * Two schedulers run on the same single thread:
 * - tick (300ms): calls ThinkService for every active bot
 * - spawn (configurable): gradually fills the world up to MaxBotsOnline
 */
public class BotManager
{
	private static final Logger LOGGER = Logger.getLogger(BotManager.class.getName());

	private static final String LOAD_BOTS = "SELECT charId, level, x, y FROM characters WHERE is_bot=1";
	private static final long TICK_INTERVAL_MS = 300;
	private static final long STATUS_LOG_INTERVAL_MS = 5000;
	private long _lastStatusLog = 0;

	/** objectId → active bot */
	private final Map<Integer, BotInstance> _bots = new ConcurrentHashMap<>();

	/** charId → pool entry for bots not yet spawned */
	private final Map<Integer, PoolEntry> _availablePool = new ConcurrentHashMap<>();

	private final ScheduledExecutorService _scheduler = Executors.newSingleThreadScheduledExecutor(r ->
	{
		final Thread t = new Thread(r, "BotManager-Scheduler");
		t.setDaemon(true);
		return t;
	});

	private ScheduledFuture<?> _tickTask;
	private ScheduledFuture<?> _spawnTask;

	// -------------------------------------------------------------------------
	// Singleton
	// -------------------------------------------------------------------------

	private BotManager()
	{
	}

	public static BotManager getInstance()
	{
		return SingletonHolder.INSTANCE;
	}

	private static class SingletonHolder
	{
		static final BotManager INSTANCE = new BotManager();
	}

	// -------------------------------------------------------------------------
	// Pool entry — level + last known position for zone selection
	// -------------------------------------------------------------------------

	private static class PoolEntry
	{
		final int level;
		final int x;
		final int y;

		PoolEntry(int level, int x, int y)
		{
			this.level = level;
			this.x = x;
			this.y = y;
		}
	}

	// -------------------------------------------------------------------------
	// Lifecycle
	// -------------------------------------------------------------------------

	/**
	 * Loads bot characters from DB and starts tick + spawn schedulers.
	 * Call once at server startup (after DB is available).
	 */
	public void start()
	{
		if (!BotConfig.BOTS_ENABLED)
		{
			LOGGER.info("BotManager: disabled via config.");
			return;
		}

		ZoneRegistry.getInstance().resetBotAssignments();
		loadBots();

		// Enable per-tick debug logging — disable in production.
		org.l2jmobius.gameserver.bot.core.goap.GoapAgent.DEBUG = true;

		if (_availablePool.isEmpty())
		{
			LOGGER.warning("BotManager: no bot characters found in DB (is_bot=1). Nothing to spawn.");
			return;
		}

		_tickTask = _scheduler.scheduleAtFixedRate(this::tick, TICK_INTERVAL_MS, TICK_INTERVAL_MS, TimeUnit.MILLISECONDS);

		final long spawnIntervalMs = BotConfig.BOTS_SPAWN_INTERVAL_SECONDS * 1000L;
		final long initialDelayMs = BotConfig.BOTS_INITIAL_DELAY_SECONDS * 1000L;
		_spawnTask = _scheduler.scheduleAtFixedRate(this::spawnBatch, initialDelayMs, spawnIntervalMs, TimeUnit.MILLISECONDS);

		LOGGER.info("BotManager: started. Pool size: " + _availablePool.size() + ", max online: " + BotConfig.MAX_BOTS_ONLINE);
	}

	/** Stops schedulers and removes all active bots cleanly. */
	public void shutdown()
	{
		if (_tickTask != null)
		{
			_tickTask.cancel(false);
			_tickTask = null;
		}
		if (_spawnTask != null)
		{
			_spawnTask.cancel(false);
			_spawnTask = null;
		}
		removeAll();
		_scheduler.shutdown();
		LOGGER.info("BotManager: shutdown complete.");
	}

	// -------------------------------------------------------------------------
	// Bot management
	// -------------------------------------------------------------------------

	/**
	 * Spawns a bot from the given profile and registers it.
	 * @param profile bot configuration
	 * @return the created BotInstance, or null on failure
	 */
	public BotInstance spawnBot(BotProfile profile)
	{
		final BotInstance bot = BotFactory.create(profile);
		if (bot == null)
		{
			return null;
		}

		BotSpawner.spawnBot(bot);

		final int objectId = bot.getPlayer().getObjectId();
		_bots.put(objectId, bot);
		ZoneRegistry.getInstance().assignBot(objectId, profile.getZone());

		LOGGER.info("BotManager: spawned " + bot.getPlayer().getName() + " (" + profile.getType() + ") in zone \"" + profile.getZone().getName() + "\"");
		return bot;
	}

	/**
	 * Saves and removes the bot with the given objectId.
	 * Returns the character to the available pool so it can be re-spawned later.
	 * @param objectId Player objectId
	 */
	public void removeBot(int objectId)
	{
		final BotInstance bot = _bots.remove(objectId);
		if (bot != null)
		{
			final int level = bot.getPlayer().getLevel();
			final int x = bot.getPlayer().getX();
			final int y = bot.getPlayer().getY();
			BotSpawner.removeBot(bot);
			ZoneRegistry.getInstance().unassignBot(objectId);
			_availablePool.put(objectId, new PoolEntry(level, x, y));
		}
	}

	/** Saves and removes all active bots. */
	public void removeAll()
	{
		for (BotInstance bot : _bots.values())
		{
			BotSpawner.removeBot(bot);
			ZoneRegistry.getInstance().unassignBot(bot.getPlayer().getObjectId());
		}
		_bots.clear();
		LOGGER.info("BotManager: all bots removed.");
	}

	public Collection<BotInstance> getBots()
	{
		return Collections.unmodifiableCollection(_bots.values());
	}

	public int getBotCount()
	{
		return _bots.size();
	}

	// -------------------------------------------------------------------------
	// Party events (future: PartyBehaviour integration)
	// -------------------------------------------------------------------------

	/**
	 * Called when a player invites a bot to a party with a specific role.
	 * Transitions the bot to {@link PartyBehaviour} with the assigned role.
	 * <p>
	 * TODO: Wire this to the actual party invite packet handler.
	 *
	 * @param objectId the bot's Player objectId
	 * @param role     the role assigned in the party
	 */
	public void onPartyInvite(int objectId, BotRole role)
	{
		final BotInstance bot = _bots.get(objectId);
		if (bot == null)
		{
			return;
		}
		final long now = System.currentTimeMillis();
		final PartyBehaviour partyBehaviour = new PartyBehaviour();
		partyBehaviour.setPartyRole(role);
		bot.getBehaviourController().transition(partyBehaviour, bot, now);
		LOGGER.info("BotManager: " + bot.getPlayer().getName() + " joined party as " + role);
	}

	/**
	 * Called when the bot's party is dissolved.
	 * Returns the bot to PvE farming.
	 *
	 * @param objectId the bot's Player objectId
	 */
	public void onPartyDissolve(int objectId)
	{
		final BotInstance bot = _bots.get(objectId);
		if (bot == null)
		{
			return;
		}
		final long now = System.currentTimeMillis();
		bot.getBehaviourController().dispatch(BotBehaviourEvent.PARTY_DISSOLVE, bot, now, null);
		LOGGER.info("BotManager: " + bot.getPlayer().getName() + " party dissolved, returning to PvE");
	}

	// -------------------------------------------------------------------------
	// Gradual spawn
	// -------------------------------------------------------------------------

	private void spawnBatch()
	{
		final int needed = BotConfig.MAX_BOTS_ONLINE - _bots.size();
		if ((needed <= 0) || _availablePool.isEmpty())
		{
			return;
		}

		final List<Map.Entry<Integer, PoolEntry>> entries = new ArrayList<>(_availablePool.entrySet());
		final int toSpawn = Math.min(needed, Math.min(BotConfig.BOTS_SPAWN_BATCH_SIZE, entries.size()));
		int spawned = 0;

		for (Map.Entry<Integer, PoolEntry> entry : entries)
		{
			if (spawned >= toSpawn)
			{
				break;
			}

			final int charId = entry.getKey();
			final PoolEntry pe = entry.getValue();

			// Pick zone closest to the bot's last saved position (among level-appropriate zones).
			final FarmZone zone = ZoneRegistry.getInstance().selectZone(pe.level, pe.x, pe.y);
			if (zone == null)
			{
				LOGGER.warning("BotManager: no zone found for level " + pe.level + ", skipping charId=" + charId);
				continue;
			}

			_availablePool.remove(charId);
			final BotType type = pickType();
			final BotProfile profile = new BotProfile(charId, type, zone, type == BotType.ACTIVE ? 0.1f : 0.4f);

			if (spawnBot(profile) != null)
			{
				spawned++;
			}
			else
			{
				LOGGER.warning("BotManager: failed to spawn charId=" + charId + ", discarding.");
			}
		}
	}

	// Picks ACTIVE or PASSIVE based on configured percentage.
	private BotType pickType()
	{
		return (Math.random() * 100) < BotConfig.BOT_ACTIVE_PERCENT ? BotType.ACTIVE : BotType.PASSIVE;
	}

	// -------------------------------------------------------------------------
	// DB
	// -------------------------------------------------------------------------

	private void loadBots()
	{
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement(LOAD_BOTS);
			ResultSet rs = ps.executeQuery())
		{
			while (rs.next())
			{
				_availablePool.put(rs.getInt("charId"), new PoolEntry(rs.getInt("level"), rs.getInt("x"), rs.getInt("y")));
			}
		}
		catch (Exception e)
		{
			LOGGER.warning("BotManager: failed to load bots from DB — " + e.getMessage());
		}
	}

	// -------------------------------------------------------------------------
	// Tick
	// -------------------------------------------------------------------------

	private void tick()
	{
		final long now = System.currentTimeMillis();
		final boolean doStatus = (now - _lastStatusLog) >= STATUS_LOG_INTERVAL_MS;
		if (doStatus)
		{
			_lastStatusLog = now;
		}

		for (BotInstance bot : _bots.values())
		{
			try
			{
				if (bot.isSessionExpired())
				{
					removeBot(bot.getPlayer().getObjectId());
					continue;
				}
				if (doStatus)
				{
					final String behaviourName = (bot.getBehaviourController() != null)
						? bot.getBehaviourController().getActive().getName()
						: "none";
					LOGGER.info("BotManager: [" + bot.getPlayer().getName() + "] behaviour=" + behaviourName + " phase=" + bot.getPhase() + " pos=" + bot.getPlayer().getX() + "," + bot.getPlayer().getY() + " isMoving=" + bot.getPlayer().isMoving());
				}
				bot.update(now);
			}
			catch (Throwable e)
			{
				LOGGER.log(Level.SEVERE, "BotManager: tick error for " + bot.getPlayer().getName(), e);
			}
		}
	}
}
