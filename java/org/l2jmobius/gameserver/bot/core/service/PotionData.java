/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.service;

/**
 * Shared healing potion constants used by DrinkPotionAction and {@link SupplyService}.
 * Single source of truth for potion IDs and reuse duration.
 */
public final class PotionData
{
	/**
	 * Healing potion item IDs in priority order (best → worst).
	 * <ul>
	 *   <li>1539 — Greater Healing Potion (quick regen)</li>
	 *   <li>1540 — Quick Healing Potion</li>
	 *   <li>1061 — Greater Healing Potion (slow regen)</li>
	 *   <li>1060 — Lesser Healing Potion</li>
	 * </ul>
	 */
	public static final int[] HEAL_POTION_IDS = { 1539, 1540, 1061, 1060 };

	/** How long (ms) the bot waits before attempting another potion. */
	public static final long POTION_REUSE_MS = 30_000;

	private PotionData()
	{
	}
}
