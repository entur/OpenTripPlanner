---
date: 2026-02-26T13:43:00+01:00
researcher: Claude
git_commit: 95d83bcceb
branch: poc/sandbox-geofencing-zones-in-graph-builder
repository: OpenTripPlanner
topic: "Vehicle Rental & GBFS Domain — Architecture Design (Post Street Module Refactoring)"
tags: [architecture, design, vehicle-rental, gbfs, geofencing, street-module, domain-model, refactoring]
status: complete
last_updated: 2026-02-26
last_updated_by: Claude
supersedes: 2026-02-24-vehicle-rental-gbfs-architecture-design.md
---

# Vehicle Rental & GBFS Domain — Architecture Design (Updated)

**Date**: 2026-02-26T13:43:00+01:00
**Researcher**: Claude
**Git Commit**: 95d83bcceb
**Branch**: poc/sandbox-geofencing-zones-in-graph-builder
**Repository**: OpenTripPlanner
**Supersedes**: [2026-02-24 Architecture Design](2026-02-24-vehicle-rental-gbfs-architecture-design.md)

## 1. Purpose

This document maps the current state of the vehicle rental and GBFS domain in OTP, updated to reflect the **new `street` Maven module** introduced in February 2026. The previous research (Feb 24) was conducted before the street module refactoring was merged. This update captures the structural changes and their impact on the vehicle rental domain.

---

## 2. Major Structural Change: The `street` Maven Module

### 2.1 What Changed

Between the previous research (commit `f5d633412c`) and now (commit `95d83bcceb`), a series of PRs by Leonard Ehrenfried extracted street-related code from the `application/` module into a new top-level `street/` Maven module:

| PR | Title | Merged | Key Change |
|---|---|---|---|
| #7283 | Pre-street-module cleanup | 2026-02-11 | Broke circular dependencies from street code back to application |
| #7144 | Street module extraction | 2026-02-17 | Created `street/` module, moved ~249 files |
| #7312 | Move Graph | 2026-02-20 | Moved `Graph.java` into street module |
| #7328 | Street search builder | 2026-02-25 | Moved `StreetSearchBuilder`, `VehicleParkingHelper` |

### 2.2 New Module Hierarchy

The root `pom.xml` now declares 8 modules (3 are new since the earlier codebase):

```
utils              ← Lowest level, no OTP dependencies
domain-core        ← NEW: Core domain types (FeedScopedId, I18NString, Cost, etc.)
raptor             ← Transit routing (unchanged)
astar              ← NEW: Generic A* search algorithm with SPI
gtfs-realtime-protobuf
street             ← NEW: Street graph, model, search, vehicle services
application        ← Main OTP application (depends on street, raptor, etc.)
otp-shaded         ← Unified JAR
test/integration
```

Dependency chain: `utils` → `domain-core` → `astar` → `street` → `application`

### 2.3 Impact on Vehicle Rental

The `service/vehiclerental/` and `service/vehicleparking/` packages moved from `application/` to the `street/` module. The Java package names are unchanged (`org.opentripplanner.service.vehiclerental.*`), but the Maven module location changed:

- **Before**: `application/src/main/java/org/opentripplanner/service/vehiclerental/`
- **After**: `street/src/main/java/org/opentripplanner/service/vehiclerental/`

This means the vehicle rental domain model, service interfaces, and street-facing code all live in the `street` module, while the GBFS updater code remains in `application/`.

---

## 3. Current State of the Vehicle Rental Domain

### 3.1 Package Inventory (Updated)

The vehicle rental domain is now spread across **three Maven modules** and **four Java package locations**:

| Maven Module | Java Package | Contents |
|---|---|---|
| `street` | `service/vehiclerental/` | Domain model, service/repository, street edges/vertices, geofencing extensions |
| `application` | `updater/vehicle_rental/` | GBFS polling updater, data sources, GBFS mappers, geofencing vertex updater |
| `application` | `ext/gbfsgeofencing/` | Build-time geofencing sandbox (includes GeofencingZoneApplier) |
| `application` | `ext/vehiclerentalservicedirectory/` | GBFS manifest-based feed discovery sandbox |

### 3.2 Service Layer (`street` module: `service/vehiclerental/`)

```
street/src/main/java/org/opentripplanner/service/vehiclerental/
    configure/
      VehicleRentalRepositoryModule.java    -- @Binds repo → DefaultVehicleRentalService
      VehicleRentalServiceModule.java       -- @Binds service → DefaultVehicleRentalService
    internal/
      DefaultVehicleRentalService.java      -- Single class implements BOTH interfaces
    model/
      GeofencingZone.java                   -- Record: id, name, geometry, dropOffBanned, traversalBanned
      RentalVehicleEntityCounts.java        -- Record: total + byType list
      RentalVehicleFuel.java                -- GBFS v3 fuel data
      RentalVehicleType.java                -- id, name, formFactor, propulsionType, maxRange
      RentalVehicleTypeCount.java           -- Record: vehicleType + count
      ReturnPolicy.java                     -- Enum: ANY_TYPE, SPECIFIC_TYPES
      VehicleRentalPlace.java               -- Interface: root of the rental place hierarchy
      VehicleRentalStation.java             -- Docked station (immutable, builder pattern)
      VehicleRentalStationBuilder.java      -- Builder for VehicleRentalStation
      VehicleRentalStationUris.java         -- Deep-link URIs
      VehicleRentalSystem.java              -- GBFS system_information model
      VehicleRentalVehicle.java             -- Free-floating vehicle (immutable, inner builder)
    street/
      BusinessAreaBorder.java               -- RentalRestrictionExtension impl
      CompositeRentalRestrictionExtension.java -- Combines multiple extensions
      GeofencingZoneExtension.java          -- RentalRestrictionExtension impl for restricted zones
      NoRestriction.java                    -- Default no-op extension
      StreetVehicleRentalLink.java          -- Edge: street ↔ rental vertex
      VehicleRentalEdge.java                -- Loop edge: pickup/drop-off state machine
      VehicleRentalPlaceVertex.java         -- Vertex for rental places
    VehicleRentalRepository.java            -- Write interface
    VehicleRentalService.java               -- Read interface

Test fixtures (street module):
    street/src/test-fixtures/java/.../service/vehiclerental/model/
      TestFreeFloatingRentalVehicleBuilder.java
      TestVehicleRentalStationBuilder.java

Tests (application module):
    application/src/test/java/.../service/vehiclerental/
      internal/DefaultVehicleRentalServiceTest.java
      model/TestVehicleRentalStationBuilder.java
      model/TestFreeFloatingRentalVehicleBuilder.java
```

**Key change from previous research**: `GeofencingZoneApplier` is **no longer** in `service/vehiclerental/street/`. It now lives only in the geofencing sandbox (`ext/gbfsgeofencing/internal/graphbuilder/GeofencingZoneApplier.java`).

### 3.3 Updater Layer (`application` module: `updater/vehicle_rental/`)

```
application/src/main/java/org/opentripplanner/updater/vehicle_rental/
    VehicleRentalUpdater.java               -- PollingGraphUpdater + inner GraphWriterRunnable
    VehicleRentalUpdaterParameters.java     -- Config wrapper
    VehicleRentalSourceType.java            -- Enum: GBFS, SMOOVE
    GeofencingVertexUpdater.java            -- Thin wrapper around zone application logic
    datasources/
      VehicleRentalDataSource.java          -- Interface
      VehicleRentalDataSourceFactory.java   -- Factory: GBFS → GbfsVehicleRentalDataSource
      params/
        VehicleRentalDataSourceParameters.java   -- Interface
        GbfsVehicleRentalDataSourceParameters.java -- Record with all GBFS config
        RentalPickupType.java               -- Enum: STATION, FREE_FLOATING
      gbfs/
        GbfsFeedDetails.java                -- Interface
        GbfsFeedLoader.java                 -- Interface
        GbfsFeedLoaderImpl.java             -- Abstract: HTTP + TTL + ETag caching
        GbfsFeedMapper.java                 -- Interface: getUpdates(), getGeofencingZones()
        GbfsFeedLoaderAndMapper.java        -- Version detector + wiring
        GbfsVehicleRentalDataSource.java    -- Implements VehicleRentalDataSource
        GbfsGeofencingZoneMapper.java       -- Abstract: template method for zone mapping
        support/
          UnknownVehicleTypeFilter.java
        v2/                                 -- 8 files: v2 loader + 7 mappers
        v3/                                 -- 8 files: v3 loader + 7 mappers
```

No changes to the GBFS updater structure. All GBFS code remains in the `application` module.

### 3.4 Street Model Integration (Updated Locations)

All street model classes are now in the `street` Maven module:

| Class | Maven Module | Java Package | Role |
|---|---|---|---|
| `RentalFormFactor` | `street` | `street.model` | Maps vehicle form to `TraverseMode` |
| `RentalRestrictionExtension` | `street` | `street.model` | Interface for geofencing restrictions on vertices |
| `VehicleRentalState` | `street` | `street.search.state` | Enum: rental state machine |
| `StateData` | `street` | `street.search.state` | 7 rental-specific fields |
| `StateEditor` | `street` | `street.search.state` | 7 rental-specific methods |
| `State` | `street` | `street.search.state` | ~15 rental accessor/query methods |
| `StreetEdge` | `street` | `street.model.edge` | ~202 lines of rental-specific traversal logic |
| `Vertex` | `street` | `street.model.vertex` | 5 rental restriction delegation methods |

### 3.5 Geofencing Sandbox (`application` module: `ext/gbfsgeofencing/`)

```
application/src/ext/java/org/opentripplanner/ext/gbfsgeofencing/
    config/
      GbfsGeofencingConfig.java             -- Configuration mapping
    configure/
      GbfsGeofencingGraphBuilderModule.java -- Dagger module
    internal/graphbuilder/
      GbfsClient.java                       -- Standalone GBFS v3 HTTP client
      GbfsGeofencingGraphBuilder.java       -- GraphBuilderModule impl
      GeofencingZoneApplier.java            -- Applies zones to street edges
      GeofencingZoneMapper.java             -- GBFS v3 → GeofencingZone mapping
    parameters/
      GbfsGeofencingParameters.java         -- Config parameters
      GbfsGeofencingFeedParameters.java     -- Per-feed parameters
```

**Key change**: `GeofencingZoneApplier` exists **only** here now, not in the service package.

### 3.6 API Surface (Unchanged)

**GTFS GraphQL API** exposes:
- `VehicleRentalStation`, `RentalVehicle`, `RentalPlace` (union), `VehicleRentalNetwork`, `RentalVehicleType`, `RentalVehicleFuel`, `RentalVehicleEntityCounts`
- Root queries: `vehicleRentalStation(id)`, `vehicleRentalStations`, `rentalVehicle(id)`, `rentalVehicles`, `vehicleRentalsByBbox`
- Deprecated: `bikeRentalStation`, `BikeRentalStation` type

**Transmodel GraphQL API** exposes:
- `BikeRentalStation`, `RentalVehicle`, `RentalVehicleType`
- Root queries: `bikeRentalStation(id)`, `bikeRentalStations`, `bikeRentalStationsByBbox`

---

## 4. Data Flow Diagrams

### 4.1 Runtime GBFS Polling Flow

```
router-config.json "updaters"
  → VehicleRentalUpdaterConfig.create()                    [application module]
    → GbfsVehicleRentalDataSourceParameters
      → VehicleRentalUpdater (PollingGraphUpdater)         [application module]
        → GbfsFeedLoaderAndMapper (detects GBFS version)   [application module]
          → v2 or v3 GbfsFeedLoader (HTTP + TTL + ETag)
          → v2 or v3 GbfsFeedMapper
            → GbfsSystemInformationMapper     → VehicleRentalSystem      [street module model]
            → GbfsVehicleTypeMapper           → RentalVehicleType        [street module model]
            → GbfsStationInformationMapper    → VehicleRentalStation     [street module model]
            → GbfsStationStatusMapper         → VehicleRentalStation     [street module model]
            → GbfsFreeVehicleStatusMapper     → VehicleRentalVehicle     [street module model]
            → GbfsGeofencingZoneMapper        → GeofencingZone           [street module model]
        → VehicleRentalGraphWriterRunnable (graph writer thread)
          ├─ VehicleRentalRepository.addVehicleRentalStation() [street module]
          ├─ VertexFactory → VehicleRentalPlaceVertex          [street module]
          ├─ VertexLinker → StreetVehicleRentalLink            [street module]
          ├─ VehicleRentalEdge (one per form factor)           [street module]
          └─ GeofencingVertexUpdater                           [application module]
               → applies zones to street edges
```

### 4.2 Build-Time Geofencing Flow (Sandbox)

```
build-config.json "gbfsGeofencing"
  → GbfsGeofencingConfig.mapConfig()                       [application ext]
    → GbfsGeofencingParameters
      → GbfsGeofencingGraphBuilder (GraphBuilderModule)    [application ext]
        → GbfsClient (v3 only, standalone)                 [application ext]
          → HTTP fetch gbfs.json discovery
          → HTTP fetch geofencing_zones.json
        → GeofencingZoneMapper → GeofencingZone            [application ext → street module model]
        → GeofencingZoneApplier                            [application ext]
          → StreetEdge.addRentalRestriction()              [street module]
```

### 4.3 Cross-Module Dependency Flow

```
                        ┌───────────────────────────┐
                        │    application module      │
                        │                            │
                        │  updater/vehicle_rental/   │
                        │  ├─ VehicleRentalUpdater   │
                        │  ├─ datasources/gbfs/      │
                        │  └─ GeofencingVertexUpdater │
                        │                            │
                        │  ext/gbfsgeofencing/       │
                        │  ├─ GbfsClient             │
                        │  ├─ GeofencingZoneApplier  │
                        │  └─ GeofencingZoneMapper   │
                        │                            │
                        │  apis/ (GraphQL)           │
                        └────────────┬───────────────┘
                                     │ depends on
                                     v
                        ┌───────────────────────────┐
                        │      street module         │
                        │                            │
                        │  service/vehiclerental/    │
                        │  ├─ model/ (domain types)  │
                        │  ├─ internal/ (service)    │
                        │  └─ street/ (edges,        │
                        │     vertices, extensions)  │
                        │                            │
                        │  street/model/             │
                        │  ├─ RentalFormFactor       │
                        │  ├─ RentalRestrictionExt   │
                        │  └─ edge/StreetEdge        │
                        │                            │
                        │  street/search/state/      │
                        │  ├─ StateData (7 fields)   │
                        │  ├─ StateEditor (7 methods)│
                        │  └─ VehicleRentalState     │
                        └────────────┬───────────────┘
                                     │ depends on
                                     v
                        ┌───────────────────────────┐
                        │    domain-core module      │
                        │  (FeedScopedId, I18N, etc.)│
                        └───────────────────────────┘
```

---

## 5. Comparison Against Canonical Patterns

### 5.1 Canonical Service Pattern (from `service/package.md`)

The seven service domains are now split across two modules:

| Service | Maven Module | Follows Canonical Pattern |
|---|---|---|
| `worldenvelope` | `application` | Yes (reference implementation) |
| `realtimevehicles` | `application` | Yes |
| `osminfo` | `application` | Yes |
| `paging` | `application` | Yes |
| `streetdetails` | `application` | Yes |
| `vehicleparking` | `street` | Yes (edges/vertices in `street/model/edge/` and `street/model/vertex/`) |
| `vehiclerental` | `street` | Partial (has `street/` sub-package with edges/vertices/extensions) |

### 5.2 Vehicle Parking vs. Vehicle Rental: The Key Contrast

Both services now live in the `street` module. But they handle street graph integration differently:

**Vehicle Parking** (canonical pattern):
- `VehicleParkingEdge` → `street/model/edge/VehicleParkingEdge.java`
- `StreetVehicleParkingLink` → `street/model/edge/StreetVehicleParkingLink.java`
- `VehicleParkingEntranceVertex` → `street/model/vertex/VehicleParkingEntranceVertex.java`
- No `street/` sub-package in the service domain

**Vehicle Rental** (deviates):
- `VehicleRentalEdge` → `service/vehiclerental/street/VehicleRentalEdge.java`
- `StreetVehicleRentalLink` → `service/vehiclerental/street/StreetVehicleRentalLink.java`
- `VehicleRentalPlaceVertex` → `service/vehiclerental/street/VehicleRentalPlaceVertex.java`
- Plus geofencing extensions in the same `street/` sub-package

### 5.3 What the Module Extraction Changes for the Previous Target Architecture

The previous target architecture document proposed:
1. Move `VehicleRentalEdge`, `StreetVehicleRentalLink`, `VehicleRentalPlaceVertex` to `street/model/edge/` and `street/model/vertex/`
2. Extract GBFS code to a top-level `o.o.gbfs/` package
3. Clean up the `service/vehiclerental/street/` sub-package

With the new module structure:
- **Point 1 is now a within-module move.** Since both `service/vehiclerental/street/` and `street/model/edge/` are in the same Maven module, moving edges/vertices is simpler — no cross-module dependency changes needed.
- **Point 2 (GBFS extraction) is still relevant.** GBFS code remains in the `application` module under `updater/vehicle_rental/datasources/gbfs/`. Extracting it would either create a new module or move it to a top-level package within `application/`.
- **Point 3 is partially addressed.** `GeofencingZoneApplier` is no longer in `service/vehiclerental/street/`, but the geofencing extensions and edges/vertices remain.

### 5.4 Structural Deviations (Updated)

#### 5.4.1 The `street/` Sub-Package in `service/vehiclerental/`

Still the biggest deviation from the canonical pattern. Contains 7 files:
- 3 graph topology classes (edge, link, vertex)
- 4 geofencing restriction implementations

The vehicle parking service does not have this sub-package.

#### 5.4.2 Geofencing Zone Logic Fragmentation

`GeofencingZoneApplier` now exists **only** in the sandbox (`ext/gbfsgeofencing/internal/graphbuilder/`). The runtime updater uses `GeofencingVertexUpdater` which has its own zone application logic. The geofencing domain is still fragmented:

1. `street` module: `service/vehiclerental/model/GeofencingZone.java` — domain model
2. `street` module: `service/vehiclerental/street/GeofencingZoneExtension.java` — restriction on vertices
3. `street` module: `service/vehiclerental/street/BusinessAreaBorder.java` — border restriction
4. `application` module: `updater/vehicle_rental/GeofencingVertexUpdater.java` — runtime updater wrapper
5. `application` module: `updater/vehicle_rental/datasources/gbfs/GbfsGeofencingZoneMapper.java` — GBFS mapping (abstract + v2/v3)
6. `application` module: `ext/gbfsgeofencing/internal/graphbuilder/GeofencingZoneApplier.java` — applies zones to edges
7. `application` module: `ext/gbfsgeofencing/internal/graphbuilder/GeofencingZoneMapper.java` — sandbox's own mapper

#### 5.4.3 GBFS Client Embedded in Updater

The GBFS protocol code (29 source files + 12 test files) remains entirely within `application/updater/vehicle_rental/datasources/gbfs/`. No separate `gbfs/` package or module has been created.

The build-time sandbox still has its own `GbfsClient` that duplicates HTTP fetching and GBFS discovery logic.

#### 5.4.4 Cross-Module Test Distribution

Tests for vehicle rental are split:
- `street/src/test-fixtures/` — builder fixtures
- `street/src/test/java/.../street/model/edge/VehicleRentalEdgeTest.java` — edge tests (note: in `street.model.edge` test package, not `service.vehiclerental.street`)
- `application/src/test/` — service tests, updater tests, GBFS mapper tests
- `application/src/test-fixtures/` — additional test fixtures

---

## 6. Routing-Time Rental Details

### 6.1 State Machine

```
WALK state → StreetVehicleRentalLink → VehicleRentalPlaceVertex
  → VehicleRentalEdge.traverse()
    ├─ BEFORE_RENTING → beginFloatingVehicleRenting()  → RENTING_FLOATING
    ├─ BEFORE_RENTING → beginVehicleRentingAtStation()  → RENTING_FROM_STATION
    ├─ RENTING_*      → dropOffRentedVehicleAtStation() → HAVE_RENTED
    └─ RENTING_FLOATING → dropFloatingVehicle()         → HAVE_RENTED

While RENTING_*:
  StreetEdge.traverse() checks:
    ├─ tov.rentalTraversalBanned(state) → block or force drop
    ├─ tov.rentalDropOffBanned(state) → enter no-drop-off area
    ├─ entering no-drop-off zone → fork: continue riding + drop-and-walk
    └─ propulsion-aware cost via State.rentalVehiclePropulsionType()
```

### 6.2 StateData Rental Fields (7)

| Field | Type | Description |
|---|---|---|
| `vehicleRentalState` | `VehicleRentalState` | Current rental state |
| `mayKeepRentedVehicleAtDestination` | `boolean` | Keep vehicle at destination |
| `vehicleRentalNetwork` | `String` | Network identifier |
| `rentalVehicleFormFactor` | `RentalFormFactor` | Form factor (bicycle, scooter, etc.) |
| `rentalVehiclePropulsionType` | `PropulsionType` | Propulsion type (human, electric, etc.) |
| `insideNoRentalDropOffArea` | `boolean` | Inside geofencing no-drop-off zone |
| `noRentalDropOffZonesAtStartOfReverseSearch` | `Set<String>` | Networks with restrictions at reverse start |

### 6.3 StateEditor Rental Methods (7)

| Method | Description |
|---|---|
| `beginFloatingVehicleRenting()` | Transition to RENTING_FLOATING |
| `beginVehicleRentingAtStation()` | Transition to RENTING_FROM_STATION |
| `dropOffRentedVehicleAtStation()` | Transition to HAVE_RENTED (station) |
| `dropFloatingVehicle()` | Transition to HAVE_RENTED (floating) |
| `enterNoRentalDropOffArea()` | Set inside no-drop-off zone |
| `leaveNoRentalDropOffArea()` | Clear no-drop-off zone flag |
| `resetStartedInNoDropOffZone()` | Reset reverse search zone tracking |

### 6.4 StreetEdge Rental Logic (~202 lines)

- 3 rental imports
- ~78 lines in `traverse()` (6 rental-specific branches/blocks)
- 7 lines in `doTraverse()` (geofencing zone tracking)
- ~51 lines for propulsion-aware cost calculation
- ~55 lines for private rental helper methods
- ~8 lines for edge splitting rental methods

---

## 7. Configuration Paths

Two configuration paths exist:

1. **Runtime updater** (`router-config.json` → `updaters[]`):
   `VehicleRentalUpdaterConfig` → `VehicleRentalUpdaterParameters` → `VehicleRentalUpdater` (application module)

2. **Build-time geofencing** (`build-config.json` → `gbfsGeofencing`):
   `GbfsGeofencingConfig` → `GbfsGeofencingParameters` → `GbfsGeofencingGraphBuilder` (application ext module)

The vehicle rental service directory sandbox is configured in `router-config.json` via `vehicleRentalServiceDirectory`.

---

## 8. Open Questions (Updated)

1. **Should `VehicleRentalEdge` and `VehicleRentalPlaceVertex` move to `street/model/edge/` and `street/model/vertex/`?** This is now a within-module move (both locations are in the `street` module), making it less risky. The vehicle parking domain already follows this pattern.

2. **Should GBFS become a separate module or top-level package?** The GBFS code (29 files) remains embedded in the `application` module's updater package. With the new module hierarchy (`domain-core` → `astar` → `street` → `application`), a `gbfs` module could sit alongside `application` or the code could move to a top-level package. This would allow the sandbox to reuse the GBFS client.

3. **Where should `GeofencingZoneApplier` live?** It currently exists only in the sandbox. If geofencing zone application needs to be shared with the runtime updater, it should move to a shared location — either `service/vehiclerental/street/` (in the `street` module) or a new shared geofencing package.

4. **Should the `service/vehiclerental/` package stay in the `street` module?** The vehicle rental domain model has no inherent dependency on street graph classes. It was moved to the `street` module because its `street/` sub-package contains edges and vertices. If those were moved to `street/model/edge/` and `street/model/vertex/`, the pure domain model could potentially live in its own module or in `application/`.

5. **PropulsionType location**: `PropulsionType` is currently a nested enum inside `RentalVehicleType` (street module). Since it affects `StreetEdge` cost calculation (also street module), the intra-module reference is clean. But promoting it to a top-level type in `street/model/` (like `RentalFormFactor`) would improve discoverability.

---

## 9. Related Work and References

### Research Documents
- `thoughts/shared/research/2026-02-24-vehicle-rental-gbfs-architecture-design.md` — Previous version of this document (pre-street-module)
- `thoughts/shared/research/2026-02-24-vehicle-rental-gbfs-target-architecture.md` — Target architecture (needs update for module structure)
- `thoughts/shared/research/2025-12-01-gbfs-geofencing-zones-graph-build-sandbox.md` — Initial geofencing sandbox research
- `thoughts/shared/research/2026-02-05-gbfs-geofencing-speed-restrictions.md` — Speed restrictions gap analysis

### Key PRs (Street Module Refactoring)
- PR #7283 — Pre-street-module cleanup
- PR #7144 — Street module extraction (main PR)
- PR #7312 — Move Graph to street module
- PR #7328 — Move StreetSearchBuilder to street module

### Key Source Files (Updated Paths)
- `street/src/main/java/.../service/vehiclerental/VehicleRentalService.java` — Read-only service interface
- `street/src/main/java/.../service/vehiclerental/VehicleRentalRepository.java` — Write interface
- `street/src/main/java/.../service/vehiclerental/internal/DefaultVehicleRentalService.java` — Combined implementation
- `street/src/main/java/.../service/vehiclerental/street/VehicleRentalEdge.java` — Pickup/drop-off state machine
- `street/src/main/java/.../street/model/edge/StreetEdge.java` — ~202 lines of rental traversal logic
- `street/src/main/java/.../street/model/RentalRestrictionExtension.java` — Restriction interface
- `street/src/main/java/.../street/model/RentalFormFactor.java` — Form factor enum
- `street/src/main/java/.../street/search/state/StateData.java` — 7 rental fields
- `street/src/main/java/.../street/search/state/StateEditor.java` — 7 rental methods
- `street/src/main/java/.../street/search/state/VehicleRentalState.java` — State machine enum
- `application/src/main/java/.../updater/vehicle_rental/VehicleRentalUpdater.java` — Runtime polling updater
- `application/src/main/java/.../updater/vehicle_rental/GeofencingVertexUpdater.java` — Runtime geofencing
- `application/src/ext/java/.../ext/gbfsgeofencing/internal/graphbuilder/GeofencingZoneApplier.java` — Build-time zone application
- `application/src/main/java/.../service/package.md` — Canonical service pattern documentation
