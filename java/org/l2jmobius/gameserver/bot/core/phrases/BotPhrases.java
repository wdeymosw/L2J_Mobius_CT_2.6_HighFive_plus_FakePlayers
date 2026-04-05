/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.phrases;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.l2jmobius.gameserver.config.custom.BotConfig;

/**
 * Loads bot chat phrases from {@code data/bot/BotPhrases.xml}.
 * <p>
 * The XML uses {@code lang} attributes on {@code <phrase>} elements.
 * Only phrases matching {@link BotConfig#BOT_LANGUAGE} are loaded.
 * Falls back to {@code en} if no phrases exist for the configured language.
 * <p>
 * Usage: {@code BotPhrases.random("grocer")}
 */
public class BotPhrases
{
	private static final Logger LOGGER = Logger.getLogger(BotPhrases.class.getName());

	private static final String XML_PATH = "data/bot/BotPhrases.xml";
	private static final String FALLBACK_LANG = "en";

	/** category → list of phrases for the active language */
	private static final Map<String, List<String>> _phrases = new HashMap<>();

	private BotPhrases()
	{
	}

	/**
	 * Must be called once after {@link BotConfig#load()}.
	 * Re-entrant: clears and reloads on each call.
	 */
	public static synchronized void load()
	{
		_phrases.clear();

		final File file = new File(".", XML_PATH);
		if (!file.exists())
		{
			LOGGER.warning("BotPhrases: " + XML_PATH + " not found — phrases disabled.");
			return;
		}

		final String lang = BotConfig.BOT_LANGUAGE.toLowerCase();

		try
		{
			final Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file);
			doc.getDocumentElement().normalize();

			final NodeList categories = doc.getElementsByTagName("category");
			for (int i = 0; i < categories.getLength(); i++)
			{
				final Node node = categories.item(i);
				if (node.getNodeType() != Node.ELEMENT_NODE)
				{
					continue;
				}
				final Element cat = (Element) node;
				final String name = cat.getAttribute("name");

				List<String> list = parsePhrases(cat, lang);
				if (list.isEmpty() && !lang.equals(FALLBACK_LANG))
				{
					LOGGER.warning("BotPhrases: no phrases for category=" + name + " lang=" + lang + ", falling back to " + FALLBACK_LANG);
					list = parsePhrases(cat, FALLBACK_LANG);
				}
				_phrases.put(name, Collections.unmodifiableList(list));
			}

			LOGGER.info("BotPhrases: loaded lang=" + lang + " categories=" + _phrases.keySet());
		}
		catch (Exception e)
		{
			LOGGER.warning("BotPhrases: failed to parse " + XML_PATH + ": " + e.getMessage());
		}
	}

	/**
	 * Returns a random phrase for the given category using the loaded language.
	 * Returns an empty string if the category is unknown or phrases not loaded.
	 *
	 * @param category e.g. {@code "grocer"}, {@code "guild"}
	 * @return random phrase string
	 */
	public static String random(String category)
	{
		final List<String> list = _phrases.getOrDefault(category, Collections.emptyList());
		if (list.isEmpty())
		{
			return "";
		}
		return list.get(ThreadLocalRandom.current().nextInt(list.size()));
	}

	// -------------------------------------------------------------------------

	private static List<String> parsePhrases(Element category, String lang)
	{
		final NodeList nodes = category.getElementsByTagName("phrase");
		final List<String> result = new ArrayList<>();
		for (int i = 0; i < nodes.getLength(); i++)
		{
			final Node node = nodes.item(i);
			if (node.getNodeType() != Node.ELEMENT_NODE)
			{
				continue;
			}
			final Element el = (Element) node;
			if (lang.equalsIgnoreCase(el.getAttribute("lang")))
			{
				final String text = el.getTextContent().trim();
				if (!text.isEmpty())
				{
					result.add(text);
				}
			}
		}
		return result;
	}
}
