/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.zone;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.l2jmobius.gameserver.model.Location;

/**
 * Parses {@code data/BotZones.xml} and returns a map of zone-name → {@link BotZoneData}.
 * <p>
 * Call {@link #load()} once at startup from {@link ZoneRegistry}.
 */
public class BotZoneDataLoader
{
	private static final Logger LOGGER = Logger.getLogger(BotZoneDataLoader.class.getName());

	/** Path relative to the working directory — isolated in the bot data folder. */
	private static final String XML_PATH = "data/bot/BotZones.xml";

	private BotZoneDataLoader()
	{
	}

	/**
	 * Parses BotZones.xml and returns the loaded data keyed by zone name.
	 * Returns an empty map if the file is missing or unparseable.
	 *
	 * @return unmodifiable map of zone name → {@link BotZoneData}
	 */
	public static Map<String, BotZoneData> load()
	{
		final File file = new File(".", XML_PATH);
		if (!file.exists())
		{
			LOGGER.warning("BotZoneDataLoader: " + XML_PATH + " not found — city walk disabled.");
			return Collections.emptyMap();
		}

		try
		{
			final Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file);
			doc.getDocumentElement().normalize();

			final Map<String, BotZoneData> result = new HashMap<>();
			final NodeList zones = doc.getElementsByTagName("zone");

			for (int i = 0; i < zones.getLength(); i++)
			{
				final Node zoneNode = zones.item(i);
				if (zoneNode.getNodeType() != Node.ELEMENT_NODE)
				{
					continue;
				}

				final Element zone = (Element) zoneNode;
				final String name = zone.getAttribute("name");

				final Location gatekeeper = parseFirst(zone, "gatekeeper");
				final Location shop = parseFirst(zone, "shop");
				final List<Location> cityPath = parsePath(zone);

				if (gatekeeper == null)
				{
					LOGGER.warning("BotZoneDataLoader: zone '" + name + "' has no <gatekeeper> — skipping.");
					continue;
				}

				result.put(name, new BotZoneData(gatekeeper, shop != null ? shop : gatekeeper, cityPath));
			}

			LOGGER.info("BotZoneDataLoader: loaded " + result.size() + " zone(s) from " + XML_PATH);
			return Collections.unmodifiableMap(result);
		}
		catch (Exception e)
		{
			LOGGER.warning("BotZoneDataLoader: failed to parse " + XML_PATH + " — " + e.getMessage());
			return Collections.emptyMap();
		}
	}

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	private static Location parseFirst(Element parent, String tag)
	{
		final NodeList nodes = parent.getElementsByTagName(tag);
		if (nodes.getLength() == 0)
		{
			return null;
		}
		return parseLocation((Element) nodes.item(0));
	}

	private static List<Location> parsePath(Element zone)
	{
		final NodeList pathNodes = zone.getElementsByTagName("cityPath");
		if (pathNodes.getLength() == 0)
		{
			return Collections.emptyList();
		}

		final Element pathEl = (Element) pathNodes.item(0);
		final NodeList points = pathEl.getElementsByTagName("point");
		final List<Location> path = new ArrayList<>(points.getLength());

		for (int i = 0; i < points.getLength(); i++)
		{
			path.add(parseLocation((Element) points.item(i)));
		}
		return path;
	}

	private static Location parseLocation(Element el)
	{
		final int x = Integer.parseInt(el.getAttribute("x"));
		final int y = Integer.parseInt(el.getAttribute("y"));
		final int z = Integer.parseInt(el.getAttribute("z"));
		return new Location(x, y, z);
	}
}
