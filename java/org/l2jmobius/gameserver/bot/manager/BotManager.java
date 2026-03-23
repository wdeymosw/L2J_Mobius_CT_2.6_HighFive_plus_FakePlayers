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
import java.util.logging.Logger;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.bot.core.BotFactory;
import org.l2jmobius.gameserver.bot.core.BotSpawner;
import org.l2jmobius.gameserver.bot.model.BotInstance;
import org.l2jmobius.gameserver.bot.model.BotProfile;
import org.l2jmobius.gameserver.bot.model.BotType;
import org.l2jmobius.gameserver.bot.service.ThinkService;
import org.l2jmobius.gameserver.bot.zone.FarmZone;
import org.l2jmobius.gameserver.bot.zone.ZoneRegistry;
import org.l2jmobius.gameserver.config.custom.BotConfig;

/**
 * Central registry and scheduler for all active bots.
 * <p>
 * Two schedulers run on the same single thread:
 * - tick (300ms): calls ThinkService for every active bot
 * - spawn (configurable): gradually fills the world up to MaxBotsOnline
 */
public class BotManager
{
	private static final Logger LOGGER = Logger.getLogger(BotManager.class.getName());

	private static final String LOAD_BOT_IDS = "SELECT charId FROM characters WHERE is_bot=1";
	private static final long TICK_INTERVAL_MS = 300;

	/** objectId → active bot */
	private final Map<Integer, BotInstance> _bots = new ConcurrentHashMap<>();

	/** Pool of all bot character IDs loaded from DB, not yet spawned */
	private final List<Integer> _availableIds = new ArrayList<>();

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
	// Lifecycle
	// -------------------------------------------------------------------------

	/**
	 * Loads bot IDs from DB and starts tick + spawn schedulers.
	 * Call once at server startup (after DB is available).
	 */
	public void start()
	{
		if (!BotConfig.BOTS_ENABLED)
		{
			LOGGER.info("BotManager: disabled via config.");
			return;
		}

		loadBotIds();

		if (_availableIds.isEmpty())
		{
			LOGGER.warning("BotManager: no bot characters found in DB (is_bot=1). Nothing to spawn.");
			return;
		}

		_tickTask = _scheduler.scheduleAtFixedRate(this::tick, TICK_INTERVAL_MS, TICK_INTERVAL_MS, TimeUnit.MILLISECONDS);

		final long spawnIntervalMs = BotConfig.BOTS_SPAWN_INTERVAL_SECONDS * 1000L;
		// Initial delay 10s so the world finishes loading before first spawn attempt.
		_spawnTask = _scheduler.scheduleAtFixedRate(this::spawnBatch, 10_000L, spawnIntervalMs, TimeUnit.MILLISECONDS);

		LOGGER.info("BotManager: started. Available bot IDs: " + _availableIds.size() + ", max online: " + BotConfig.MAX_BOTS_ONLINE);
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
	 *
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

		LOGGER.info("BotManager: spawned " + bot.getPlayer().getName() + " (" + profile.getType() + ") in " + profile.getZone().getName());
		return bot;
	}

	/**
	 * Saves and removes the bot with the given objectId.
	 *
	 * @param objectId Player objectId
	 */
	public void removeBot(int objectId)
	{
		final BotInstance bot = _bots.remove(objectId);
		if (bot != null)
		{
			BotSpawner.removeBot(bot);
			ZoneRegistry.getInstance().unassignBot(objectId);
			// Return ID to pool so it can be re-spawned later
			_availableIds.add(objectId);
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
	// Gradual spawn
	// -------------------------------------------------------------------------

	private void spawnBatch()
	{
		final int needed = BotConfig.MAX_BOTS_ONLINE - _bots.size();
		if (needed <= 0)
		{
			return;
		}

		final int toSpawn = Math.min(needed, BotConfig.BOTS_SPAWN_BATCH_SIZE);
		int spawned = 0;

		for (int i = 0; (i < _availableIds.size()) && (spawned < toSpawn); i++)
		{
			final FarmZone zone = ZoneRegistry.getInstance().selectZone();
			if (zone == null)
			{
				LOGGER.warning("BotManager: all zones full, cannot spawn more bots.");
				break;
			}

			final int objectId = _availableIds.remove(i);
			final BotType type = pickType();
			final BotProfile profile = new BotProfile(objectId, type, zone, type == BotType.CORE ? 0.1f : 0.4f);

			if (spawnBot(profile) != null)
			{
				spawned++;
				i--; // list shifted after remove
			}
			else
			{
				// Failed to load — discard this ID
				LOGGER.warning("BotManager: failed to spawn objectId=" + objectId + ", skipping.");
			}
		}
	}

	/** Picks CORE or NOISE based on configured percentage. */
	private BotType pickType()
	{
		return (Math.random() * 100) < BotConfig.BOT_CORE_PERCENT ? BotType.CORE : BotType.NOISE;
	}

	// -------------------------------------------------------------------------
	// DB
	// -------------------------------------------------------------------------

	private void loadBotIds()
	{
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement(LOAD_BOT_IDS);
			ResultSet rs = ps.executeQuery())
		{
			while (rs.next())
			{
				_availableIds.add(rs.getInt("charId"));
			}
		}
		catch (Exception e)
		{
			LOGGER.warning("BotManager: failed to load bot IDs — " + e.getMessage());
		}
	}

	// -------------------------------------------------------------------------
	// Tick
	// -------------------------------------------------------------------------

	private void tick()
	{
		for (BotInstance bot : _bots.values())
		{
			try
			{
				if (bot.isSessionExpired())
				{
					removeBot(bot.getPlayer().getObjectId());
					continue;
				}
				ThinkService.think(bot);
			}
			catch (Exception e)
			{
				LOGGER.warning("BotManager: tick error for " + bot.getPlayer().getName() + " — " + e.getMessage());
			}
		}
	}
}
