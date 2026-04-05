/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.model;

import org.l2jmobius.gameserver.bot.core.goap.GoapTuning;
import org.l2jmobius.gameserver.bot.core.service.SupplyService;
import org.l2jmobius.gameserver.bot.core.service.TargetService;
import org.l2jmobius.gameserver.model.Location;
import org.l2jmobius.gameserver.model.actor.Creature;
import org.l2jmobius.gameserver.model.actor.Player;

/**
 * Immutable snapshot of a bot's world state, built once per tick.
 * <p>
 * Contains only raw facts — no business thresholds, no decisions.
 * BotController reads this to evaluate environment transitions (→ RETREATING, → DEFENDING).
 * {@link org.l2jmobius.gameserver.bot.core.brain.BotBrain} reads this to resolve decisions
 * within a state (e.g. USE_POTION vs RECOVER vs SEARCH_TARGET).
 * <p>
 * Never access {@link Player} directly outside of this class and services.
 */
public class BotContext
{
	/** Current HP as a percentage 0–100. */
	public final double hpPercent;

	/** Current MP as a percentage 0–100. */
	public final double mpPercent;

	/** Currently locked target, or {@code null}. */
	public final Creature target;

	/** Whether the bot is inside its assigned farm zone. */
	public final boolean isInFarmZone;

	/** {@code true} when inventory slots are ≥ 90 % full. */
	public final boolean inventoryFull;

	/** {@code true} when shots/arrows are below the restock threshold. */
	public final boolean outOfAmmo;

	/** Raw inventory weight penalty level (0 = normal, 1 = yellow, 2 = red, 3 = black). */
	public final int weightPenalty;

	/** {@code true} when the target is alive and within physical attack range. */
	public final boolean canAttackTarget;

	/** Number of alive mobs currently targeting this bot (0 = no one attacking). */
	public final int attackerCount;

	/** {@code true} when the potion reuse timer has expired and the bot may drink again. */
	public final boolean potionReuseReady;

	/** Bot's current coordinates. */
	public final Location position;

	/** Role (MELEE, MAGE, ARCHER, ...). */
	public final BotRole role;

	private BotContext(double hpPercent, double mpPercent, Creature target, boolean isInFarmZone, boolean inventoryFull, boolean outOfAmmo, int weightPenalty, boolean canAttackTarget, int attackerCount, boolean potionReuseReady, Location position, BotRole role)
	{
		this.hpPercent = hpPercent;
		this.mpPercent = mpPercent;
		this.target = target;
		this.isInFarmZone = isInFarmZone;
		this.inventoryFull = inventoryFull;
		this.outOfAmmo = outOfAmmo;
		this.weightPenalty = weightPenalty;
		this.canAttackTarget = canAttackTarget;
		this.attackerCount = attackerCount;
		this.potionReuseReady = potionReuseReady;
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
	 * @param now current time in ms (used to evaluate potion reuse guard)
	 * @return immutable context snapshot for this tick
	 */
	public static BotContext of(BotInstance bot, long now)
	{
		final Player player = bot.getPlayer();
		final double hp = (player.getMaxHp() > 0) ? (player.getCurrentHp() / player.getMaxHp() * 100.0) : 0;
		final double mp = (player.getMaxMp() > 0) ? (player.getCurrentMp() / player.getMaxMp() * 100.0) : 100;
		final Creature target = bot.getTarget();
		// Use a generous range check: physicalAttackRange + 80 units tolerance.
		// PathService stops within ~60 units of the nav snapshot, and the mob may
		// have moved slightly, so a tight range check causes a false canAttackTarget=false.
		final boolean canAttack = (target != null) && !target.isDead() && (player.calculateDistance3D(target) < (player.getPhysicalAttackRange() + GoapTuning.ATTACK_RANGE_TOLERANCE));
		return new BotContext(
			hp,
			mp,
			target,
			bot.isInZone(),
			!player.isInventoryUnder90(false),
			SupplyService.needsRestock(bot),
			player.getWeightPenalty(),
			canAttack,
			TargetService.countAttackers(bot),
			now >= bot.getNextPotionTime(),
			new Location(player.getX(), player.getY(), player.getZ()),
			bot.getRole());
	}
}
