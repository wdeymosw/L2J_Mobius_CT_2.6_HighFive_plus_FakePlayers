/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.service;

import java.io.File;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.l2jmobius.gameserver.bot.core.model.BotRole;
import org.l2jmobius.gameserver.model.item.enums.ItemGrade;

/**
 * Loads bot gear set definitions from {@code data/bot/BotGearSets.xml}.
 * <p>
 * Gear slots per role per grade: [weapon, offhand, chest, legs, gloves, boots, helmet]<br>
 * Jewelry slots per grade: [earring, ring, necklace]
 * <p>
 * Call {@link #getInstance()} — loads once on first access.
 */
public class BotGearSetData
{
	private static final Logger LOGGER = Logger.getLogger(BotGearSetData.class.getName());

	private static final String XML_PATH = "data/bot/BotGearSets.xml";

	// Slot indices for gear arrays
	static final int WEAPON  = 0;
	static final int OFFHAND = 1;
	static final int CHEST   = 2;
	static final int LEGS    = 3;
	static final int GLOVES  = 4;
	static final int BOOTS   = 5;
	static final int HELMET  = 6;

	// Slot indices for jewelry arrays
	static final int EARRING  = 0;
	static final int RING     = 1;
	static final int NECKLACE = 2;

	// role → (grade → int[7])
	private final Map<BotRole, Map<ItemGrade, int[]>> _gearSets = new EnumMap<>(BotRole.class);

	// grade → int[3]
	private final Map<ItemGrade, int[]> _jewelrySets = new EnumMap<>(ItemGrade.class);

	private BotGearSetData()
	{
		load();
	}

	// -------------------------------------------------------------------------
	// Public API
	// -------------------------------------------------------------------------

	/**
	 * Returns the gear set for the given role and grade.
	 * If the exact grade is missing, falls back to the nearest lower grade.
	 * Returns null if no sets are defined for the role at all.
	 *
	 * @param role  bot role
	 * @param grade desired gear grade
	 * @return int[7] = [weapon, offhand, chest, legs, gloves, boots, helmet], or null
	 */
	public int[] getGearSet(BotRole role, ItemGrade grade)
	{
		final Map<ItemGrade, int[]> byGrade = _gearSets.get(role);
		if (byGrade == null)
		{
			return null;
		}
		// Try exact grade, then fall back through D→C→B→A order
		final ItemGrade[] fallback = { grade, ItemGrade.D, ItemGrade.C, ItemGrade.B, ItemGrade.A };
		for (ItemGrade g : fallback)
		{
			if (byGrade.containsKey(g))
			{
				return byGrade.get(g);
			}
		}
		return null;
	}

	/**
	 * Returns the jewelry set for the given grade.
	 * Falls back to D-grade if the grade is not defined.
	 *
	 * @param grade desired grade
	 * @return int[3] = [earring, ring, necklace], or null
	 */
	public int[] getJewelrySet(ItemGrade grade)
	{
		if (_jewelrySets.containsKey(grade))
		{
			return _jewelrySets.get(grade);
		}
		return _jewelrySets.get(ItemGrade.D);
	}

	// -------------------------------------------------------------------------
	// Loading
	// -------------------------------------------------------------------------

	private void load()
	{
		final File file = new File(".", XML_PATH);
		if (!file.exists())
		{
			LOGGER.warning("BotGearSetData: " + XML_PATH + " not found — GearService will have no sets.");
			return;
		}

		try
		{
			final Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file);
			doc.getDocumentElement().normalize();

			parseRoles(doc);
			parseJewelry(doc);

			LOGGER.info("BotGearSetData: loaded " + _gearSets.size() + " role(s), " + _jewelrySets.size() + " jewelry grade(s) from " + XML_PATH);
		}
		catch (Exception e)
		{
			LOGGER.warning("BotGearSetData: failed to parse " + XML_PATH + " — " + e.getMessage());
		}
	}

	private void parseRoles(Document doc)
	{
		final NodeList roleNodes = doc.getElementsByTagName("role");
		for (int i = 0; i < roleNodes.getLength(); i++)
		{
			final Node node = roleNodes.item(i);
			if (node.getNodeType() != Node.ELEMENT_NODE)
			{
				continue;
			}

			final Element roleEl = (Element) node;
			final String roleName = roleEl.getAttribute("name");
			BotRole role;
			try
			{
				role = BotRole.valueOf(roleName);
			}
			catch (IllegalArgumentException e)
			{
				LOGGER.warning("BotGearSetData: unknown role '" + roleName + "' — skipping.");
				continue;
			}

			final Map<ItemGrade, int[]> gradeMap = new EnumMap<>(ItemGrade.class);
			final NodeList gradeNodes = roleEl.getElementsByTagName("grade");
			for (int j = 0; j < gradeNodes.getLength(); j++)
			{
				final Element gradeEl = (Element) gradeNodes.item(j);
				final ItemGrade grade = parseGrade(gradeEl.getAttribute("name"));
				if (grade == null)
				{
					continue;
				}

				final int[] slots = new int[7];
				slots[WEAPON]  = parseInt(gradeEl, "weapon",  0);
				slots[OFFHAND] = parseInt(gradeEl, "offhand", 0);
				slots[CHEST]   = parseInt(gradeEl, "chest",   0);
				slots[LEGS]    = parseInt(gradeEl, "legs",    0);
				slots[GLOVES]  = parseInt(gradeEl, "gloves",  0);
				slots[BOOTS]   = parseInt(gradeEl, "boots",   0);
				slots[HELMET]  = parseInt(gradeEl, "helmet",  0);
				gradeMap.put(grade, slots);
			}

			_gearSets.put(role, Collections.unmodifiableMap(gradeMap));
		}
	}

	private void parseJewelry(Document doc)
	{
		final NodeList jewelryNodes = doc.getElementsByTagName("jewelry");
		if (jewelryNodes.getLength() == 0)
		{
			return;
		}

		final Element jewelryEl = (Element) jewelryNodes.item(0);
		final NodeList gradeNodes = jewelryEl.getElementsByTagName("grade");
		for (int i = 0; i < gradeNodes.getLength(); i++)
		{
			final Element gradeEl = (Element) gradeNodes.item(i);
			final ItemGrade grade = parseGrade(gradeEl.getAttribute("name"));
			if (grade == null)
			{
				continue;
			}

			final int[] slots = new int[3];
			slots[EARRING]  = parseInt(gradeEl, "earring",  0);
			slots[RING]     = parseInt(gradeEl, "ring",     0);
			slots[NECKLACE] = parseInt(gradeEl, "necklace", 0);
			_jewelrySets.put(grade, slots);
		}
	}

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	private static ItemGrade parseGrade(String name)
	{
		switch (name.toUpperCase())
		{
			case "D": return ItemGrade.D;
			case "C": return ItemGrade.C;
			case "B": return ItemGrade.B;
			case "A": return ItemGrade.A;
			case "S": return ItemGrade.S;
			default:
				LOGGER.warning("BotGearSetData: unknown grade '" + name + "' — skipping.");
				return null;
		}
	}

	private static int parseInt(Element el, String attr, int defaultValue)
	{
		final String val = el.getAttribute(attr);
		if (val == null || val.isEmpty())
		{
			return defaultValue;
		}
		try
		{
			return Integer.parseInt(val);
		}
		catch (NumberFormatException e)
		{
			LOGGER.warning("BotGearSetData: invalid int '" + val + "' for attribute '" + attr + "'");
			return defaultValue;
		}
	}

	// -------------------------------------------------------------------------
	// Singleton
	// -------------------------------------------------------------------------

	public static BotGearSetData getInstance()
	{
		return SingletonHolder.INSTANCE;
	}

	private static final class SingletonHolder
	{
		static final BotGearSetData INSTANCE = new BotGearSetData();
	}
}
