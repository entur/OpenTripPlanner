---
date: 2026-02-05T14:00:00+01:00
researcher: Claude
git_commit: c7e9b34eb5f66af1cbfde11854cace8f69b97b19
branch: speed-restrictions
repository: OpenTripPlanner
topic: "GBFS Geofencing Speed Restrictions: Current State and Gap Analysis"
tags: [research, codebase, geofencing, gbfs, speed-restrictions, vehicle-rental, routing]
status: complete
last_updated: 2026-02-05
last_updated_by: Claude
---

# Research: GBFS Geofencing Speed Restrictions

**Date**: 2026-02-05T14:00:00+01:00
**Researcher**: Claude
**Git Commit**: c7e9b34eb5f66af1cbfde11854cace8f69b97b19
**Branch**: speed-restrictions
**Repository**: OpenTripPlanner

## Research Question

What is the current state of GBFS geofencing zone infrastructure on the `speed-restrictions` branch, and what would be required to support speed restrictions from GBFS geofencing zones?

## Summary

The `speed-restrictions` branch has made significant architectural changes to the geofencing system (6 commits, 29 files changed, +1191/-224 lines). These changes implement a **boundary-only geofencing model** with state tracking, zone priority/precedence, per-network spatial indexing, and support for multiple rules per zone. However, the GBFS `maximum_speed_kph` field -- available in both the v2.3 and v3.0 GBFS Java library -- is **not mapped** anywhere in the codebase. The `GeofencingZone` domain model has no speed field. The routing `State` has no speed restriction state. Speed calculation in `StreetEdge.calculateSpeed()` does not consult geofencing zones. The infrastructure built on this branch provides a solid foundation for adding speed restrictions, but the feature itself does not yet exist.

## Detailed Findings

### 1. What the `speed-restrictions` Branch Changed

The branch contains 6 commits on top of `dev-2.x`:

| Commit | Description |
|--------|-------------|
| `c7e9b34` | Store per-network `GeofencingZoneIndex` to prevent overwriting |
| `f80c0ed` | Optimize geofencing zone containment checks (inner envelope) |
| `9deeb14` | Implement boundary-only geofencing with state tracking |
| `449bd19` | Remove GBFS reference from domain layer comment |
| `e5e9723` | Support multiple rules per GBFS geofencing zone |
| `39f95d9` | Priority-based precedence for overlapping geofencing zones |

**Key architectural changes:**
- **Boundary-only geofencing**: Instead of marking every edge inside a zone with a `GeofencingZoneExtension`, only boundary-crossing edges get `GeofencingBoundaryExtension` markers. Zone membership is tracked in routing `State`.
- **`GeofencingZoneIndex`** (new class): A spatial index using JTS `STRtree` + `PreparedGeometry` + inner envelope optimization for fast point-in-zone queries at vehicle pickup time.
- **Per-network isolation**: `Graph` stores a `Map<String, GeofencingZoneIndex>`, one per rental network. Each `VehicleRentalUpdater` manages its own index independently.
- **Priority-based precedence**: `GeofencingZone` now has a `priority` field. `CompositeRentalRestrictionExtension` selects the highest-priority (lowest number) applicable extension when multiple zones overlap.
- **Multiple rules per zone**: The GBFS mapper iterates over all rules in a feature, producing separate `GeofencingZone` objects for each rule with computed priorities.
- **Moved `GeofencingVertexUpdater`**: From `updater/vehicle_rental/` to `service/vehiclerental/street/`, decoupled from the updater layer.

### 2. Current Geofencing Zone Domain Model

**`GeofencingZone`** (`service/vehiclerental/model/GeofencingZone.java:12-52`):

```
record GeofencingZone(
  FeedScopedId id,
  @Nullable I18NString name,
  Geometry geometry,
  boolean dropOffBanned,
  boolean traversalBanned,
  int priority
)
```

Two boolean restriction fields. **No speed field exists.**

- `hasRestriction()`: `dropOffBanned || traversalBanned`
- `isBusinessArea()`: `!dropOffBanned && !traversalBanned`

### 3. GBFS `maximum_speed_kph` Field -- Available But Unmapped

Both GBFS v2.3 and v3.0 `GBFSRule` classes in the Java library (`org.mobilitydata:gbfs-java-model:1.0.9`) contain a `maximum_speed_kph` field:

**V2.3** (`GBFSRule.java`):
- Field: `private Integer maximumSpeedKph` (line 58)
- Getter: `getMaximumSpeedKph()` (line 148)
- JSON property: `"maximum_speed_kph"`

**V3.0** (`GBFSRule.java`):
- Field: `private Integer maximumSpeedKph` (line 67)
- Getter: `getMaximumSpeedKph()` (line 182)
- JSON property: `"maximum_speed_kph"`

The OTP GBFS mappers currently extract only two fields per rule:
- **V2**: `rule.getRideAllowed()` -> `dropOffBanned`, `rule.getRideThroughAllowed()` -> `traversalBanned`
- **V3**: `rule.getRideEndAllowed()` -> `dropOffBanned`, `rule.getRideThroughAllowed()` -> `traversalBanned`

`getMaximumSpeedKph()` is never called.

### 4. Current Speed Calculation Path (Unaware of Geofencing)

Speed is determined in `StreetEdge.calculateSpeed()` (line 206-224):

```java
final double speed = switch (traverseMode) {
  case WALK -> walkingBike ? preferences.bike().walking().speed() : preferences.walk().speed();
  case BICYCLE -> Math.min(preferences.bike().speed(), getCyclingSpeedLimit());
  case CAR -> getCarSpeed();
  case SCOOTER -> Math.min(preferences.scooter().speed(), getCyclingSpeedLimit());
};
```

This is called at `StreetEdge.java:1055` during `doTraverse()`. The speed depends solely on the `StreetSearchRequest` preferences and the edge's own speed properties. **Geofencing zones are not consulted.**

The closest analogue to zone-based speed adjustment is the **propulsion type** system: `VehicleRentalEdge.getPropulsionType()` extracts propulsion from the rental place, stores it in `StateData`, and `StreetEdge.bicycleOrScooterTraversalCost()` uses it to adjust effective distance (ELECTRIC ignores slope entirely, ELECTRIC_ASSIST interpolates). This affects travel time but is per-vehicle, not per-zone.

### 5. Routing State: Zone Tracking Infrastructure (No Speed State)

**`StateData.currentGeofencingZones`** (line 67): `Set<GeofencingZone>` -- tracks which zones the vehicle is currently inside. Updated at boundary crossings via `StateEditor.enterGeofencingZone()`/`exitGeofencingZone()`.

The state tracks two zone-derived restriction queries:
- `State.isDropOffBannedByCurrentZones()` (line 472-481)
- `State.isTraversalBannedByCurrentZones()` (line 487-496)

**No speed restriction field exists in `State`, `StateData`, or `StateEditor`.**

### 6. How Zone Boundaries Are Applied to the Graph

`GeofencingVertexUpdater.applyGeofencingZones()` (line 62-98):

1. **Restricted zones** (have `dropOffBanned` or `traversalBanned`): `applyBoundaryRestrictions()` places `GeofencingBoundaryExtension(zone, entering)` on the **to-vertex** of boundary-crossing edges.
2. **Business areas** (no restrictions): Unions all business area geometries, places `BusinessAreaBorder(network)` on **from-vertex** of edges crossing the union boundary.
3. Builds `GeofencingZoneIndex` from all zones and returns it.

### 7. Priority/Precedence System

- `GeofencingZone.priority`: defaults to `0` (highest priority). Set by GBFS mapper as `zoneIndex * 1000 + ruleIndex`.
- `GeofencingBoundaryExtension.priority()`: delegates to `zone.priority()`
- `BusinessAreaBorder.priority()`: returns `Integer.MAX_VALUE` (lowest priority)
- `CompositeRentalRestrictionExtension.getHighestPriorityApplicable()` (line 50-61): selects extension with lowest `priority()` among those where `appliesTo(state)` is true.

### 8. Data Flow: Graph to Routing

1. `VehicleRentalUpdater.runPolling()` fetches GBFS data
2. `GbfsGeofencingZoneMapper.toInternalModel()` creates `GeofencingZone` objects (no speed field)
3. `GeofencingVertexUpdater.applyGeofencingZones()` marks boundary edges, builds `GeofencingZoneIndex`
4. Index stored on `Graph.geofencingZoneIndexes` per network
5. `DirectStreetRouter` -> `GraphPathFinder` -> `StreetSearchBuilder` -> `StreetSearchRequest.geofencingZoneIndexes`
6. At vehicle pickup: `VehicleRentalEdge.initializeGeofencingZones()` queries the spatial index
7. During traversal: `StreetEdge.updateGeofencingZoneState()` updates `State.currentGeofencingZones` at boundaries
8. `State.isTraversalBannedByCurrentZones()` / `isDropOffBannedByCurrentZones()` enforce binary restrictions

### 9. Existing Speed-Related Systems (Not Geofencing)

| System | Location | How Speed Works |
|--------|----------|-----------------|
| OSM `maxspeed` tags | `SpeedParser.java`, stored on `StreetEdge.carSpeed` | Per-edge, set at build time |
| Graph-wide `maxCarSpeed` | `OsmModule.getMaxCarSpeed()` -> `StreetModelDetails` | Heuristic bound for A* |
| Cycling speed limit | `StreetEdge.getCyclingSpeedLimit()` | Per-edge, caps bike/scooter speed |
| Propulsion type | `VehicleRentalEdge.getPropulsionType()` -> `StateData` | Adjusts effective distance for slope |
| User preferences | `StreetSearchRequest.bike().speed()`, `.scooter().speed()`, etc. | Per-request speed cap |

### 10. Where Speed Restrictions Would Need to Integrate

Based on the existing architecture, speed restrictions from GBFS geofencing zones would need to touch these layers:

**Layer 1 -- GBFS Parsing (data in)**:
- `GbfsGeofencingZoneMapper.toInternalModel()` (`GbfsGeofencingZoneMapper.java:60-98`): Currently does not read `rule.getMaximumSpeedKph()`
- V2 mapper: would need a new abstract method like `ruleMaxSpeedKph(R)` alongside existing `ruleBansDropOff(R)` / `ruleBansPassThrough(R)`
- V3 mapper: same

**Layer 2 -- Domain Model**:
- `GeofencingZone` record (`GeofencingZone.java:12`): No `maxSpeedKph` or `maxSpeedMps` field

**Layer 3 -- Routing State**:
- `StateData` (`StateData.java:67`): `currentGeofencingZones` tracks zones, but there is no derived speed restriction field
- `State`: No method like `getCurrentSpeedRestriction()` or `getMaxSpeedFromZones()`

**Layer 4 -- Speed Application**:
- `StreetEdge.calculateSpeed()` (line 206): Does not consult `State.currentGeofencingZones`
- `StreetEdge.doTraverse()` (line 1055): Speed is calculated after zone state is updated (line 1047-1049), so the zone set would be current by the time speed is needed

**Layer 5 -- Restriction Extension Interface**:
- `RentalRestrictionExtension`: No `speedRestriction()` or `maxSpeed()` method
- `GeofencingBoundaryExtension`: No speed data

### 11. Test Coverage of Current Geofencing System

| Test File | What It Tests |
|-----------|--------------|
| `GeofencingVertexUpdaterTest` | Boundary-only marking, interior edges unmarked, boundary extensions have correct entering/exiting flags, business area borders, index creation |
| `GeofencingZoneIndexTest` | Empty index, single zone, overlapping zones, restricted-only filter, per-network isolation |
| `StreetEdgeGeofencingTest` | Priority precedence (higher-priority zone wins), extension add/remove, network matching, state forking at no-drop-off zones, forward and reverse traversal |
| `GbfsFeedMapperTest` | End-to-end GBFS parsing, zone priority assignment (first zone = 0, second = 1000) |

No tests reference speed restrictions.

## Code References

- `GeofencingZone.java:12-52` - Domain model (no speed field)
- `GbfsGeofencingZoneMapper.java:60-98` - Base mapper (does not read `maximumSpeedKph`)
- `v2/GbfsGeofencingZoneMapper.java:51-58` - V2 rule field access (only `rideAllowed`, `rideThroughAllowed`)
- `v3/GbfsGeofencingZoneMapper.java:59-66` - V3 rule field access (only `rideEndAllowed`, `rideThroughAllowed`)
- `StreetEdge.java:206-224` - Speed calculation (no geofencing awareness)
- `StreetEdge.java:1046-1055` - Zone state update then speed calc (sequential, speed after zone update)
- `StateData.java:67` - `currentGeofencingZones` field
- `State.java:472-496` - Zone-based ban queries (no speed query)
- `RentalRestrictionExtension.java:12-92` - Interface (no speed method)
- `CompositeRentalRestrictionExtension.java:50-61` - Priority-based resolution
- `GeofencingBoundaryExtension.java:22` - Boundary marker (no speed data)
- `GeofencingVertexUpdater.java:62-98` - Zone application orchestrator
- `Graph.java:76-80,400-418` - Per-network zone index storage
- `VehicleRentalEdge.java:257-272` - Zone initialization at pickup

## Architecture Documentation

The geofencing system on this branch follows a **boundary-only pattern**:

```
GBFS Data → GbfsGeofencingZoneMapper → GeofencingZone (model)
                                            ↓
                          GeofencingVertexUpdater.applyGeofencingZones()
                           /                                    \
            Restricted Zones                              Business Areas
            (boundary-only)                               (border marking)
                  ↓                                            ↓
    GeofencingBoundaryExtension                       BusinessAreaBorder
    (on tov of boundary edges)                     (on fromv of border edges)
                  ↓                                            ↓
    GeofencingZoneIndex ←─── stored per-network on Graph
                  ↓
    VehicleRentalEdge.initializeGeofencingZones() at pickup
                  ↓
    StreetEdge.updateGeofencingZoneState() at boundaries
                  ↓
    State.currentGeofencingZones (zone membership set)
                  ↓
    State.isTraversalBannedByCurrentZones()
    State.isDropOffBannedByCurrentZones()
    (binary ban checks -- no speed check)
```

### Key Design Decisions on This Branch

1. **Boundary-only marking**: Reduces the number of edges that need extensions from O(edges inside zone) to O(edges crossing boundary). Zone membership tracked in state instead.
2. **Per-network spatial indexes**: Each rental network's zones are indexed separately, preventing overwrites when multiple updaters run.
3. **Priority via zone/rule ordering**: GBFS feature order determines zone priority (`zoneIndex * 1000 + ruleIndex`), following the GBFS spec's implication that earlier features take precedence.
4. **State-based zone tracking**: `StateData.currentGeofencingZones` is an immutable `Set<GeofencingZone>` updated via copy-on-write in `StateEditor`.

## Historical Context

- `thoughts/shared/research/2025-12-01-gbfs-geofencing-zones-graph-build-sandbox.md` - Previous research on loading geofencing zones at graph build time. Documented the runtime geofencing system as it existed before the boundary-only refactor. The proposed sandbox architecture remains relevant but would need updating to account for the boundary-only approach and `GeofencingZoneIndex`.

## Open Questions

1. **Speed restriction semantics**: Should a geofencing zone speed limit cap the vehicle's maximum speed (like `getCyclingSpeedLimit()` caps bike speed), or should it replace the user's preferred speed entirely?
2. **State tracking vs. extension approach**: Should speed be tracked in `State.currentGeofencingZones` (derive max speed from the zone set) or should a new extension type carry speed data?
3. **Overlapping zones with different speeds**: When zones overlap, should the most restrictive (lowest) speed win, or should the highest-priority zone's speed win (consistent with the current priority model)?
4. **Zones with speed but no ban**: A GBFS zone can have `ride_through_allowed=true` and `maximum_speed_kph=15`. Currently, such a zone has `traversalBanned=false` and `dropOffBanned=false`, so `isBusinessArea()` returns `true`. Should speed-limited zones be treated as a third category distinct from restricted zones and business areas?
5. **Unit conversion**: GBFS provides speed in km/h (`Integer`). OTP uses m/s (`double`) internally. Where should the conversion happen?
6. **Heuristic impact**: If speed restrictions reduce vehicle speed in certain zones, does `EuclideanRemainingWeightHeuristic` need to account for this to remain admissible?
