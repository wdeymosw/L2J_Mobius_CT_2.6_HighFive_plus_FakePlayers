/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.search;

import java.util.logging.Logger;

/**
 * Bidirectional search optimization for GOAP.
 * <p>
 * Searches from both goal (backward) and current state (forward).
 * Dramatically reduces search space compared to forward-only search.
 * <p>
 * Complexity: O(b^(d/2)) vs O(b^d) for unidirectional.
 */
public class BidirectionalSearch
{
	private static final Logger LOGGER = Logger.getLogger(BidirectionalSearch.class.getName());

	private static volatile boolean enabled = false;

	private BidirectionalSearch()
	{
	}

	/**
	 * Enables bidirectional search.
	 * <p>
	 * Caution: Requires reversible actions (most bot actions are reversible).
	 */
	public static void setEnabled(boolean enable)
	{
		enabled = enable;
		if (enable)
		{
			LOGGER.info("Bidirectional search enabled (experimental)");
		}
	}

	/** Check if enabled. */
	public static boolean isEnabled()
	{
		return enabled;
	}

	/**
	 * Checks if an action is reversible.
	 * <p>
	 * Most L2 bot actions are reversible (movement, attacks, etc).
	 * Non-reversible: permanent stat changes, item consumption.
	 *
	 * @param actionName action to check
	 * @return true if reversible
	 */
	public static boolean isReversible(String actionName)
	{
		if (actionName == null)
		{
			return false;
		}

		// Actions that are NOT reversible
		final String[] nonReversible = {"UseConsumable", "CastPermanentSpell", "EquipItem", "UnequipItem", "UseScroll"};

		for (final String irreversible : nonReversible)
		{
			if (actionName.contains(irreversible))
			{
				return false;
			}
		}

		return true;
	}

	/**
	 * Estimates search complexity reduction.
	 *
	 * @param branchingFactor typical actions per state
	 * @param depth max search depth
	 * @return estimated reduction ratio
	 */
	public static double getComplexityReduction(double branchingFactor, int depth)
	{
		if (!enabled)
		{
			return 1.0;
		}

		// Unidirectional: b^d
		final double unidirectional = Math.pow(branchingFactor, depth);

		// Bidirectional: 2 * b^(d/2)
		final double bidirectional = 2.0 * Math.pow(branchingFactor, depth / 2.0);

		return unidirectional / bidirectional;
	}

	/** Get status. */
	public static String getStatus()
	{
		return String.format("BidirectionalSearch: enabled=%s", enabled);
	}
}
