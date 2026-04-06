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
package handlers.admincommandhandlers;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.l2jmobius.gameserver.handler.IAdminCommandHandler;
import org.l2jmobius.gameserver.model.actor.Player;

public class AdminCollectBotZones implements IAdminCommandHandler
{
	private static final Logger LOGGER = Logger.getLogger(AdminCollectBotZones.class.getName());

	private static final String[] ADMIN_COMMANDS =
	{
		"admin_bz_start",
		"admin_bz_add",
		"admin_bz_list",
		"admin_bz_save",
		"admin_bz_clear"
	};

	private static final Map<Integer, ZoneCollector> _collectors = new HashMap<>();

	private static class ZoneData
	{
		String name;
		int x, y, z;

		ZoneData(String name, int x, int y, int z)
		{
			this.name = name;
			this.x = x;
			this.y = y;
			this.z = z;
		}
	}

	private static class ZoneCollector
	{
		String zoneName;
		List<ZoneData> points = new ArrayList<>();
	}

	@Override
	public boolean onCommand(String command, Player activeChar)
	{
		if (command.startsWith("admin_bz_start"))
		{
			try
			{
				final String params = command.substring("admin_bz_start".length()).trim();
				if (params.isEmpty())
				{
					activeChar.sendSysMessage("Usage: //admin_bz_start <zone_name>");
					return true;
				}

				ZoneCollector collector = new ZoneCollector();
				collector.zoneName = params;
				_collectors.put(activeChar.getObjectId(), collector);
				activeChar.sendSysMessage("Зона '" + params + "' начата. //admin_bz_add");
			}
			catch (Exception e)
			{
				activeChar.sendSysMessage("Error: " + e.getMessage());
			}
		}
		else if (command.equals("admin_bz_add"))
		{
			try
			{
				ZoneCollector collector = _collectors.get(activeChar.getObjectId());
				if (collector == null)
				{
					activeChar.sendSysMessage("Начните сначала: //admin_bz_start <zone_name>");
					return true;
				}

				String pointName = "Point_" + (collector.points.size() + 1);
				ZoneData point = new ZoneData(pointName, activeChar.getX(), activeChar.getY(), activeChar.getZ());
				collector.points.add(point);
				activeChar.sendSysMessage("✓ " + pointName + " (" + activeChar.getX() + "," + activeChar.getY() + "," + activeChar.getZ() + ")");
			}
			catch (Exception e)
			{
				activeChar.sendSysMessage("Error: " + e.getMessage());
			}
		}
		else if (command.equals("admin_bz_list"))
		{
			ZoneCollector collector = _collectors.get(activeChar.getObjectId());
			if (collector == null)
			{
				activeChar.sendSysMessage("Нет активной зоны.");
				return true;
			}

			activeChar.sendSysMessage("=== " + collector.zoneName + " (" + collector.points.size() + ") ===");
			for (int i = 0; i < collector.points.size(); i++)
			{
				ZoneData p = collector.points.get(i);
				activeChar.sendSysMessage(i + 1 + ". " + p.name + " X:" + p.x + " Y:" + p.y + " Z:" + p.z);
			}
		}
		else if (command.equals("admin_bz_save"))
		{
			ZoneCollector collector = _collectors.get(activeChar.getObjectId());
			if (collector == null)
			{
				activeChar.sendSysMessage("Нет активной зоны.");
				return true;
			}

			if (collector.points.isEmpty())
			{
				activeChar.sendSysMessage("Нет точек.");
				return true;
			}

			try
			{
				File logDir = new File("./log");
				if (!logDir.exists())
				{
					logDir.mkdirs();
				}

				String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
				String filename = "./log/botzones_" + collector.zoneName.replace(" ", "_") + "_" + timestamp + ".xml";

				try (FileWriter writer = new FileWriter(filename))
				{
					writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
					writer.write("<zone name=\"" + escapeXml(collector.zoneName) + "\">\n");

					for (ZoneData point : collector.points)
					{
						writer.write("\t<point name=\"" + escapeXml(point.name) + "\" x=\"" + point.x + "\" y=\"" + point.y + "\" z=\"" + point.z + "\"/>\n");
					}

					writer.write("</zone>\n");
				}

				activeChar.sendSysMessage("Сохранено: " + filename);
				LOGGER.info("Zone '" + collector.zoneName + "' saved (" + collector.points.size() + " points)");
			}
			catch (IOException e)
			{
				activeChar.sendSysMessage("Ошибка: " + e.getMessage());
			}
		}
		else if (command.equals("admin_bz_clear"))
		{
			_collectors.remove(activeChar.getObjectId());
			activeChar.sendSysMessage("Очищено.");
		}

		return true;
	}

	private static String escapeXml(String str)
	{
		return str.replace("&", "&amp;")
			.replace("<", "&lt;")
			.replace(">", "&gt;")
			.replace("\"", "&quot;")
			.replace("'", "&apos;");
	}

	@Override
	public String[] getCommandList()
	{
		return ADMIN_COMMANDS;
	}
}
