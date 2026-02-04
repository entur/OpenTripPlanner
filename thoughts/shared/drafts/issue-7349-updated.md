
**Is your feature request related to a problem? Please describe.**

The vehicle rental updater (`VehicleRentalUpdater`) currently has two distinct responsibilities tangled together:

1. **GBFS protocol handling**: The GBFS code (~29 files for version detection, feed loading, HTTP caching, v2/v3 mapping) is buried inside `updater/vehicle_rental/datasources/gbfs/`. It can't be reused outside the updater. The build-time geofencing sandbox (wip #7155) already had to duplicate GBFS client logic to avoid depending on the updater code.

2. **Graph mutation**: The inner `GraphWriterRunnable` directly creates vertices, links them to the street graph via `VertexLinker`, creates edges, and applies geofencing zones (~90 lines of graph topology management). This logic belongs in the vehicle rental service domain, not in the updater.

**Goal / high level use-case**

Separate three concerns that are currently mixed together in the updater:
- **GBFS protocol** (data fetching & mapping) → reusable across runtime and build-time
- **Graph mutation** (vertices, edges, linking, geofencing) → owned by the service domain
- **Update orchestration** (polling, scheduling, write-lock coordination) → the updater's actual job

**Describe the solution you'd like**

### 1. Extract GBFS protocol code to `o.o.gbfs/`

Move GBFS protocol code into a top-level `o.o.gbfs/` package, following the precedent of `o.o.gtfs/`, `o.o.netex/`, and `o.o.osm/`. This is mostly a mechanical move with no behavioral changes.

### 2. Move graph mutation responsibility into the service domain

The `VehicleRentalRepository` (in the `street` module) should own all graph topology management for vehicle rental places. Today the updater's `GraphWriterRunnable` does this directly — creating `VehicleRentalPlaceVertex`, linking via `VertexLinker`, creating `VehicleRentalEdge`/`StreetVehicleRentalLink`, and applying geofencing zones.

After this change, the `GraphWriterRunnable` becomes a thin delegation:

```java
@Override
public void run(RealTimeUpdateContext context) {
  service.updateVehicleRentalPlaces(context.graph(), stations, geofencingZones);
}
```

The updater still owns the `GraphWriterRunnable` (write-lock coordination is an updater framework concern), but delegates the actual mutation work to the service.

This also means shared logic like `GeofencingZoneApplier`/`GeofencingVertexUpdater` can live in the service domain where both the runtime updater and the build-time graph builder reuse it. The `speed-restrictions` branch already validated this by moving `GeofencingVertexUpdater` from `updater/vehicle_rental/` into `service/vehiclerental/street/`.

### 3. Move edges/vertices to canonical locations

Move `VehicleRentalEdge`, `StreetVehicleRentalLink`, and `VehicleRentalPlaceVertex` from `service/vehiclerental/street/` to `street/model/edge/` and `street/model/vertex/`, matching how vehicle parking does it. Both locations are already in the `street` module, so this is a within-module move.

### 4. Enable `VertexLinker` for use in the service domain

`VertexLinker` currently lives in the `application` module, but its only non-`street` dependency is a single `OTPFeature.FlexRouting.isOn()` check. Injecting this as a boolean via DI would allow `VertexLinker` to move to the `street` module, where the service can use it directly for linking vertices to the street graph.

**Questions for discussion**

1. Should graph mutation move to `VehicleRentalRepository` (the write interface) or to a separate class within the service domain?

2. For `VertexLinker`: inject the feature flag via DI and move to `street`, or define a linking SPI in `street` that `application` implements?

3. Should this be sequenced as independent PRs? A natural order would be:
   - Edge/vertex move (3) — mechanical, low risk
   - GBFS extraction (1) — mechanical, medium scope
   - VertexLinker move (4) — small, enables (2)
   - Graph mutation delegation (2) — the architectural change

**Describe alternatives you've considered**

- Keep GBFS under `updater/` but make it a public API — doesn't match other data import protocols
- Separate `gbfs` Maven module — probably overkill for now
- Accept the duplication — doesn't scale
- Move the updater into `service/vehiclerental/` — conflates update scheduling (an application-level concern) with domain logic
- Define an SPI for vertex linking instead of moving `VertexLinker` — viable fallback if `VertexLinker` has other entanglements

**Additional context**

- The recent street module extraction (#7144, #7312, #7328) moved `service/vehiclerental/` into the `street` module
- The `ext/gbfsgeofencing/` sandbox is the immediate motivator (wip #7155)
- The `speed-restrictions` branch already moved `GeofencingVertexUpdater` into the service domain, validating the graph-mutation-in-service approach
- Can be broken into independent PRs
