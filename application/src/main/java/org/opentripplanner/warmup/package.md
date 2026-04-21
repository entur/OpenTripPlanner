# Application Warmup

Runs GraphQL trip queries in a background thread during OTP startup to warm up the application
(JIT compilation, GraphQL schema caches, routing data structures, etc.) before production traffic
arrives.

## Lifecycle

1. `ConstructApplication` obtains a `WarmupLauncher` from the Dagger factory
   (`configure.WarmupModule` wires it) and calls `start()` after Raptor transit data is created
   and updaters are configured.
2. A daemon thread sends sequential queries through the configured GraphQL API (TransModel or GTFS),
   exercising the full stack: GraphQL parsing, data fetchers, routing (Raptor + A*), itinerary
   filtering, and response serialization.
3. It alternates between depart-at / arrive-by and cycles through access/egress modes.
4. The thread stops when the health probe reports "UP" (all updaters primed).

## Design

`WarmupParameters` is the SPI consumed by the module. `WarmupConfig`
(in `standalone.config.routerconfig`) implements this interface and maps the JSON config section
into parameter values.

`WarmupQueryExecutor` is the strategy interface with two implementations:
- `TransmodelWarmupQueryExecutor` -- builds and executes TransModel `trip` queries.
- `GtfsWarmupQueryExecutor` -- builds and executes GTFS `planConnection` queries.

Each executor receives configurable access/egress mode lists (`StreetMode` values) and maps them
to the API-specific GraphQL enum names.

## Configuration

Configured via the `warmup` section in `router-config.json`. See `WarmupConfig` for details.
