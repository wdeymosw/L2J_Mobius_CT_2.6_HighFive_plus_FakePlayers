/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.goap;

import java.util.EnumMap;
import java.util.Map;

import org.l2jmobius.gameserver.bot.core.model.BotContext;
import org.l2jmobius.gameserver.bot.core.model.BotInstance;
import org.l2jmobius.gameserver.bot.core.service.BuffService;
import org.l2jmobius.gameserver.bot.core.service.LootService;
import org.l2jmobius.gameserver.bot.core.service.PotionData;
import org.l2jmobius.gameserver.bot.core.service.SkillService;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.effects.EffectType;
import org.l2jmobius.gameserver.model.skill.Skill;
import org.l2jmobius.gameserver.model.skill.targets.TargetType;

/**
 * Set of boolean {@link Fact}s representing the bot's world at one point in time.
 * <p>
 * Storage: {@code EnumMap<Fact, Boolean>} — only explicitly-set facts are stored.
 * An unset fact is treated as {@code false} (closed-world assumption).
 * <p>
 * Partial matching: {@link #satisfies} only checks facts present in the goal state,
 * so goals declare the minimal desired subset, not the full world.
 */
public class WorldState
{
	// --- HP thresholds (mirrors BotBrain constants, kept here to avoid cross-dependency) ---
	private static final double HP_CRITICAL_THRESHOLD = 20.0;
	private static final double HP_LOW_THRESHOLD = 60.0;
	private static final double HP_FULL_THRESHOLD = 99.0;
	private static final double MP_LOW_THRESHOLD = 40.0;
	private static final double MP_OK_THRESHOLD = 40.0;
	private static final double MP_FULL_THRESHOLD = 99.0;

	private final EnumMap<Fact, Boolean> _facts;

	public WorldState()
	{
		_facts = new EnumMap<>(Fact.class);
	}

	/** Copy constructor — used by the planner to simulate action effects. */
	public WorldState(WorldState other)
	{
		_facts = new EnumMap<>(other._facts);
	}

	// -------------------------------------------------------------------------
	// Fact accessors
	// -------------------------------------------------------------------------

	/** Sets a fact explicitly. */
	public void set(Fact fact, boolean value)
	{
		_facts.put(fact, value);
	}

	/**
	 * Returns the value of a fact.
	 * Facts not explicitly set return {@code false} (closed-world).
	 */
	public boolean get(Fact fact)
	{
		return Boolean.TRUE.equals(_facts.get(fact));
	}

	/** Returns {@code true} if this fact was explicitly set (rather than absent/default). */
	public boolean isExplicitlySet(Fact fact)
	{
		return _facts.containsKey(fact);
	}

	/** Returns the internal facts map for iteration. */
	public EnumMap<Fact, Boolean> getMask()
	{
		return _facts;
	}

	// -------------------------------------------------------------------------
	// Planner helpers
	// -------------------------------------------------------------------------

	/**
	 * Returns {@code true} if every fact in {@code goal} matches this state.
	 * Facts absent from {@code goal} are ignored (partial matching).
	 *
	 * @param goal the desired partial world state
	 * @return true if this state satisfies the goal
	 */
	public boolean satisfies(WorldState goal)
	{
		for (Map.Entry<Fact, Boolean> entry : goal._facts.entrySet())
		{
			if (get(entry.getKey()) != entry.getValue())
			{
				return false;
			}
		}
		return true;
	}

	/**
	 * Returns a new WorldState with the given effects applied on top.
	 * Does not mutate — the original state is unchanged.
	 *
	 * @param effects facts to overlay
	 * @return new WorldState with effects applied
	 */
	public WorldState applyEffects(WorldState effects)
	{
		final WorldState result = new WorldState(this);
		for (Map.Entry<Fact, Boolean> entry : effects._facts.entrySet())
		{
			result.set(entry.getKey(), entry.getValue());
		}
		return result;
	}

	/**
	 * Returns {@code true} if at least one fact in {@code effects} matches
	 * a fact required by {@code needed} with the same value.
	 * Used by the planner to filter relevant actions.
	 *
	 * @param effects action effects
	 * @param needed  currently needed facts
	 * @return true if this action contributes toward the goal
	 */
	public static boolean contributesTo(WorldState effects, WorldState needed)
	{
		for (Map.Entry<Fact, Boolean> entry : effects._facts.entrySet())
		{
			final Fact f = entry.getKey();
			if (needed.isExplicitlySet(f) && (needed.get(f) == entry.getValue()))
			{
				return true;
			}
		}
		return false;
	}

	// -------------------------------------------------------------------------
	// Factory
	// -------------------------------------------------------------------------

	/**
	 * Builds the current world state from the bot's live context snapshot.
	 * Called once per tick by {@link org.l2jmobius.gameserver.bot.core.goap.GoapAgent}.
	 *
	 * @param ctx pre-built perception snapshot for this tick
	 * @param bot the bot instance
	 * @return current WorldState
	 */
	public static WorldState fromContext(BotContext ctx, BotInstance bot)
	{
		final WorldState ws = new WorldState();

		// --- Health ---
		ws.set(Fact.HP_CRITICAL, ctx.hpPercent < HP_CRITICAL_THRESHOLD);
		ws.set(Fact.HP_LOW, ctx.hpPercent < HP_LOW_THRESHOLD);
		ws.set(Fact.HP_MID, ctx.hpPercent < HP_FULL_THRESHOLD);
		ws.set(Fact.HP_FULL, ctx.hpPercent >= HP_FULL_THRESHOLD);

		// --- Mana ---
		ws.set(Fact.MP_LOW, ctx.mpPercent < MP_LOW_THRESHOLD);
		ws.set(Fact.MP_OK, ctx.mpPercent >= MP_OK_THRESHOLD);
		ws.set(Fact.MP_FULL, ctx.mpPercent >= MP_FULL_THRESHOLD);

		// --- Combat ---
		ws.set(Fact.UNDER_ATTACK, ctx.attackerCount > 0);
		ws.set(Fact.TARGET_EXISTS, ctx.hasTarget());
		ws.set(Fact.TARGET_IN_RANGE, ctx.canAttackTarget);
		ws.set(Fact.TARGET_DEAD, !ctx.hasTarget());
		ws.set(Fact.IN_COMBAT, ctx.hasTarget() || (ctx.attackerCount > 0));
		ws.set(Fact.THREAT_NEUTRALIZED, !ctx.hasTarget() && (ctx.attackerCount == 0));

		// --- Zone / position ---
		ws.set(Fact.IN_FARM_ZONE, ctx.isInFarmZone);
		ws.set(Fact.IN_SAFE_PLACE, !ctx.isInFarmZone);

		// --- Inventory / supplies ---
		ws.set(Fact.INVENTORY_OK, !ctx.inventoryFull && (ctx.weightPenalty < 2));
		ws.set(Fact.POTION_READY, ctx.potionReuseReady);
		ws.set(Fact.HAS_AMMO, !ctx.outOfAmmo);
		ws.set(Fact.HAS_POTIONS, hasPotions(bot.getPlayer()));

		// --- Skills ---
		ws.set(Fact.HAS_HEAL_SKILL, hasHealSkill(bot));

		// --- Player state ---
		ws.set(Fact.IS_SITTING, bot.getPlayer().isSitting());
		ws.set(Fact.IS_DEAD, bot.getPlayer().isDead());
		ws.set(Fact.IS_CASTING, bot.getPlayer().isCastingNow());
		ws.set(Fact.IS_MOVING, bot.getPlayer().isMoving());
		ws.set(Fact.OVERWEIGHT, ctx.weightPenalty >= 2);

		// --- Buff state ---
		ws.set(Fact.HAS_BUFF, !BuffService.needsBuff(bot));

		// --- Combat skills ---
		ws.set(Fact.HAS_DAMAGE_SKILL, SkillService.hasDamageSkill(bot));

		// --- Loot ---
		ws.set(Fact.LOOT_NEARBY, LootService.hasNearbyLoot(bot));

		return ws;
	}

	// -------------------------------------------------------------------------
	// Private helpers
	// -------------------------------------------------------------------------

	private static boolean hasPotions(Player player)
	{
		for (int id : PotionData.HEAL_POTION_IDS)
		{
			if (player.getInventory().getInventoryItemCount(id, -1) > 0)
			{
				return true;
			}
		}
		return false;
	}

	private static boolean hasHealSkill(BotInstance bot)
	{
		final Player player = bot.getPlayer();
		for (Skill skill : player.getAllSkills())
		{
			if ((skill.getTargetType() == TargetType.SELF) && skill.hasEffectType(EffectType.HEAL) && !player.isSkillDisabled(skill) && (player.getCurrentMp() >= skill.getMpConsume()))
			{
				return true;
			}
		}
		return false;
	}

	@Override
	public String toString()
	{
		final StringBuilder sb = new StringBuilder("WorldState{");
		for (Map.Entry<Fact, Boolean> entry : _facts.entrySet())
		{
			if (Boolean.TRUE.equals(entry.getValue()))
			{
				sb.append(entry.getKey().name()).append(' ');
			}
		}
		sb.append('}');
		return sb.toString();
	}
}
