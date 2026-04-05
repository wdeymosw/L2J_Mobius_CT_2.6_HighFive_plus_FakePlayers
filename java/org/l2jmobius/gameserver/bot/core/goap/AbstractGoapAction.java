/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.goap;

/**
 * Base class for all GOAP actions.
 * <p>
 * Owns the immutable {@link WorldState} pair (preconditions and effects) that every
 * action must declare. Subclasses pass pre-built static instances to the constructor,
 * keeping the per-class static initializer pattern while eliminating the repetitive
 * {@code getPreconditions()} / {@code getEffects()} override boilerplate.
 */
public abstract class AbstractGoapAction implements GoapAction
{
	private final WorldState _preconditions;
	private final WorldState _effects;

	/**
	 * @param preconditions facts that must be true for the planner to use this action
	 * @param effects        facts this action establishes when it completes
	 */
	protected AbstractGoapAction(WorldState preconditions, WorldState effects)
	{
		_preconditions = preconditions;
		_effects = effects;
	}

	@Override
	public final WorldState getPreconditions()
	{
		return _preconditions;
	}

	@Override
	public final WorldState getEffects()
	{
		return _effects;
	}
}
