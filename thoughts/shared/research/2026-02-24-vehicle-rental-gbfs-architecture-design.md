---
date: 2026-02-24T14:00:00+01:00
researcher: Claude
git_commit: f5d633412c
branch: poc/sandbox-geofencing-zones-in-graph-builder
repository: OpenTripPlanner
topic: "Vehicle Rental & GBFS Domain — Architecture Design Document"
tags: [architecture, design, vehicle-rental, gbfs, geofencing, refactoring, domain-model]
status: complete
last_updated: 2026-02-24
last_updated_by: Claude
---

# Vehicle Rental & GBFS Domain — Architecture Design Document

**Date**: 2026-02-24
**Git Commit**: f5d633412c
**Branch**: poc/sandbox-geofencing-zones-in-graph-builder
**Repository**: OpenTripPlanner

## 1. Purpose

This document maps the current state of the vehicle rental and GBFS domain in OTP, compares it against the project's canonical architectural patterns, identifies structural gaps, and proposes a target architecture to guide refactoring efforts. It is intended to serve as a basis for discussion with the OTP development team.

---

## 2. OTP Canonical Architecture (Reference)

### 2.1 Layered Model

From `ARCHITECTURE.md` and `service/package.md`, OTP defines three layers:

| Layer | Role | Example |
|---|---|---|
| **Use Case Service** | Stateless, combines domain services for a feature | `RoutingService` |
| **Domain Model** | Encapsulates a business area with Service (read) + Repository (write) | `worldenvelope`, `vehicleparking` |
| **Raptor** | Fully isolated routing engine | `raptor/` module |

### 2.2 Canonical Service Package Layout

From `service/package.md`:

```
o.o.service.<name>/
    configure/                   -- Dagger DI modules
      <Name>RepositoryModule
      <Name>ServiceModule
    internal/                    -- Private implementations
      Default<Name>Service
      Default<Name>Repository
    model/                       -- Public domain model
      <Domain Model Classes>
    <Name>Service                -- Read-only interface (root)
    <Name>Repository             -- Write interface (root, if exposed)
    package.md                   -- Architecture documentation
```

**Key rules:**
- `Default<Name>Repository` must be `Serializable` (persisted in `graph.obj`)
- Both defaults must be **thread-safe**
- Repository interface exposed only if used outside the module (by updaters)
- The `worldenvelope` service is the canonical reference example

### 2.3 Two-Phase DI Wiring

- **Load phase** (`LoadApplicationFactory`): registers `*RepositoryModule` classes that produce `Serializable` repositories
- **Construct phase** (`ConstructApplicationFactory`): registers `*ServiceModule` classes; repositories from Load are injected via `@BindsInstance`

### 2.4 Sandbox Extension Pattern

From the `emission` sandbox (`ext/emission/`):

```
o.o.ext.<name>/
    config/                      -- Config mapping
    configure/                   -- Dagger modules (Repository, Service, GraphBuilder)
    internal/                    -- Implementations + graphbuilder/
    model/                       -- Domain model
    parameters/                  -- Configuration parameter types
    <Name>Service                -- @Sandbox annotated interface
    <Name>Repository             -- Interface
    package.md                   -- Documentation
```

Key patterns: `@Nullable` return types for optional features, `OTPFeature` flag gating, `GraphBuilderModule` for build-time work.

### 2.5 Package Naming Conventions

From `doc/dev/decisionrecords/NamingConventions.md`:

- `component.api` — programming interface for outside callers
- `component.model` — entities and value objects
- `component.configure` — Dagger DI wiring
- `component.service` — service implementations
- `support` — internal helpers, not public
- `mapping` — cross-domain translation

---

## 3. Current State of the Vehicle Rental Domain

### 3.1 Package Inventory

The vehicle rental domain is currently spread across **four distinct locations**:

| Location | Contents |
|---|---|
| `service/vehiclerental/` | Domain model, service/repository, street integration, geofencing applier |
| `updater/vehicle_rental/` | GBFS polling updater, data sources, GBFS mappers, geofencing vertex updater |
| `street/model/` | `RentalFormFactor` enum, `RentalRestrictionExtension` interface |
| `ext/gbfsgeofencing/` | Build-time geofencing sandbox |
| `ext/vehiclerentalservicedirectory/` | GBFS manifest-based feed discovery sandbox |

### 3.2 Service Layer (`service/vehiclerental/`)

```
service/vehiclerental/
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
      GeofencingZoneApplier.java            -- Applies zones to street edges (shared)
      GeofencingZoneExtension.java          -- RentalRestrictionExtension impl for restricted zones
      NoRestriction.java                    -- Default no-op extension
      StreetVehicleRentalLink.java          -- Edge: street ↔ rental vertex
      VehicleRentalEdge.java                -- Loop edge: pickup/drop-off state machine
      VehicleRentalPlaceVertex.java         -- Vertex for rental places
    VehicleRentalRepository.java            -- Write interface (2 methods)
    VehicleRentalService.java               -- Read interface (7 methods + spatial queries)
```

**Notable**: No `package.md` exists for this domain.

### 3.3 Updater Layer (`updater/vehicle_rental/`)

```
updater/vehicle_rental/
    VehicleRentalUpdater.java               -- PollingGraphUpdater + inner GraphWriterRunnable
    VehicleRentalUpdaterParameters.java     -- Config wrapper
    VehicleRentalSourceType.java            -- Enum: GBFS, SMOOVE
    GeofencingVertexUpdater.java            -- Thin wrapper around GeofencingZoneApplier
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
        v2/
          GbfsFeedLoader.java               -- v2 specialization
          GbfsFeedMapper.java               -- v2 mapper: system info → stations → vehicles → zones
          GbfsGeofencingZoneMapper.java      -- v2 zone mapping
          GbfsStationInformationMapper.java
          GbfsStationStatusMapper.java
          GbfsFreeVehicleStatusMapper.java
          GbfsSystemInformationMapper.java
          GbfsVehicleTypeMapper.java
        v3/
          GbfsFeedLoader.java               -- v3 specialization
          GbfsFeedMapper.java               -- v3 mapper
          GbfsGeofencingZoneMapper.java      -- v3 zone mapping
          GbfsStationInformationMapper.java
          GbfsStationStatusMapper.java
          GbfsVehicleStatusMapper.java
          GbfsSystemInformationMapper.java
          GbfsVehicleTypeMapper.java
```

### 3.4 Street Model Integration (Cross-Cutting)

| Class | Location | Role |
|---|---|---|
| `RentalFormFactor` | `street/model/` | Maps vehicle form to `TraverseMode` |
| `RentalRestrictionExtension` | `street/model/` | Interface for geofencing restrictions on vertices |
| `VehicleRentalState` | `street/search/state/` | Enum: state machine for rental routing |
| `StateData` fields | `street/search/state/` | `vehicleRentalState`, `rentalVehicleFormFactor`, `rentalVehiclePropulsionType`, `vehicleRentalNetwork`, `insideNoRentalDropOffArea`, etc. |
| `StateEditor` methods | `street/search/state/` | 4 rental transition methods + 2 no-drop-off zone methods |
| `StreetEdge` | `street/model/edge/` | ~200 lines of rental-specific traversal logic in `traverse()` and `doTraverse()` |

### 3.5 API Surface

**GTFS GraphQL API** exposes:
- `VehicleRentalStation`, `RentalVehicle`, `RentalPlace` (union), `VehicleRentalNetwork`, `RentalVehicleType`, `RentalVehicleFuel`, `RentalVehicleEntityCounts`
- Root queries: `vehicleRentalStation(id)`, `vehicleRentalStations`, `rentalVehicle(id)`, `rentalVehicles`, `vehicleRentalsByBbox`
- Deprecated: `bikeRentalStation`, `BikeRentalStation` type

**Transmodel GraphQL API** exposes:
- `BikeRentalStation`, `RentalVehicle`, `RentalVehicleType`
- Root queries: `bikeRentalStation(id)`, `bikeRentalStations`, `bikeRentalStationsByBbox`

### 3.6 Configuration Paths

Two configuration paths exist:

1. **Runtime updater** (`router-config.json` → `updaters[]`): `VehicleRentalUpdaterConfig` → `VehicleRentalUpdaterParameters` → `VehicleRentalUpdater`
2. **Build-time geofencing** (`build-config.json` → `gbfsGeofencing`): `GbfsGeofencingConfig` → `GbfsGeofencingParameters` → `GbfsGeofencingGraphBuilder`

The vehicle rental service directory sandbox is also configured in `router-config.json` via `vehicleRentalServiceDirectory`.

---

## 4. Data Flow Diagrams

### 4.1 Runtime GBFS Polling Flow

```
router-config.json "updaters"
  → VehicleRentalUpdaterConfig.create()
    → GbfsVehicleRentalDataSourceParameters
      → VehicleRentalUpdater (PollingGraphUpdater)
        → GbfsFeedLoaderAndMapper (detects GBFS version)
          → v2 or v3 GbfsFeedLoader (HTTP + TTL + ETag caching)
          → v2 or v3 GbfsFeedMapper
            → GbfsSystemInformationMapper     → VehicleRentalSystem
            → GbfsVehicleTypeMapper           → RentalVehicleType
            → GbfsStationInformationMapper    → VehicleRentalStation
            → GbfsStationStatusMapper         → VehicleRentalStation (merged)
            → GbfsFreeVehicleStatusMapper     → VehicleRentalVehicle
            → GbfsGeofencingZoneMapper        → GeofencingZone
        → VehicleRentalGraphWriterRunnable (graph writer thread)
          ├─ VehicleRentalRepository.addVehicleRentalStation()
          ├─ VertexFactory → VehicleRentalPlaceVertex
          ├─ VertexLinker → StreetVehicleRentalLink (bidirectional)
          ├─ VehicleRentalEdge (one per form factor)
          └─ GeofencingVertexUpdater → GeofencingZoneApplier
               → StreetEdge.addRentalRestriction()
```

### 4.2 Build-Time Geofencing Flow (Sandbox)

```
build-config.json "gbfsGeofencing"
  → GbfsGeofencingConfig.mapConfig()
    → GbfsGeofencingParameters
      → GbfsGeofencingGraphBuilder (GraphBuilderModule)
        → GbfsClient (v3 only)
          → HTTP fetch gbfs.json discovery
          → HTTP fetch geofencing_zones.json
        → GeofencingZoneMapper → GeofencingZone
        → GeofencingZoneApplier
          → StreetEdge.addRentalRestriction()
```

### 4.3 Routing-Time Rental State Machine

```
WALK state → StreetVehicleRentalLink → VehicleRentalPlaceVertex
  → VehicleRentalEdge.traverse()
    ├─ BEFORE_RENTING → beginFloatingVehicleRenting()  → RENTING_FLOATING
    ├─ BEFORE_RENTING → beginVehicleRentingAtStation()  → RENTING_FROM_STATION
    ├─ RENTING_*      → dropOffRentedVehicleAtStation() → HAVE_RENTED
    └─ RENTING_FLOATING → dropFloatingVehicle()         → HAVE_RENTED

While RENTING_*:
  StreetEdge.traverse() checks:
    ├─ tov.rentalTraversalBanned(state) → block
    ├─ tov.rentalDropOffBanned(state) → enter no-drop-off area
    ├─ entering no-drop-off zone → fork: continue riding + drop-and-walk
    └─ propulsion-aware cost via State.rentalVehiclePropulsionType()
```

---

## 5. Comparison Against Canonical Patterns

### 5.1 What Follows the Pattern Well

| Aspect | Assessment |
|---|---|
| Service/Repository split | Correct: `VehicleRentalService` (read) + `VehicleRentalRepository` (write) |
| Single implementation for both | Acceptable pattern (matches `RealtimeVehicleService`) |
| Dagger modules in `configure/` | Correct: two modules with `@Binds` |
| Thread-safety | Uses `ConcurrentHashMap` — functional but differs from canonical `volatile` + immutable collections |
| Domain model in `model/` | Correct placement, immutable types with builders |
| Sandbox in `ext/` | `gbfsgeofencing` follows the emission pattern well |

### 5.2 Structural Deviations

#### 5.2.1 The `street/` Sub-Package Problem

The `service/vehiclerental/street/` sub-package is the biggest deviation. The canonical service pattern has no `street/` sub-package. This package contains:
- Graph edges and vertices (`VehicleRentalEdge`, `VehicleRentalPlaceVertex`, `StreetVehicleRentalLink`)
- Geofencing restriction implementations (`GeofencingZoneExtension`, `BusinessAreaBorder`, `CompositeRentalRestrictionExtension`, `NoRestriction`)
- Graph mutation logic (`GeofencingZoneApplier`)

These are **street model concerns** that couple the domain service to the street routing graph. In the canonical model:
- Graph integration classes belong in the `street/` or `routing/` domains
- The service domain should be infrastructure-agnostic

Meanwhile, `RentalFormFactor` and `RentalRestrictionExtension` (the interface) live in `street/model/`, which creates a **bidirectional dependency**:
- `street/model/` → `service/vehiclerental/street/` (for `CompositeRentalRestrictionExtension`, `NoRestriction`)
- `service/vehiclerental/street/` → `street/model/` (for `RentalFormFactor`, `RentalRestrictionExtension`, `StreetEdge`)

#### 5.2.2 Geofencing Lives in Multiple Places

Geofencing zone logic is fragmented across:
1. `service/vehiclerental/model/GeofencingZone.java` — the domain model
2. `service/vehiclerental/street/GeofencingZoneApplier.java` — applies zones to edges
3. `service/vehiclerental/street/GeofencingZoneExtension.java` — restriction on vertices
4. `service/vehiclerental/street/BusinessAreaBorder.java` — border restriction
5. `updater/vehicle_rental/GeofencingVertexUpdater.java` — thin wrapper for the updater
6. `updater/vehicle_rental/datasources/gbfs/GbfsGeofencingZoneMapper.java` — GBFS-to-model mapping (abstract + v2/v3)
7. `ext/gbfsgeofencing/` — duplicate sandbox for build-time loading

There is code duplication between the runtime and build-time paths, particularly in GBFS zone mapping.

#### 5.2.3 No `package.md`

The vehicle rental service has no `package.md` documenting its architecture. Every other well-structured domain has one.

#### 5.2.4 Repository Is Not Serializable

`DefaultVehicleRentalService` does not implement `Serializable`. This violates the `service/package.md` rule that the repository "should be serialized in the `graph.obj` file." The rental places are fully runtime data populated by updaters, so this may be intentional — but it's undocumented.

#### 5.2.5 GBFS Is Not a Separate Concern

The `updater/vehicle_rental/datasources/gbfs/` package is a large sub-tree (23 classes) that handles all GBFS protocol details: version detection, feed discovery, HTTP caching with ETags/TTL, and per-feed type mapping for v2 and v3. This is essentially a **GBFS client library** embedded inside the updater package.

The build-time sandbox (`ext/gbfsgeofencing/`) cannot reuse this client, leading to its own `GbfsClient` class that duplicates HTTP fetching and GBFS discovery logic (v3 only).

#### 5.2.6 Routing State Is Densely Coupled

The routing `StateData` has 7 rental-specific fields. `StreetEdge.traverse()` contains ~200 lines of rental-specific branching logic mixed with general traversal. `StateEditor` has 6 rental-specific methods. This coupling is intrinsic to OTP's routing model (the A* search must know about rental state), but the volume of rental-specific code in `StreetEdge` is notable.

---

## 6. Proposed Target Architecture

### 6.1 Design Principles

1. **Separate GBFS from vehicle rental**: GBFS is a data source protocol; vehicle rental is a domain. They should be independently evolvable.
2. **Extract street integration from the service domain**: Graph edges, vertices, and geofencing application logic are street model concerns.
3. **Consolidate geofencing into a coherent module**: One place for all geofencing logic, reusable by both runtime and build-time paths.
4. **Document the architecture**: Add `package.md` to each component.
5. **Minimize breakage**: Prefer moves and renames over rewrites.

### 6.2 Proposed Package Structure

```
o.o.service.vehiclerental/                    -- DOMAIN MODEL (pure)
    configure/
      VehicleRentalRepositoryModule
      VehicleRentalServiceModule
    internal/
      DefaultVehicleRentalService
    model/
      VehicleRentalPlace                      -- Interface
      VehicleRentalStation                    -- Docked station
      VehicleRentalVehicle                    -- Free-floating
      VehicleRentalSystem                     -- System info
      RentalVehicleType                       -- Type metadata (incl. PropulsionType)
      RentalVehicleFuel                       -- Fuel/range data
      GeofencingZone                          -- Zone model
      ReturnPolicy                            -- Enum
      RentalVehicleEntityCounts               -- Counts record
      RentalVehicleTypeCount                  -- Per-type count
      VehicleRentalStationUris                -- Deep links
    VehicleRentalService                      -- Read-only interface
    VehicleRentalRepository                   -- Write interface
    package.md                                -- NEW: architecture documentation

o.o.street.model/
    RentalFormFactor                          -- Stays here (street concern)
    RentalRestrictionExtension                -- Stays here (street concern)

o.o.street.model.edge/                        -- STREET INTEGRATION (moved from service)
    VehicleRentalEdge                         -- Pickup/drop-off state machine
    StreetVehicleRentalLink                   -- Street ↔ rental vertex link

o.o.street.model.vertex/
    VehicleRentalPlaceVertex                  -- Rental place in graph

o.o.service.vehiclerental.street/             -- GEOFENCING + RESTRICTION EXTENSIONS
    GeofencingZoneApplier                     -- Shared zone-to-edge application
    GeofencingZoneExtension                   -- Restriction impl: zone rules
    BusinessAreaBorder                        -- Restriction impl: area border
    CompositeRentalRestrictionExtension       -- Combines multiple
    NoRestriction                             -- Default no-op

o.o.updater.vehicle_rental/                   -- UPDATER (thinner)
    VehicleRentalUpdater                      -- PollingGraphUpdater
    VehicleRentalUpdaterParameters
    VehicleRentalSourceType
    datasources/
      VehicleRentalDataSource                 -- Interface
      VehicleRentalDataSourceFactory          -- Factory
      params/                                 -- Parameter types
    package.md                                -- NEW

o.o.updater.vehicle_rental.datasources.gbfs/  -- GBFS CLIENT (isolated)
    GbfsFeedLoaderAndMapper                   -- Version detection + wiring
    GbfsFeedLoader                            -- Interface
    GbfsFeedLoaderImpl                        -- Abstract: HTTP + TTL + ETag
    GbfsFeedMapper                            -- Interface
    GbfsGeofencingZoneMapper                  -- Abstract zone mapper
    GbfsVehicleRentalDataSource               -- Implements VehicleRentalDataSource
    support/
    v2/                                       -- Version-specific mappers
    v3/                                       -- Version-specific mappers

o.o.ext.gbfsgeofencing/                       -- SANDBOX (reuses shared components)
    ... (follows emission pattern, uses shared GeofencingZoneApplier + GBFS loader)
```

### 6.3 Dependency Direction

```
                    ┌────────────────┐
                    │  GraphQL APIs  │
                    └───────┬────────┘
                            │ reads from
                            v
                  ┌───────────────────┐
                  │  service/         │
                  │  vehiclerental/   │  ← Pure domain model
                  │  (model + svc)    │     No street dependencies
                  └───────┬───────────┘
                          │ used by
              ┌───────────┼───────────┐
              v           v           v
     ┌──────────────┐  ┌────────┐  ┌─────────────────┐
     │  updater/    │  │street/ │  │ ext/             │
     │  vehicle_    │  │model/  │  │ gbfsgeofencing/  │
     │  rental/     │  │edge/   │  │ (sandbox)        │
     └──────┬───────┘  └────────┘  └────────┬─────────┘
            │                               │
            └──────────┬────────────────────┘
                       v
              ┌────────────────┐
              │ GBFS client    │  ← Protocol/transport concern
              │ (in updater/   │     Could eventually be extracted
              │  datasources/) │     into its own module
              └────────────────┘
```

---

## 7. Refactoring Themes

The following themes organize the work into incremental, independently shippable changes.

### Theme 1: Documentation

**Goal**: Add `package.md` files to document current architecture and design intent.

- Add `service/vehiclerental/package.md`
- Add `updater/vehicle_rental/package.md`
- Add `updater/vehicle_rental/datasources/gbfs/package.md`
- Document the `street/` sub-package's role and why it exists

**Risk**: None. Documentation only.

### Theme 2: Consolidate Geofencing Zone Application

**Goal**: Eliminate duplication between runtime and build-time geofencing paths.

Current state:
- Runtime: `VehicleRentalUpdater` → `GeofencingVertexUpdater` → `GeofencingZoneApplier`
- Build-time: `GbfsGeofencingGraphBuilder` → `GeofencingZoneApplier` (already shared on this branch)
- But: build-time sandbox has its own `GeofencingZoneMapper` that duplicates the GBFS mapper logic

Target:
- Shared `GeofencingZoneApplier` (already done on this branch)
- Build-time sandbox reuses the updater's `GbfsGeofencingZoneMapper` for GBFS-to-model conversion
- The thin `GeofencingVertexUpdater` wrapper in the updater package can be eliminated in favor of direct `GeofencingZoneApplier` usage

### Theme 3: Extract Street Graph Edges/Vertices

**Goal**: Move graph integration classes to their canonical locations.

Candidates for relocation:
- `VehicleRentalEdge` → `street/model/edge/` (alongside other edge types)
- `VehicleRentalPlaceVertex` → `street/model/vertex/` (alongside other vertex types)
- `StreetVehicleRentalLink` → `street/model/edge/` (alongside other edge types)

**Consideration**: This is a significant move that touches many imports. The benefit is architectural consistency — all edge types live in `street/model/edge/`, all vertex types in `street/model/vertex/`. The cost is a large mechanical refactor with import churn.

**Alternative**: Keep these in `service/vehiclerental/street/` but document the deviation and the rationale (proximity to domain logic). This is the lower-risk option.

### Theme 4: Isolate GBFS Client

**Goal**: Make the GBFS protocol handling reusable outside the updater context.

The GBFS client code (version detection, feed discovery, HTTP caching, per-feed type mapping) is currently embedded in the updater package. The build-time sandbox had to create its own `GbfsClient` because it couldn't reuse the updater's client.

Options:
- **4a. Extract to a shared package** (e.g., `updater/vehicle_rental/datasources/gbfs/` stays but is made public and documented as a reusable GBFS client)
- **4b. Create a `gbfs/` top-level package** under `org.opentripplanner.gbfs/` for protocol-level GBFS handling

### Theme 5: Address PropulsionType Data Flow

**Goal**: Complete the propulsion-aware routing work.

From the `propulsion-aware-rental-vehicle-routing` plan:
- `PropulsionType` needs to flow through `StateData` → `State` → `StreetEdge` cost calculation
- `VehicleRentalEdge` needs to extract `PropulsionType` from the rental place and pass it to `StateEditor`
- Cost calculation in `StreetEdge.bicycleOrScooterTraversalCost()` needs propulsion-aware effective distances

This is largely implemented on the related branch but needs integration.

### Theme 6: Support GBFS Speed Restrictions

**Goal**: Map `maximum_speed_kph` from GBFS geofencing zones into the routing model.

From the speed-restrictions research:
- GBFS `GBFSRule` has a `maximumSpeedKph` field (both v2 and v3) that is currently unmapped
- `GeofencingZone` domain model has no speed field
- `StreetEdge.calculateSpeed()` does not consult geofencing zones
- The boundary-only geofencing architecture (from the speed-restrictions branch) provides the state tracking infrastructure needed

This requires changes across all layers: GBFS mapping → domain model → routing state → speed calculation.

### Theme 7: Improve Thread-Safety Pattern

**Goal**: Align with the canonical thread-safety patterns used by other services.

Current: `ConcurrentHashMap` for all rental places.
Canonical: `volatile` + immutable collection replacement (as in `vehicleparking`, `realtimevehicles`).

This is a low-priority improvement since `ConcurrentHashMap` is functionally correct.

---

## 8. Prioritization and Dependencies

```
Theme 1 (Documentation)     ← Start here, no dependencies
    │
Theme 2 (Consolidate Geofencing) ← Already partially done on this branch
    │
Theme 4 (Isolate GBFS Client) ← Enables theme 6 and reduces sandbox duplication
    │
    ├── Theme 5 (PropulsionType) ← Independent, can parallel with 4
    │
    └── Theme 6 (Speed Restrictions) ← Depends on 4 (GBFS mapper) + geofencing state infrastructure

Theme 3 (Extract Edges/Vertices) ← Large mechanical change, do when ready
Theme 7 (Thread Safety) ← Low priority, do opportunistically
```

---

## 9. Open Questions for Discussion

1. **Should `VehicleRentalEdge` and `VehicleRentalPlaceVertex` move to `street/model/`?** The canonical pattern places all edges in `street/model/edge/` and all vertices in `street/model/vertex/`. But the transit domain keeps its edges close too (`transit/model/`). What is the preferred convention?

2. **Should GBFS become a top-level domain?** GBFS is a protocol that could serve multiple features (rental, geofencing, parking in the future). A dedicated `org.opentripplanner.gbfs/` package would isolate protocol handling from business logic.

3. **Should geofencing be a separate service?** Currently, geofencing is part of vehicle rental. But geofencing could apply to other mobility modes. Should there be a `service/geofencing/` with its own model, separate from vehicle rental?

4. **How should build-time and runtime geofencing coexist?** The current sandbox is a separate feature flag. Should the build-time path be folded into the core geofencing infrastructure, with configuration determining when zones are loaded?

5. **PropulsionType location**: `PropulsionType` is currently a nested enum inside `RentalVehicleType`. Since it affects routing cost calculation (a `street` concern), should it be elevated to a top-level type in `street/model/` (like `RentalFormFactor`)?

6. **Boundary-only vs. edge-marking geofencing**: The `speed-restrictions` branch introduced a boundary-only approach with state tracking. Should this replace the current edge-marking approach, or should both coexist?

---

## 10. Related Work and References

### Branch Work
- `poc/sandbox-geofencing-zones-in-graph-builder` — build-time geofencing sandbox (current branch)
- `feature/propulsion-type-aware-bike-and-scooter-routing` — PropulsionType through routing state
- `speed-restrictions` — boundary-only geofencing with state tracking, priority system

### Research Documents
- `thoughts/shared/research/2025-12-01-gbfs-geofencing-zones-graph-build-sandbox.md` — Initial geofencing sandbox research
- `thoughts/shared/research/2026-02-05-gbfs-geofencing-speed-restrictions.md` — Speed restrictions gap analysis
- `thoughts/shared/plans/2025-11-27-propulsion-aware-rental-vehicle-routing.md` — PropulsionType implementation plan
- `thoughts/shared/plans/2025-12-02-gbfs-geofencing-zones-graph-build-sandbox.md` — Geofencing sandbox implementation plan

### Project Documentation
- `ARCHITECTURE.md` — OTP layered architecture overview
- `application/src/main/java/org/opentripplanner/service/package.md` — Canonical service package layout
- `doc/dev/decisionrecords/NamingConventions.md` — Package and naming conventions
- `doc/dev/decisionrecords/AnalysesAndDesign.md` — Design documentation expectations

### Key Source Files
- `service/vehiclerental/VehicleRentalService.java` — Read-only service interface
- `service/vehiclerental/VehicleRentalRepository.java` — Write interface
- `service/vehiclerental/internal/DefaultVehicleRentalService.java` — Combined implementation
- `updater/vehicle_rental/VehicleRentalUpdater.java` — Runtime polling updater
- `service/vehiclerental/street/VehicleRentalEdge.java` — Pickup/drop-off state machine
- `service/vehiclerental/street/GeofencingZoneApplier.java` — Zone-to-edge application
- `street/model/RentalRestrictionExtension.java` — Restriction interface
- `street/search/state/StateData.java` — Routing state (7 rental fields)
- `street/model/edge/StreetEdge.java` — ~200 lines of rental traversal logic
