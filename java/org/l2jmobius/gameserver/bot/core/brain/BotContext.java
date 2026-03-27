/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.brain;

import org.l2jmobius.gameserver.bot.core.model.BotInstance;
import org.l2jmobius.gameserver.bot.core.model.BotRole;
import org.l2jmobius.gameserver.bot.core.service.SupplyService;
import org.l2jmobius.gameserver.model.Location;
import org.l2jmobius.gameserver.model.actor.Creature;
import org.l2jmobius.gameserver.model.actor.Player;

/**
 * Immutable snapshot of a bot's world state, built once per tick.
 * <p>
 * {@link BotBrain} reads only this — never the {@link Player} directly.
 */
public class BotContext
{
	/** Current HP as a percentage 0–100. */
	public final double hpPercent;

	/** Currently locked target, or {@code null}. */
	public final Creature target;

	/** Whether the bot is inside its assigned farm zone. */
	public final boolean isInFarmZone;

	/** {@code true} when HP is below the retreat threshold. */
	public final boolean lowHp;

	/** {@code true} when inventory slots are ≥ 90 % full. */
	public final boolean inventoryFull;

	/** {@code true} when shots/arrows are below the restock threshold. */
	public final boolean outOfAmmo;

	/** Bot's current coordinates. */
	public final Location position;

	/** Role (MELEE, MAGE, ARCHER, ...). */
	public final BotRole role;

	private static final double LOW_HP_THRESHOLD = 20.0;

	private BotContext(double hpPercent, Creature target, boolean isInFarmZone, boolean lowHp, boolean inventoryFull, boolean outOfAmmo, Location position, BotRole role)
	{
		this.hpPercent = hpPercent;
		this.target = target;
		this.isInFarmZone = isInFarmZone;
		this.lowHp = lowHp;
		this.inventoryFull = inventoryFull;
		this.outOfAmmo = outOfAmmo;
		this.position = position;
		this.role = role;
	}

	/** @return {@code true} if the bot has a living target. */
	public boolean hasTarget()
	{
		return (target != null) && !target.isDead();
	}

	/**
	 * Builds a context snapshot from the given bot's current state.
	 *
	 * @param bot the bot to snapshot
	 * @return immutable context for this tick
	 */
	public static BotContext of(BotInstance bot)
	{
		final Player player = bot.getPlayer();
		final double hp = (player.getMaxHp() > 0) ? (player.getCurrentHp() / player.getMaxHp() * 100.0) : 0;
		return new BotContext(
			hp,
			bot.getTarget(),
			bot.isInZone(),
			hp < LOW_HP_THRESHOLD,
			!player.isInventoryUnder90(false),
			SupplyService.needsRestock(bot),
			new Location(player.getX(), player.getY(), player.getZ()),
			bot.getRole());
	}
}
