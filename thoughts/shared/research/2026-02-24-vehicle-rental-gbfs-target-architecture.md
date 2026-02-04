---
date: 2026-02-24T14:56:00+01:00
researcher: Claude
git_commit: f5d633412c
branch: poc/sandbox-geofencing-zones-in-graph-builder
repository: OpenTripPlanner
topic: "Vehicle Rental & GBFS — Target Architecture Design"
tags: [architecture, design, vehicle-rental, gbfs, geofencing, refactoring, domain-model, target-architecture]
status: complete
last_updated: 2026-02-24
last_updated_by: Claude
---

# Vehicle Rental & GBFS — Target Architecture Design

**Date**: 2026-02-24
**Git Commit**: f5d633412c
**Branch**: poc/sandbox-geofencing-zones-in-graph-builder
**Repository**: OpenTripPlanner
**Companion**: [Current State Analysis](2026-02-24-vehicle-rental-gbfs-architecture-design.md)

## 1. Purpose

This document proposes a target architecture for the vehicle rental and GBFS domains in OTP. It is informed by:
- The [current state analysis](2026-02-24-vehicle-rental-gbfs-architecture-design.md) which maps the existing code
- Analysis of the canonical service patterns (`worldenvelope`, `vehicleparking`, `realtimevehicles`)
- Analysis of the sandbox extension pattern (`emission`, `gbfsgeofencing`)
- The OTP architectural principles in `ARCHITECTURE.md` and `service/package.md`

The central design decision is to **separate GBFS (a data-source protocol) from vehicle rental (a domain)**. GBFS concerns are extracted into a dedicated `gbfs` package, while the vehicle rental domain is cleaned up to better follow canonical patterns.

---

## 2. Design Principles

1. **GBFS is a protocol, not a domain.** GBFS feed fetching, version detection, HTTP caching, and GBFS-to-OTP mapping are data-source concerns. They should live in their own package, independent of the updater framework and reusable by any consumer (runtime updater, build-time graph builder, future features).

2. **Vehicle rental is a domain.** The service/repository/model triad follows the canonical `service/<name>/` pattern. It should not contain GBFS-specific code or street graph integration classes.

3. **Street integration follows precedent.** The `vehicleparking` domain places its edges and vertices in `street/model/edge/` and `street/model/vertex/`. Vehicle rental should follow the same pattern for consistency.

4. **Geofencing zones are a vehicle rental concern.** They are defined by rental operators, scoped to rental networks, and consumed only during rental-mode routing. The restriction extensions remain in `service/vehiclerental/` (see discussion in section 7).

5. **Minimize breakage.** Prefer moves over rewrites. Keep existing interfaces stable. Each theme is independently shippable.

---

## 3. Canonical Patterns (Reference)

From analysis of the three canonical service domains:

| Pattern | worldenvelope | vehicleparking | realtimevehicles |
|---|---|---|---|
| Service + Repository interfaces at root | Yes | Yes | Yes |
| Single impl for both interfaces | No (separate) | No (separate) | Yes |
| configure/ with 2 Dagger modules | Yes | Yes | Yes |
| internal/ for implementations | Yes | Yes | Yes |
| model/ for public domain types | Yes | Yes | Yes |
| street/ sub-package | No | No | No |
| Thread-safety | volatile + immutable | volatile + copy-on-write | volatile + copy-on-write |

Key observations:
- **No service domain has a `street/` sub-package.** Vehicle parking places its `VehicleParkingEdge`, `StreetVehicleParkingLink`, and `VehicleParkingEntranceVertex` in `street/model/edge/` and `street/model/vertex/` respectively.
- **Both vehicleparking and realtimevehicles deviate from the Serializable rule** — their repositories do not implement `Serializable`. This is acceptable for purely-runtime data.
- **realtimevehicles uses a single class implementing both Service and Repository** — the same pattern currently used by `DefaultVehicleRentalService`.

---

## 4. Target Package Structure

### 4.1 New GBFS Package

```
o.o.gbfs/                                             -- NEW: GBFS protocol package
    GbfsService                                        -- Public facade: fetch + map GBFS feeds
    model/
      GbfsSystemInformation                            -- Mapped from GBFS system_information
      GbfsGeofencingZone                               -- Mapped from GBFS geofencing_zones
    client/
      GbfsFeedLoaderAndMapper                          -- Version detection + composition
      GbfsFeedLoader                                   -- Interface: periodic feed refresh
      GbfsFeedLoaderImpl                               -- Abstract: HTTP + TTL + ETag caching
      GbfsFeedMapper                                   -- Interface: GBFS → OTP model mapping
      GbfsFeedDetails                                  -- Interface: feed URL + name metadata
      GbfsGeofencingZoneMapper                         -- Abstract: zone mapping template
      support/
        UnknownVehicleTypeFilter
      v2/
        GbfsFeedLoader                                 -- v2/v2.3 discovery + feed fetching
        GbfsFeedMapper                                 -- v2/v2.3 mapping orchestrator
        GbfsGeofencingZoneMapper                       -- v2 zone mapping
        GbfsStationInformationMapper
        GbfsStationStatusMapper
        GbfsFreeVehicleStatusMapper
        GbfsSystemInformationMapper
        GbfsVehicleTypeMapper
      v3/
        GbfsFeedLoader                                 -- v3.0 discovery + feed fetching
        GbfsFeedMapper                                 -- v3.0 mapping orchestrator
        GbfsGeofencingZoneMapper                       -- v3 zone mapping
        GbfsStationInformationMapper
        GbfsStationStatusMapper
        GbfsVehicleStatusMapper
        GbfsSystemInformationMapper
        GbfsVehicleTypeMapper
    package.md                                         -- Architecture documentation
```

**Key decisions:**

- The package is `o.o.gbfs/`, a **top-level package** under `org.opentripplanner`. This reflects that GBFS is a protocol/data-source concern at the same level as `gtfs/`, `netex/`, and `osm/` — all of which are top-level packages for data import protocols.
- The `client/` sub-package contains the version-aware feed loading machinery. This is the equivalent of the current `updater/vehicle_rental/datasources/gbfs/` tree, relocated and made protocol-focused.
- The `model/` sub-package is minimal — only types that represent GBFS-specific concepts not already covered by the vehicle rental domain model. Most mapped types (`VehicleRentalStation`, `VehicleRentalVehicle`, `RentalVehicleType`, etc.) remain in `service/vehiclerental/model/` because they are domain types that happen to be populated from GBFS.
- `GbfsService` is a public facade that composes a `GbfsFeedLoaderAndMapper` and exposes high-level operations: `fetchAndMapRentalPlaces()`, `fetchAndMapGeofencingZones()`, etc. This replaces the current `GbfsVehicleRentalDataSource` wrapper and the sandbox's `GbfsClient`.

**What moves here from current code:**

| Current location | Target location |
|---|---|
| `updater/vehicle_rental/datasources/gbfs/GbfsFeedLoaderAndMapper` | `gbfs/client/GbfsFeedLoaderAndMapper` |
| `updater/vehicle_rental/datasources/gbfs/GbfsFeedLoader` | `gbfs/client/GbfsFeedLoader` |
| `updater/vehicle_rental/datasources/gbfs/GbfsFeedLoaderImpl` | `gbfs/client/GbfsFeedLoaderImpl` |
| `updater/vehicle_rental/datasources/gbfs/GbfsFeedMapper` | `gbfs/client/GbfsFeedMapper` |
| `updater/vehicle_rental/datasources/gbfs/GbfsFeedDetails` | `gbfs/client/GbfsFeedDetails` |
| `updater/vehicle_rental/datasources/gbfs/GbfsGeofencingZoneMapper` | `gbfs/client/GbfsGeofencingZoneMapper` |
| `updater/vehicle_rental/datasources/gbfs/GbfsVehicleRentalDataSource` | Replaced by `gbfs/GbfsService` or thin adapter in updater |
| `updater/vehicle_rental/datasources/gbfs/support/*` | `gbfs/client/support/*` |
| `updater/vehicle_rental/datasources/gbfs/v2/*` | `gbfs/client/v2/*` |
| `updater/vehicle_rental/datasources/gbfs/v3/*` | `gbfs/client/v3/*` |
| `ext/gbfsgeofencing/internal/graphbuilder/GbfsClient` | **Deleted** — replaced by `gbfs/GbfsService` |
| `ext/gbfsgeofencing/internal/graphbuilder/GeofencingZoneMapper` | **Deleted** — replaced by `gbfs/client/v3/GbfsGeofencingZoneMapper` |

### 4.2 Cleaned-Up Vehicle Rental Service

```
o.o.service.vehiclerental/                             -- DOMAIN MODEL (follows canonical pattern)
    configure/
      VehicleRentalRepositoryModule                    -- Unchanged
      VehicleRentalServiceModule                       -- Unchanged
    internal/
      DefaultVehicleRentalService                      -- Unchanged (implements both interfaces)
    model/
      VehicleRentalPlace                               -- Interface
      VehicleRentalStation                             -- Docked station
      VehicleRentalVehicle                             -- Free-floating vehicle
      VehicleRentalSystem                              -- System metadata
      RentalVehicleType                                -- Type info (formFactor, propulsionType, maxRange)
      RentalVehicleFuel                                -- Fuel/range data
      GeofencingZone                                   -- Zone model (stays here — domain concept)
      ReturnPolicy                                     -- Enum
      RentalVehicleEntityCounts                        -- Counts record
      RentalVehicleTypeCount                           -- Per-type count
      VehicleRentalStationUris                         -- Deep-link URIs
    street/                                            -- Geofencing restriction extensions
      GeofencingZoneApplier                            -- Applies zones to street edges
      GeofencingZoneExtension                          -- RentalRestrictionExtension impl
      BusinessAreaBorder                               -- RentalRestrictionExtension impl
      CompositeRentalRestrictionExtension              -- Composite pattern
      NoRestriction                                    -- Null-object default
    VehicleRentalService                               -- Read-only interface
    VehicleRentalRepository                            -- Write interface
    package.md                                         -- NEW: architecture documentation
```

**What changes:**
- `VehicleRentalEdge`, `StreetVehicleRentalLink`, and `VehicleRentalPlaceVertex` move out (see 4.3)
- The `street/` sub-package is retained **only for geofencing restriction extensions** (see section 7 for rationale)
- A `package.md` is added documenting the domain

### 4.3 Street Model Integration (Moved)

```
o.o.street.model.edge/
    VehicleRentalEdge                                  -- MOVED from service/vehiclerental/street/
    StreetVehicleRentalLink                            -- MOVED from service/vehiclerental/street/

o.o.street.model.vertex/
    VehicleRentalPlaceVertex                           -- MOVED from service/vehiclerental/street/
```

**Rationale**: This aligns with the `vehicleparking` precedent:
- `VehicleParkingEdge` is in `street/model/edge/`
- `StreetVehicleParkingLink` is in `street/model/edge/`
- `VehicleParkingEntranceVertex` is in `street/model/vertex/`
- `VertexFactory` already has a `vehicleRentalPlace()` factory method

The graph topology classes (edges and vertices) are street model concepts — they define how domain entities participate in the street graph. The domain model (stations, vehicles, types) stays in the service package.

### 4.4 Thinner Updater

```
o.o.updater.vehicle_rental/
    VehicleRentalUpdater                               -- PollingGraphUpdater (uses gbfs/GbfsService)
    VehicleRentalUpdaterParameters
    VehicleRentalSourceType
    datasources/
      VehicleRentalDataSource                          -- Interface (may become simpler)
      VehicleRentalDataSourceFactory                   -- Factory
      params/
        VehicleRentalDataSourceParameters
        GbfsVehicleRentalDataSourceParameters
        RentalPickupType
      GbfsVehicleRentalDataSource                      -- Thin adapter: wraps gbfs/GbfsService
    package.md                                         -- NEW
```

**What changes:**
- The entire `datasources/gbfs/` sub-tree is gone — moved to `gbfs/`
- `GbfsVehicleRentalDataSource` becomes a thin adapter that wraps `gbfs/GbfsService` and implements `VehicleRentalDataSource`
- `GeofencingVertexUpdater` is eliminated — the updater calls `GeofencingZoneApplier` directly (it was already a trivial wrapper)

### 4.5 Simplified GbfsGeofencing Sandbox

```
o.o.ext.gbfsgeofencing/
    config/
      GbfsGeofencingConfig                             -- Unchanged
    configure/
      GbfsGeofencingGraphBuilderModule                 -- Unchanged
    internal/
      graphbuilder/
        GbfsGeofencingGraphBuilder                     -- Uses gbfs/GbfsService instead of own GbfsClient
    parameters/
      GbfsGeofencingParameters                         -- Unchanged
      GbfsGeofencingFeedParameters                     -- Unchanged
```

**What changes:**
- `GbfsClient` is **deleted** — replaced by `gbfs/GbfsService`
- `GeofencingZoneMapper` is **deleted** — replaced by the shared `gbfs/client/v3/GbfsGeofencingZoneMapper`
- `GbfsGeofencingGraphBuilder` is simplified to use the shared GBFS service for fetching and mapping

---

## 5. Dependency Direction

```
                    ┌────────────────┐
                    │  GraphQL APIs  │
                    └───────┬────────┘
                            │ reads from
                            v
                  ┌───────────────────┐
                  │ service/          │
                  │ vehiclerental/    │ ← Pure domain model
                  │ (model + svc)     │    No GBFS, no edges/vertices
                  └──────┬────────────┘
                         │ domain types used by
            ┌────────────┼────────────────┐
            v            v                v
   ┌──────────────┐ ┌─────────┐  ┌─────────────────┐
   │  updater/    │ │ street/ │  │ ext/             │
   │  vehicle_    │ │ model/  │  │ gbfsgeofencing/  │
   │  rental/     │ │ edge/   │  │ (sandbox)        │
   │              │ │ vertex/ │  │                  │
   └──────┬───────┘ └─────────┘  └────────┬────────┘
          │                               │
          └──────────┬────────────────────┘
                     │ both use
                     v
            ┌────────────────┐
            │   o.o.gbfs/    │ ← Protocol/transport package
            │   (client +    │    Version detection, HTTP,
            │    mappers)    │    GBFS-to-domain mapping
            └────────────────┘
                     │
                     │ maps to
                     v
            ┌────────────────┐
            │ service/       │
            │ vehiclerental/ │ ← Domain model types
            │ model/         │
            └────────────────┘
```

**Dependency rules:**
- `gbfs/` depends on `service/vehiclerental/model/` (to produce domain types)
- `gbfs/` depends on `street/model/` (for `RentalFormFactor`)
- `gbfs/` does NOT depend on `updater/` or `ext/`
- `updater/vehicle_rental/` depends on `gbfs/` (to fetch data) and `service/vehiclerental/` (to store data)
- `ext/gbfsgeofencing/` depends on `gbfs/` (to fetch zones) and `service/vehiclerental/street/` (to apply zones)
- `street/model/edge/` depends on `service/vehiclerental/model/` (for `VehicleRentalPlace` used by `VehicleRentalEdge`) — same pattern as `VehicleParkingEdge` depending on `service/vehicleparking/model/VehicleParking`

---

## 6. Refactoring Sequence

The work is organized into independently shippable themes, ordered by dependency:

### Theme 1: Extract GBFS Package

**Goal**: Create `o.o.gbfs/` and move all GBFS protocol code there.

Steps:
1. Create `o.o.gbfs/client/` package structure
2. Move the 20+ classes from `updater/vehicle_rental/datasources/gbfs/` to `gbfs/client/`
3. Create `GbfsService` facade providing high-level operations
4. Update `GbfsVehicleRentalDataSource` in the updater to become a thin adapter wrapping `GbfsService`
5. Update `GbfsGeofencingGraphBuilder` to use `GbfsService` instead of its own `GbfsClient`
6. Delete `ext/gbfsgeofencing/internal/graphbuilder/GbfsClient.java`
7. Delete `ext/gbfsgeofencing/internal/graphbuilder/GeofencingZoneMapper.java`
8. Add `gbfs/package.md`

**Impact**: Large number of import changes, but no behavioral changes. All tests should pass unchanged.

### Theme 2: Move Edges and Vertices to Street Model

**Goal**: Align with the vehicleparking precedent.

Steps:
1. Move `VehicleRentalPlaceVertex` to `street/model/vertex/`
2. Move `VehicleRentalEdge` to `street/model/edge/`
3. Move `StreetVehicleRentalLink` to `street/model/edge/`
4. Update all imports (mechanical refactor)

**Impact**: Pure mechanical move. Import churn only.

### Theme 3: Eliminate GeofencingVertexUpdater Wrapper

**Goal**: Remove the trivial wrapper.

Steps:
1. Replace `GeofencingVertexUpdater` usage in `VehicleRentalUpdater` with direct `GeofencingZoneApplier` calls
2. Delete `GeofencingVertexUpdater.java`

**Impact**: Minimal — one class deleted, one caller updated.

### Theme 4: Add Documentation

**Goal**: Add `package.md` files to document the architecture.

Steps:
1. Add `service/vehiclerental/package.md`
2. Add `gbfs/package.md`
3. Add `updater/vehicle_rental/package.md`

**Impact**: None. Documentation only.

### Dependency Order

```
Theme 1 (Extract GBFS) ← Do first — everything else benefits
    │
    ├── Theme 2 (Move edges/vertices) ← Independent, can parallel
    │
    ├── Theme 3 (Eliminate wrapper) ← Trivial, can parallel
    │
    └── Theme 4 (Documentation) ← Do alongside or after
```

---

## 7. Design Decisions and Rationale

### 7.1 Why a Top-Level `o.o.gbfs/` Package?

GBFS is a data import protocol, analogous to GTFS, NeTEx, and OSM. Each of these has a top-level package:
- `o.o.gtfs/` — GTFS import
- `o.o.netex/` — NeTEx import
- `o.o.osm/` — OpenStreetMap import

A top-level `o.o.gbfs/` follows the same convention. It also signals that GBFS is not specific to any one domain — it could serve vehicle parking in the future (GBFS includes parking data feeds), or other mobility data.

**Alternative considered**: Keep GBFS under `updater/vehicle_rental/datasources/gbfs/` but make it public. Rejected because: (a) it couples the protocol to the updater framework, (b) it prevents the build-time sandbox from using it cleanly, and (c) it doesn't match the precedent set by GTFS/NeTEx/OSM.

### 7.2 Why Keep the `street/` Sub-Package for Geofencing Extensions?

The geofencing restriction extensions (`GeofencingZoneExtension`, `BusinessAreaBorder`, `CompositeRentalRestrictionExtension`, `NoRestriction`, `GeofencingZoneApplier`) are kept in `service/vehiclerental/street/` rather than moved to `street/model/`. Rationale:

1. **They are rental-domain-specific.** These classes check rental state (`VehicleRentalState`), rental networks, and rental-specific zone rules. They are not generic street model concerns.
2. **The `RentalRestrictionExtension` interface is already in `street/model/`.** Having the interface in the street model and the implementations in the rental domain follows the dependency inversion principle — the street model defines the contract, the domain provides the implementation.
3. **`GeofencingZoneApplier` bridges two domains.** It reads `GeofencingZone` domain objects and writes to `StreetEdge` graph objects. Keeping it in the rental domain (rather than the street model) keeps the dependency direction correct: rental depends on street, not the other way.
4. **Precedent**: The bidirectional dependency between `street/model/RentalRestrictionExtension` (which references `CompositeRentalRestrictionExtension` and `NoRestriction`) is an existing architectural decision. Resolving it would require an interface-only extraction into `street/model/` which is a larger change that can be tackled separately.

### 7.3 Why Move VehicleRentalEdge/Vertex to `street/model/`?

The `vehicleparking` domain sets the precedent:
- `VehicleParkingEdge` is in `street/model/edge/` (not in `service/vehicleparking/`)
- `VehicleParkingEntranceVertex` is in `street/model/vertex/`
- Both import from `service/vehicleparking/model/` for domain types

Vehicle rental should follow the same pattern. The edges and vertices are graph topology classes — they define how a domain entity participates in the street graph. They extend `Edge` and `Vertex` base classes from the street model.

The alternative (keeping them in `service/vehiclerental/street/`) is lower-risk but perpetuates an inconsistency with `vehicleparking`.

### 7.4 Why Not a Separate Geofencing Service?

Geofencing zones are currently defined exclusively by vehicle rental operators via GBFS feeds. They are scoped to rental networks and checked only during rental-mode routing. Making a separate `service/geofencing/` would be premature abstraction — there is no current consumer of geofencing zones outside the rental context.

If geofencing becomes relevant to other mobility modes in the future, extraction can happen then. For now, `GeofencingZone` stays in `service/vehiclerental/model/`.

### 7.5 What About the `GbfsService` Facade?

The `GbfsService` facade wraps `GbfsFeedLoaderAndMapper` and provides a stable public API for consumers. It replaces two current patterns:
- The updater's `GbfsVehicleRentalDataSource` (which wraps `GbfsFeedLoaderAndMapper` and implements the updater's `VehicleRentalDataSource` interface)
- The sandbox's `GbfsClient` (which fetches and maps GBFS data independently)

The facade should provide methods like:
```java
public class GbfsService {
    /** Fetch and update all feeds. Returns true if new data available. */
    boolean update();

    /** Get the latest mapped rental places. */
    List<VehicleRentalPlace> getRentalPlaces();

    /** Get the latest mapped geofencing zones. */
    List<GeofencingZone> getGeofencingZones();
}
```

The updater's `GbfsVehicleRentalDataSource` becomes a thin adapter implementing `VehicleRentalDataSource` by delegating to `GbfsService`.

---

## 8. What Does NOT Change

To keep scope manageable, the following are explicitly out of scope:

- **`StreetEdge` rental traversal logic (~200 lines)**: The rental-specific branching in `StreetEdge.traverse()` and `doTraverse()` is deeply embedded in the routing engine. Extracting it would be a large, high-risk change.
- **`StateData` rental fields (7 fields)**: These are intrinsic to the A* routing state machine. They cannot be moved without changing the routing architecture.
- **Thread-safety pattern**: `DefaultVehicleRentalService` uses `ConcurrentHashMap` rather than volatile + immutable collections. This is functionally correct and low priority to change.
- **Serializable deviation**: The rental repository is not `Serializable`. This is intentional — rental data is fully runtime and not persisted in `graph.obj`.
- **PropulsionType routing integration**: This is a separate feature being developed on another branch.
- **Speed restriction mapping**: Requires additional GBFS mapping and routing state changes, tracked separately.

---

## 9. Summary of File Movements

| File | Current Location | Target Location |
|---|---|---|
| `GbfsFeedLoaderAndMapper.java` | `updater/vehicle_rental/datasources/gbfs/` | `gbfs/client/` |
| `GbfsFeedLoader.java` (interface) | `updater/vehicle_rental/datasources/gbfs/` | `gbfs/client/` |
| `GbfsFeedLoaderImpl.java` | `updater/vehicle_rental/datasources/gbfs/` | `gbfs/client/` |
| `GbfsFeedMapper.java` (interface) | `updater/vehicle_rental/datasources/gbfs/` | `gbfs/client/` |
| `GbfsFeedDetails.java` | `updater/vehicle_rental/datasources/gbfs/` | `gbfs/client/` |
| `GbfsGeofencingZoneMapper.java` | `updater/vehicle_rental/datasources/gbfs/` | `gbfs/client/` |
| `GbfsVehicleRentalDataSource.java` | `updater/vehicle_rental/datasources/gbfs/` | `gbfs/` (reworked as `GbfsService`) |
| `UnknownVehicleTypeFilter.java` | `updater/vehicle_rental/datasources/gbfs/support/` | `gbfs/client/support/` |
| `v2/GbfsFeedLoader.java` | `updater/vehicle_rental/datasources/gbfs/v2/` | `gbfs/client/v2/` |
| `v2/GbfsFeedMapper.java` | `updater/vehicle_rental/datasources/gbfs/v2/` | `gbfs/client/v2/` |
| `v2/GbfsGeofencingZoneMapper.java` | `updater/vehicle_rental/datasources/gbfs/v2/` | `gbfs/client/v2/` |
| `v2/GbfsStationInformationMapper.java` | `updater/vehicle_rental/datasources/gbfs/v2/` | `gbfs/client/v2/` |
| `v2/GbfsStationStatusMapper.java` | `updater/vehicle_rental/datasources/gbfs/v2/` | `gbfs/client/v2/` |
| `v2/GbfsFreeVehicleStatusMapper.java` | `updater/vehicle_rental/datasources/gbfs/v2/` | `gbfs/client/v2/` |
| `v2/GbfsSystemInformationMapper.java` | `updater/vehicle_rental/datasources/gbfs/v2/` | `gbfs/client/v2/` |
| `v2/GbfsVehicleTypeMapper.java` | `updater/vehicle_rental/datasources/gbfs/v2/` | `gbfs/client/v2/` |
| `v3/GbfsFeedLoader.java` | `updater/vehicle_rental/datasources/gbfs/v3/` | `gbfs/client/v3/` |
| `v3/GbfsFeedMapper.java` | `updater/vehicle_rental/datasources/gbfs/v3/` | `gbfs/client/v3/` |
| `v3/GbfsGeofencingZoneMapper.java` | `updater/vehicle_rental/datasources/gbfs/v3/` | `gbfs/client/v3/` |
| `v3/GbfsStationInformationMapper.java` | `updater/vehicle_rental/datasources/gbfs/v3/` | `gbfs/client/v3/` |
| `v3/GbfsStationStatusMapper.java` | `updater/vehicle_rental/datasources/gbfs/v3/` | `gbfs/client/v3/` |
| `v3/GbfsVehicleStatusMapper.java` | `updater/vehicle_rental/datasources/gbfs/v3/` | `gbfs/client/v3/` |
| `v3/GbfsSystemInformationMapper.java` | `updater/vehicle_rental/datasources/gbfs/v3/` | `gbfs/client/v3/` |
| `v3/GbfsVehicleTypeMapper.java` | `updater/vehicle_rental/datasources/gbfs/v3/` | `gbfs/client/v3/` |
| `VehicleRentalEdge.java` | `service/vehiclerental/street/` | `street/model/edge/` |
| `StreetVehicleRentalLink.java` | `service/vehiclerental/street/` | `street/model/edge/` |
| `VehicleRentalPlaceVertex.java` | `service/vehiclerental/street/` | `street/model/vertex/` |
| `GeofencingVertexUpdater.java` | `updater/vehicle_rental/` | **Deleted** |
| `GbfsClient.java` | `ext/gbfsgeofencing/internal/graphbuilder/` | **Deleted** |
| `GeofencingZoneMapper.java` | `ext/gbfsgeofencing/internal/graphbuilder/` | **Deleted** |

---

## 10. Open Questions

1. **`GbfsService` naming**: Should the facade be called `GbfsService` (matches OTP convention) or `GbfsClient` (clearer protocol semantics)? In the canonical OTP pattern, "Service" implies a domain read-only interface. Since this is a data-fetching concern, `GbfsFeedService` or `GbfsDataSource` might be more appropriate.

2. **`gbfs/model/` necessity**: Do we need GBFS-specific model types, or do the existing `service/vehiclerental/model/` types suffice? Currently all GBFS mappers produce vehicle rental domain types directly. A GBFS-specific model would add a mapping layer but improve separation. The pragmatic choice is to skip it for now and add it if/when GBFS serves a second domain.

3. **Test relocation**: The tests under `updater/vehicle_rental/datasources/gbfs/` need to move to `gbfs/`. Are there integration tests that depend on the updater wiring that would need refactoring?

4. **`VehicleRentalServiceDirectory` sandbox**: The `ext/vehiclerentalservicedirectory/` sandbox currently creates GBFS data sources using `GbfsVehicleRentalDataSource` from the updater package. After the GBFS extraction, it should use `GbfsService` directly. This sandbox should be updated as part of Theme 1.

---

## 11. Related Documents

- [Current State Analysis](2026-02-24-vehicle-rental-gbfs-architecture-design.md) — Detailed inventory of all current code
- `ARCHITECTURE.md` — OTP layered architecture overview
- `service/package.md` — Canonical service package layout
- `doc/dev/decisionrecords/NamingConventions.md` — Package naming conventions
