/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.goap;

/**
 * Central registry of all bot decision-making and behaviour constants.
 * <p>
 * Everything a developer might want to tune — goal priorities, action costs,
 * HP/MP thresholds, timeouts, distances, economy rates — lives here so that
 * no magic numbers are scattered across individual action, service, or goal classes.
 * <p>
 * <b>Goal priority rules:</b> higher number = higher urgency.
 * Goals returning priority {@code 0} are inactive and never selected.<br>
 * <b>Action cost rules:</b> lower cost = planner prefers this action.
 * The planner minimises total plan cost via backward-chaining A*.
 */
public final class GoapTuning
{
	private GoapTuning()
	{
	}

	// =========================================================================
	// Goal priorities
	// =========================================================================

	/** HP {@literal <} {@link #HP_CRITICAL_THRESHOLD}% — escape to city immediately. */
	public static final int PRIORITY_SURVIVE = 100;

	/** Under attack and not critically wounded — neutralise the threat. */
	public static final int PRIORITY_DEFEND = 90;

	/** HP low + potion ready — drink mid-fight before farming continues. */
	public static final int PRIORITY_DRINK_POTION = 52;

	/** Living target exists — finish the kill. */
	public static final int PRIORITY_FARM = 50;

	/** Loot on ground after a kill — pick it up before buffing. */
	public static final int PRIORITY_PICKUP = 48;

	/** Buffs missing and not in combat — apply self-buffs before hunting. */
	public static final int PRIORITY_BUFF = 47;

	/** In farm zone, healthy, no target — search for next mob. */
	public static final int PRIORITY_HUNT = 45;

	/** Not in combat, HP or MP low — sit and regenerate. */
	public static final int PRIORITY_RESTORE = 40;

	/** Out of ammo or inventory full — travel to city to sell/restock. */
	public static final int PRIORITY_RESTOCK = 30;

	/** Fallback patrol — stay inside the farm zone. */
	public static final int PRIORITY_WANDER = 10;

	// =========================================================================
	// Action costs  (lower = planner-preferred)
	// =========================================================================

	/** Skill-based attack — preferred over autoattack when a damage skill is available. */
	public static final float COST_DAMAGE_SKILL = 0.8f;

	/** Autoattack fallback when no damage skill is available. */
	public static final float COST_ATTACK = 1.0f;

	/** Drink healing potion — fast, low cost; preferred over sit-rest. */
	public static final float COST_DRINK_POTION = 1.0f;

	/** Pick up a ground item. */
	public static final float COST_PICKUP_LOOT = 1.0f;

	/** Cast one self-buff skill. */
	public static final float COST_USE_BUFF = 1.0f;

	/** Cast self-heal skill — slightly more expensive than a potion (costs MP). */
	public static final float COST_HEAL_SKILL = 1.5f;

	/** Move within attack range of current target. */
	public static final float COST_MOVE_TO_TARGET = 2.0f;

	/** Go to city and restock ammo/supplies. */
	public static final float COST_RESTOCK = 2.0f;

	/** Go to city and sell loot. */
	public static final float COST_SELL_ITEMS = 2.0f;

	/** Search farm zone for a mob to attack. */
	public static final float COST_SEARCH_TARGET = 3.0f;

	/** Teleport to the city (emergency escape or restock trip). */
	public static final float COST_TELEPORT_CITY = 5.0f;

	/** Teleport back to the farm zone. */
	public static final float COST_TELEPORT_FARM = 5.0f;

	/** Sit and wait for natural HP/MP regeneration — last resort. */
	public static final float COST_SIT_REST = 10.0f;

	// =========================================================================
	// HP / MP world-state thresholds  (percent, 0–100)
	// =========================================================================

	// HP zones: CRITICAL [0, HP_CRITICAL)  LOW [HP_CRITICAL, HP_LOW)
	//           MID [HP_LOW, HP_FULL)      FULL [HP_FULL, 100]

	/** Below this HP% the bot considers itself in mortal danger → SurviveGoal. */
	public static final double HP_CRITICAL_THRESHOLD = 20.0;

	/** Below this HP% (but above critical) the bot considers itself wounded → DrinkPotionGoal / RestoreGoal. */
	public static final double HP_LOW_THRESHOLD = 60.0;

	/** At or above this HP% the bot considers its HP full. */
	public static final double HP_FULL_THRESHOLD = 99.0;

	// MP zones: LOW [0, MP_LOW)   OK [MP_LOW, MP_FULL)   FULL [MP_FULL, 100]

	/** Below this MP% the bot avoids MP-costly actions until it regenerates. */
	public static final double MP_LOW_THRESHOLD = 40.0;

	/** At or above this MP% the bot considers its MP full. */
	public static final double MP_FULL_THRESHOLD = 99.0;

	// =========================================================================
	// SitRest stand-up thresholds
	// =========================================================================

	/** Bot stands up when HP reaches this percent after sitting. */
	public static final double STAND_HP_THRESHOLD = 80.0;

	/** Bot stands up when MP reaches this percent after sitting. */
	public static final double STAND_MP_THRESHOLD = 55.0;

	// =========================================================================
	// Agent tick timing  (milliseconds)
	// =========================================================================

	/** Minimum interval between GOAP ticks (simulates human reaction time). */
	public static final long THINK_MS_MIN = 250;

	/** Maximum interval between GOAP ticks. */
	public static final long THINK_MS_MAX = 400;

	/** Minimum delay before the bot attempts to revive after death. */
	public static final long REVIVE_DELAY_MIN = 1_000;

	/** Maximum delay before the bot attempts to revive after death. */
	public static final long REVIVE_DELAY_MAX = 5_000;

	// =========================================================================
	// Action timeouts  (milliseconds)
	// =========================================================================

	/** Default GOAP action timeout before the plan is aborted and replanned. */
	public static final long ACTION_DEFAULT_TIMEOUT_MS = 30_000;

	/** Max time to wait for a target to die while auto-attacking (mob likely fled or unreachable). */
	public static final long ACTION_ATTACK_TIMEOUT_MS = 8_000;

	/** Max time to wait for a skill cast to start and complete. */
	public static final long ACTION_CAST_TIMEOUT_MS = 10_000;

	/** How long to disable a skill after a failed cast attempt (prevents tight retry loop). */
	public static final long ACTION_CAST_FAIL_DISABLE_MS = 3_000;

	/** Safety timeout when picking up an item (item may have despawned). */
	public static final long ACTION_PICKUP_TIMEOUT_MS = 8_000;

	/** Safety timeout waiting for sit-down animation to confirm. */
	public static final long ACTION_SIT_TIMEOUT_MS = 3_000;

	/** Safety timeout waiting for stand-up animation to confirm. */
	public static final long ACTION_STAND_TIMEOUT_MS = 3_000;

	/** Max time to wait for the bot to close range to its target before aborting movement. */
	public static final long ACTION_MOVE_TO_TARGET_TIMEOUT_MS = 8_000;

	/** Wait after issuing standUp() for the ~2500 ms sit→stand animation to finish. */
	public static final long STAND_UP_ANIMATION_MS = 2_600;

	/**
	 * Grace period after last combat during which SitRest is blocked.
	 * Prevents immediately sitting down when a slow-registering aggressor is still nearby.
	 */
	public static final long POST_COMBAT_REST_GRACE_MS = 3_000;

	// =========================================================================
	// Teleport & travel timing  (milliseconds)
	// =========================================================================

	/** Settle pause after arriving by teleport (city or farm zone). */
	public static final long TELEPORT_SETTLE_MS = 1_000;

	/** Wait time at a gatekeeper NPC before teleporting to the farm zone. */
	public static final long GATEKEEPER_WAIT_MS = 3_000;

	// =========================================================================
	// Movement & pathfinding distances  (game units)
	// =========================================================================

	/** Distance threshold to consider a movement destination reached. */
	public static final int ARRIVAL_RADIUS = 150;

	/** Distance at which the bot switches from moving to picking up an item. */
	public static final int PICKUP_RADIUS = 70;

	/**
	 * If a mob moves more than this many units from the stored path position,
	 * the entire path is recalculated.
	 */
	public static final int MOB_REPATH_THRESHOLD = 300;

	/**
	 * Distance from the last waypoint at which the bot refreshes the waypoint
	 * to the mob's current live position.
	 */
	public static final int MOB_LAST_WP_UPDATE_RANGE = 200;

	/**
	 * Per-tick distance the mob must increase to be counted as fleeing.
	 * After {@link #MAX_FLEE_CHECKS} consecutive flee ticks the target is abandoned.
	 */
	public static final int MOB_FLEE_DELTA = 150;

	/** Consecutive geo-path errors before the bot gives up on the current target. */
	public static final int MAX_GEO_ERRORS = 3;

	/** Consecutive "mob is fleeing" ticks before the bot abandons the target. */
	public static final int MAX_FLEE_CHECKS = 3;

	/**
	 * If the bot is within this distance of the farm zone, it walks directly
	 * instead of routing through the city gatekeeper.
	 */
	public static final int MAX_WALK_BACK_DISTANCE = 3_000;

	// =========================================================================
	// Target search  (game units / milliseconds)
	// =========================================================================

	/** Initial radius used when scanning for a mob to attack. */
	public static final int TARGET_SEARCH_RADIUS_BASE = 700;

	/** Maximum scan radius; applied when no mob is found at the base radius. */
	public static final int TARGET_SEARCH_RADIUS_MAX = 1_000;

	/** Maximum Z-axis difference when evaluating whether a mob is reachable (avoids cross-floor targeting). */
	public static final int TARGET_MAX_Z_DIFF = 800;

	/** Minimum delay between consecutive target searches. */
	public static final long TARGET_SEARCH_COOLDOWN_MIN = 2_000;

	/** Maximum delay between consecutive target searches. */
	public static final long TARGET_SEARCH_COOLDOWN_MAX = 4_000;

	/**
	 * Extra tolerance added to the physical attack range when checking
	 * whether the bot can hit its target ({@code physicalAttackRange + ATTACK_RANGE_TOLERANCE}).
	 * Accounts for path-stop imprecision and slight mob movement.
	 */
	public static final int ATTACK_RANGE_TOLERANCE = 80;

	// =========================================================================
	// Loot collection  (game units)
	// =========================================================================

	/** Radius within which ground items are detected and queued for pickup. */
	public static final int LOOT_SCAN_RADIUS = 400;

	// =========================================================================
	// Supply & inventory
	// =========================================================================

	/** Restock shots/arrows when remaining quantity falls below this value. */
	public static final long RESTOCK_THRESHOLD = 500;

	/** Target quantity after a restock run. */
	public static final long RESTOCK_TARGET = 3_000;

	/** Target quantity of healing potions to maintain. */
	public static final long POTION_TARGET = 100;

	/** Minimum time between potion uses (potion reuse cooldown). */
	public static final long POTION_REUSE_MS = 30_000;

	/** Sell items when encumbrance reaches this fraction of maximum carry weight. */
	public static final double WEIGHT_SELL_THRESHOLD = 0.60;

	// =========================================================================
	// Economy
	// =========================================================================

	/** NPC buy price multiplier applied to the item reference price. */
	public static final double SELL_RATE = 0.5;

	/** Items with reference price below this threshold are not listed in the bot's private shop. */
	public static final long MIN_SHOP_PRICE = 5_000;

	/** Bot private-shop price multiplier applied to the item reference price. */
	public static final double SHOP_PRICE_RATE = 2.0;

	// =========================================================================
	// Progression (class transfers)
	// =========================================================================

	/** Player level at which the bot performs the 1st class transfer. */
	public static final int FIRST_CLASS_LEVEL = 20;

	/** Player level at which the bot performs the 2nd class transfer. */
	public static final int SECOND_CLASS_LEVEL = 40;

	/** Player level at which the bot performs the 3rd class transfer. */
	public static final int THIRD_CLASS_LEVEL = 76;
}
