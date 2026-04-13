# Application Warmup

Runs GraphQL trip queries in a background thread during OTP startup to warm up the application
(JIT compilation, GraphQL schema caches, routing data structures, etc.) before production traffic
arrives.

## Lifecycle

1. `WarmupWorker.start()` is called from `ConstructApplication` after Raptor transit data is
   created and updaters are configured.
2. A daemon thread sends sequential queries through the configured GraphQL API (TransModel or GTFS),
   exercising the full stack: GraphQL parsing, data fetchers, routing (Raptor + A*), itinerary
   filtering, and response serialization.
3. It alternates between depart-at / arrive-by and cycles through access/egress modes.
4. The thread stops when the health probe reports "UP" (all updaters primed).

## Design

`WarmupQueryExecutor` is the strategy interface with two implementations:
- `TransmodelWarmupQueryExecutor` -- builds and executes TransModel `trip` queries.
- `GtfsWarmupQueryExecutor` -- builds and executes GTFS `planConnection` queries.

The executors are decoupled from configuration: they receive `WgsCoordinate` values directly
rather than the `WarmupConfig` object.

## Configuration

Configured via the `warmup` section in `router-config.json`. See `WarmupConfig` for details.
