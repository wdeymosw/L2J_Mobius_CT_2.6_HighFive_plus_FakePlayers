/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.monitor;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Logger;

import org.l2jmobius.gameserver.bot.core.cache.WorldStateCache;
import org.l2jmobius.gameserver.bot.core.logging.BotMetrics;
import org.l2jmobius.gameserver.bot.core.pool.PoolingMetrics;

/**
 * Periodic metrics dump to file for performance analysis.
 * <p>
 * Writes JSON-formatted metrics every interval for offline analysis.
 */
public class MetricsDumper
{
	private static final Logger LOGGER = Logger.getLogger(MetricsDumper.class.getName());
	private static final String METRICS_DIR = "bot_metrics";
	private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");

	private MetricsDumper()
	{
	}

	/**
	 * Dumps current metrics to file.
	 *
	 * @return path to created file or null if failed
	 */
	public static String dumpMetrics()
	{
		try
		{
			// Create directory if needed
			final File dir = new File(METRICS_DIR);
			if (!dir.exists())
			{
				dir.mkdirs();
			}

			// Generate filename with timestamp
			final String timestamp = TIME_FORMAT.format(new Date());
			final File file = new File(dir, "metrics_" + timestamp + ".json");

			// Write metrics in JSON format
			try (FileWriter writer = new FileWriter(file))
			{
				writer.write(buildMetricsJson());
			}

			LOGGER.info("Metrics dumped to: " + file.getAbsolutePath());
			return file.getAbsolutePath();
		}
		catch (IOException e)
		{
			LOGGER.severe("Failed to dump metrics: " + e.getMessage());
			return null;
		}
	}

	private static String buildMetricsJson()
	{
		final StringBuilder sb = new StringBuilder();

		sb.append("{\n");
		sb.append("  \"timestamp\": \"").append(new Date()).append("\",\n");
		sb.append("  \"metrics\": {\n");
		sb.append("    \"actions\": ").append(BotMetrics.getActionExecutedCount()).append(",\n");
		sb.append("    \"plans\": ").append(BotMetrics.getPlanBuiltCount()).append(",\n");
		sb.append("    \"exceptions\": ").append(BotMetrics.getExceptionRecoverableCount()).append(",\n");
		sb.append("    \"timeouts\": ").append(BotMetrics.getActionTimeoutCount()).append("\n");
		sb.append("  },\n");
		sb.append("  \"performance\": {\n");
		sb.append("    \"cache_rate\": \"").append(WorldStateCache.getStats()).append("\",\n");
		sb.append("    \"pool_reuse\": ").append(PoolingMetrics.getReuseRate()).append("\n");
		sb.append("  }\n");
		sb.append("}\n");

		return sb.toString();
	}

	/** Start periodic dumping (every 60 seconds). */
	public static void startPeriodicDumping()
	{
		final Thread dumper = new Thread(() ->
		{
			while (true)
			{
				try
				{
					Thread.sleep(60000); // Every 60 seconds
					dumpMetrics();
				}
				catch (InterruptedException e)
				{
					break;
				}
			}
		});

		dumper.setDaemon(true);
		dumper.setName("BotMetricsDumper");
		dumper.start();
	}
}
