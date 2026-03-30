/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.goap;

/**
 * All boolean world facts used by the GOAP planner.
 * <p>
 * Continuous values (hpPercent, mpPercent) are bucketed into discrete facts
 * at WorldState build time. Facts are independent — multiple HP facts can be
 * true simultaneously (e.g. HP_LOW=true also implies HP_MID=true).
 */
public enum Fact
{
	// --- Health ---
	/** hpPercent < 20 — critical, triggers SurviveGoal. */
	HP_CRITICAL,
	/** hpPercent < 60 — potion threshold. */
	HP_LOW,
	/** hpPercent < 99 — standing regen. */
	HP_MID,
	/** hpPercent >= 99. */
	HP_FULL,

	// --- Mana ---
	/** mpPercent < 40 — sit-regen threshold. */
	MP_LOW,
	/** mpPercent >= 40. */
	MP_OK,
	/** mpPercent >= 99. */
	MP_FULL,

	// --- Combat ---
	/** At least one mob has this bot in its attack-by list or has targeted the bot. */
	UNDER_ATTACK,
	/** Bot has a living, locked target. */
	TARGET_EXISTS,
	/** Target is within physical attack range (canAttackTarget == true). */
	TARGET_IN_RANGE,
	/** No living target locked (goal-effect fact: "target is dead / gone"). */
	TARGET_DEAD,
	/** Bot is actively fighting: has a target or attackers. */
	IN_COMBAT,
	/** No attackers and no target. */
	THREAT_NEUTRALIZED,

	// --- Zone / position ---
	/** Bot is inside its assigned farm zone. */
	IN_FARM_ZONE,
	/** Bot is at city home location (not in farm zone). */
	IN_SAFE_PLACE,

	// --- Inventory / supplies ---
	/** Inventory not full and weight penalty < 2. */
	INVENTORY_OK,
	/** potionReuseReady == true. */
	POTION_READY,
	/** outOfAmmo == false. */
	HAS_AMMO,
	/** At least one healing potion present in inventory. */
	HAS_POTIONS,

	// --- Skills ---
	/** A usable SELF+HEAL skill exists and is not on cooldown. */
	HAS_HEAL_SKILL,

	// --- Player state ---
	/** player.isSitting(). */
	IS_SITTING,
	/** player.isDead(). */
	IS_DEAD,
}
