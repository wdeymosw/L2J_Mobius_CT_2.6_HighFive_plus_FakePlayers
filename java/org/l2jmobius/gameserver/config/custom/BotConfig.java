/*
 * Copyright (c) 2013 L2jMobius
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.l2jmobius.gameserver.config.custom;

import org.l2jmobius.commons.util.ConfigReader;

/**
 * Bot Orchestrator configuration.
 */
public class BotConfig
{
	private static final String BOT_CONFIG_FILE = "./config/Custom/Bots.ini";

	public static boolean BOTS_ENABLED;
	public static int MAX_BOTS_ONLINE;
	public static int BOTS_SPAWN_BATCH_SIZE;
	public static int BOTS_SPAWN_INTERVAL_SECONDS;
	public static int BOT_CORE_PERCENT;
	public static double BOT_XP_MULTIPLIER;
	public static double BOT_DROP_MULTIPLIER;

	public static void load()
	{
		final ConfigReader config = new ConfigReader(BOT_CONFIG_FILE);
		BOTS_ENABLED = config.getBoolean("EnableBots", false);
		MAX_BOTS_ONLINE = config.getInt("MaxBotsOnline", 50);
		BOTS_SPAWN_BATCH_SIZE = config.getInt("BotsSpawnBatchSize", 3);
		BOTS_SPAWN_INTERVAL_SECONDS = config.getInt("BotsSpawnIntervalSeconds", 60);
		BOT_CORE_PERCENT = config.getInt("BotCorePercent", 30);
		BOT_XP_MULTIPLIER = config.getDouble("BotXpMultiplier", 0.1);
		BOT_DROP_MULTIPLIER = config.getDouble("BotDropMultiplier", 0.1);
	}
}
