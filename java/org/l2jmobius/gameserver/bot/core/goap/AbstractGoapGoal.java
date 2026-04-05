/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.goap;

/**
 * Base class for all GOAP goals.
 * <p>
 * Owns the immutable desired {@link WorldState} that every goal must declare.
 * Subclasses pass a pre-built static instance to the constructor, eliminating
 * the repetitive {@code getDesiredState()} override boilerplate.
 */
public abstract class AbstractGoapGoal implements GoapGoal
{
	private final WorldState _desired;

	/**
	 * @param desired partial world state this goal wants to achieve
	 */
	protected AbstractGoapGoal(WorldState desired)
	{
		_desired = desired;
	}

	@Override
	public final WorldState getDesiredState()
	{
		return _desired;
	}
}
