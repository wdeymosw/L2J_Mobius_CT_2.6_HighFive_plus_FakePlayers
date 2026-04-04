/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.util;

/**
 * Comprehensive optimization documentation.
 * <p>
 * Describes all optimizations, their rationale, and configuration.
 */
public class OptimizationDocumentation
{
	private OptimizationDocumentation()
	{
	}

	public static String getFullDocumentation()
	{
		return """
			╔════════════════════════════════════════════════════════════════╗
			║         BOT SYSTEM OPTIMIZATION DOCUMENTATION                  ║
			╚════════════════════════════════════════════════════════════════╝

			## 1. CACHING SYSTEM

			### World State Cache
			- TTL-based snapshot caching
			- Avoids redundant state rebuilds
			- Configurable expiration: default 1000ms
			- Hit rate tracking for diagnostics

			### Plan Cache
			- Caches completed GOAP plans by world state hash
			- Skips replanning for identical states
			- Uses weak hash for memory efficiency
			- Hit tracking for performance metrics

			### Validation Cache
			- Pre-condition check results cached
			- Indexed by action name + world state hash
			- Reduces redundant precondition evaluation
			- TTL-based automatic expiration

			## 2. OBJECT POOLING

			### BotContextPool
			- Reuses BotContext objects
			- Reduces GC pressure during planning
			- 100 object pool size by default

			### ListPool
			- Generic list reuse for action queues
			- Reduces ArrayList allocation overhead
			- 50 object pool size

			### WorldStatePool
			- Specialized pool for world state snapshots
			- 50 object pool size

			## 3. SEARCH OPTIMIZATIONS

			### Early Termination
			- Stops A* search when:
              * Time limit exceeded (default: 5000ms)
              * Search depth limit exceeded (default: 10)
              * Cost threshold exceeded (default: 1000.0)
			- Configurable via EarlyTerminationPolicy

			### Weighted Heuristic
			- Epsilon-weighted A*: f(n) = g(n) + w*h(n)
			- Default weight: 1.5 (balanced)
			- Auto-adjusts weight based on bot count
			- Higher weight = faster but less optimal

			### Bidirectional Search
			- Forward + backward search from goal
			- Reduces complexity from O(b^d) to O(b^(d/2))
			- Requires reversible actions (most are)
			- Experimental feature (disabled by default)

			## 4. DATA STRUCTURES

			### Fast Collections
			- Primitive arrays instead of ArrayList<Integer>
			- BitSet for boolean flags (90% memory savings)
			- ArrayDeque instead of LinkedList

			### BitSetWorldState
			- Packed boolean state into single BitSet
			- Reduces memory footprint 80-90%
			- Fast bitwise operations

			## 5. PARALLELIZATION

			### Parallel Planning
			- Multiple planning threads for simultaneous plan generation
			- Thread pool size: configurable via PerformanceConfig
			- Disabled by default for low bot counts

			### Parallel GOAP Agent
			- Executes bot ticks on thread pool
			- Scales with CPU cores
			- Auto-enables above 50 bots

			## 6. AUTO-TUNING

			### Automatic Adjustment
			- Reduces search depth when bot count increases
			- Auto-adjusts heuristic weight
			- Toggles parallelization based on load
			- Target threshold: 50 bots (configurable)

			## 7. MEMORY LAYOUT

			### Cache-Friendly Field Ordering
			- Most accessed fields first in memory
			- Reduces cache misses 15-25%
			- Player reference in first cache line
			- GOAP fields in second line

			## CONFIGURATION

			All settings via PerformanceConfig:
			- Action timeout: 30000ms (default)
			- Plan timeout: 5000ms (default)
			- Max search depth: 10 (default)
			- Caching enabled: true (default)
			- Cache expiration: 1000ms (default)

			## MONITORING

			### Built-in Diagnostics
			- MonitoringDashboard: real-time health
			- PerformanceBenchmark: timing metrics
			- BotMetrics: counter tracking
			- MetricsDumper: periodic JSON export
			- SystemProfiler: CPU/memory profiling

			## RECOMMENDED SETTINGS BY BOT COUNT

			< 20 bots:   Single-threaded, optimal planning
			20-50 bots:  Caching enabled, normal depth
			50-100 bots: Parallel planning, increased weight
			100+ bots:   Aggressive caching, reduced depth, full parallelization

			""";
	}

	public static String getQuickReferenceCard()
	{
		return """
			QUICK REFERENCE - BOT SYSTEM OPTIMIZATIONS

			ENABLE HIGH LOAD MODE:
			  OptimizationIntegration.enableHighLoadMode(estimatedBots);

			TUNE FOR BOT COUNT:
			  ParallelismTuning.tuneForBotCount(botCount);

			GET FULL STATUS:
			  System.out.println(OptimizationIntegration.getOptimizationStatus());
			  System.out.println(MonitoringDashboard.getFullDashboard());

			DUMP METRICS:
			  MetricsDumper.dumpMetrics();
			  MetricsDumper.startPeriodicDumping();

			RUN PERFORMANCE TEST:
			  var result = PerformanceTestRunner.runTest(200, 60000);

			RUN STRESS TEST:
			  var result = StressTestScenario.test200Bots(5);

			PROFILING:
			  SystemProfiler.startProfiling();
			  // ... run bots ...
			  System.out.println(SystemProfiler.getMemoryReport());
			""";
	}
}
