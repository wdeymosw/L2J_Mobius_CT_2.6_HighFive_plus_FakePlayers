/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.exception;

/**
 * Strategy for recovering from a bot exception.
 * Defines how the bot system should handle different types of errors.
 * 
 * Note: This file will be moved to bot/core/exception/ package
 * after directory structure is created.
 */
public enum BotRecoveryStrategy
{
	/** Replan entire bot behavior from scratch */
	REPLAN,
	
	/** Adjust bot state and then replan */
	ADJUST_AND_REPLAN,
	
	/** Disable bot completely (critical error) */
	DISABLE
}
