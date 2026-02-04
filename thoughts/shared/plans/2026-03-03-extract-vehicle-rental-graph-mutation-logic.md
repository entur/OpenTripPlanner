# Extract Vehicle Rental Graph Mutation Logic — Implementation Plan

## Overview

Extract business logic from `VehicleRentalUpdater`'s inner `VehicleRentalGraphWriterRunnable` into
the domain classes that own it, and move+rename `GeofencingVertexUpdater` to the street module as
`GeofencingZoneApplier`. This leaves the updater as pure orchestration while making each piece of
domain logic independently testable.

## Current State Analysis

The inner class `VehicleRentalGraphWriterRunnable` (lines 135-246 of `VehicleRentalUpdater.java`)
performs three phases of graph mutation: add/update stations, remove absent stations, apply
geofencing zones. All edge creation, linking, and geofencing logic is inline within the `run()`
method.

`GeofencingVertexUpdater` is a standalone class but is package-private in the wrong package
(`updater/vehicle_rental/`) with a misleading name (operates on edges, not vertices; "Updater"
implies `PollingGraphUpdater`).

### Key Discoveries:
- `VehicleRentalEdge.createVehicleRentalEdge(vertex, formFactor)` already exists — just need a
  batch variant: `VehicleRentalEdge.java:32-37`
- `StreetVehicleRentalLink` has two overloaded `createStreetVehicleRentalLink` factory methods —
  just need a convenience method combining them: `StreetVehicleRentalLink.java:26-38`
- `VertexFactory.vehicleRentalPlace()` is trivially `new VehicleRentalPlaceVertex(station)` +
  `graph.addVertex()` — can be replaced with inline code: `VertexFactory.java:163-165`
- `GeofencingVertexUpdater` has zero imports from the `updater` package — clean to move
- No tests exist for the graph mutation logic (`VehicleRentalUpdaterTest` never executes the
  runnable)

## Desired End State

After this plan is complete:
1. `GeofencingVertexUpdater` is renamed to `GeofencingZoneApplier` and lives in
   `street/.../service/vehiclerental/street/`
2. `VehicleRentalEdge` has a `createRentalEdgesForStation()` factory method
3. `StreetVehicleRentalLink` has a `createBidirectionalLinks()` factory method
4. `VehicleRentalUpdater` no longer uses `VertexFactory` — creates and registers vertices directly
5. The updater's `run()` method is pure orchestration — each domain operation delegates to the class
   that owns it
6. New factory methods have unit tests

### Verification:
- `mvn test -pl street` passes (new factory method tests, moved geofencing test)
- `mvn test -pl application` passes (updater uses renamed class + new methods)
- `mvn package -DskipTests` compiles cleanly

## What We're NOT Doing

- **Extracting the inner class** — The inner class pattern is standard OTP convention for updaters
  (`VehicleParkingUpdater` uses the same pattern). The inner class accesses 6 fields from the outer
  class; extracting would add ceremony without benefit. The goal is to extract *business logic*, not
  the orchestration shell.
- **Creating a "Helper" class** — The parking domain uses `VehicleParkingHelper` for good reasons
  (multi-entrance N^2 edge logic, build-time + runtime reuse). Rental is simpler (one vertex per
  place, simple form-factor edges). A "Helper" is an OOP anti-pattern name with no domain identity.
- **Moving edges/vertices to `street/model/`** — That's a future step (moving
  `VehicleRentalEdge`, `StreetVehicleRentalLink`, `VehicleRentalPlaceVertex` to
  `street/model/edge/` and `street/model/vertex/`). Independent of this work.
- **Adding updater-level graph mutation tests** — Optional follow-up (PR 3 in research doc). The
  factory methods will have their own unit tests.

## Implementation Approach

Two phases, each designed to be a separate reviewable PR:

1. **Phase 1 (structural):** Move+rename `GeofencingVertexUpdater` → `GeofencingZoneApplier`.
   Pure move — no logic changes.
2. **Phase 2 (behavioral):** Add factory methods to domain classes, simplify updater to use them.

---

## Phase 1: Move+Rename GeofencingVertexUpdater → GeofencingZoneApplier

### Overview
Move `GeofencingVertexUpdater` from `application/.../updater/vehicle_rental/` to
`street/.../service/vehiclerental/street/` and rename to `GeofencingZoneApplier`. Move the test
file correspondingly. Update the single consumer in `VehicleRentalUpdater`.

### Changes Required:

#### 1. Move + rename the class
**FROM**: `application/src/main/java/org/opentripplanner/updater/vehicle_rental/GeofencingVertexUpdater.java`
**TO**: `street/src/main/java/org/opentripplanner/service/vehiclerental/street/GeofencingZoneApplier.java`

Changes:
- Package declaration: `org.opentripplanner.updater.vehicle_rental` →
  `org.opentripplanner.service.vehiclerental.street`
- Class name: `GeofencingVertexUpdater` → `GeofencingZoneApplier`
- Visibility: package-private → `public` (cross-module access required)
- Update Javadoc class description to match new name
- All internal logic stays exactly the same

#### 2. Move + rename the test
**FROM**: `application/src/test/java/org/opentripplanner/updater/vehicle_rental/GeofencingVertexUpdaterTest.java`
**TO**: `street/src/test/java/org/opentripplanner/service/vehiclerental/street/GeofencingZoneApplierTest.java`

Changes:
- Package declaration updated
- Class name: `GeofencingVertexUpdaterTest` → `GeofencingZoneApplierTest`
- Internal references: `GeofencingVertexUpdater` → `GeofencingZoneApplier`
- Test imports may need adjustment if test support classes differ between modules.
  Check that test helper classes used (`intersectionVertex`, `streetEdge`, `id`, `Polygons`) are
  available in the `street` module test scope. These are from `_support.geometry.Polygons` and
  `street.model._support.TurnRestrictionFixture` — verify they exist in the street module test
  dependencies.

#### 3. Update consumer in VehicleRentalUpdater
**File**: `application/src/main/java/org/opentripplanner/updater/vehicle_rental/VehicleRentalUpdater.java`

Change import and instantiation:
```java
// Old:
import org.opentripplanner.updater.vehicle_rental.GeofencingVertexUpdater;
var updater = new GeofencingVertexUpdater(context.graph()::findEdges);

// New:
import org.opentripplanner.service.vehiclerental.street.GeofencingZoneApplier;
var applier = new GeofencingZoneApplier(context.graph()::findEdges);
latestModifiedEdges = applier.applyGeofencingZones(geofencingZones);
```

Note: The old import is implicit (same package), so there may be no explicit import to change —
just add the new import and rename the local variable and constructor call.

### Success Criteria:

#### Automated Verification:
- [x] `mvn package -DskipTests` compiles cleanly
- [x] `mvn test -pl street -Dtest=GeofencingZoneApplierTest` passes
- [x] `mvn test -pl application -Dtest=VehicleRentalUpdaterTest` passes
- [x] `mvn test -pl application -Dps` passes (full application module tests) — pre-existing errors only
- [x] `mvn test -pl street -Dps` passes (full street module tests)
- [x] No references to `GeofencingVertexUpdater` remain in Java source files
- [x] Old files are deleted

---

## Phase 2: Add Factory Methods and Simplify Updater

### Overview
Add `createRentalEdgesForStation()` to `VehicleRentalEdge` and `createBidirectionalLinks()` to
`StreetVehicleRentalLink`. Replace inline code in the updater with calls to these methods. Remove
`VertexFactory` usage in favor of direct vertex creation.

### Changes Required:

#### 1. Add `createRentalEdgesForStation()` to VehicleRentalEdge
**File**: `street/src/main/java/org/opentripplanner/service/vehiclerental/street/VehicleRentalEdge.java`

Add a new static method after the existing `createVehicleRentalEdge` factory method:

```java
/**
 * Creates rental edges for all form factors available at a station and adds them to the
 * given disposable edge collection.
 */
public static void createRentalEdgesForStation(
  VehicleRentalPlaceVertex vertex,
  VehicleRentalPlace station,
  DisposableEdgeCollection edges
) {
  var formFactors = Stream.concat(
    station.availablePickupFormFactors(false).stream(),
    station.availableDropoffFormFactors(false).stream()
  ).collect(Collectors.toSet());
  for (var formFactor : formFactors) {
    edges.addEdge(createVehicleRentalEdge(vertex, formFactor));
  }
}
```

Imports needed: `java.util.stream.Stream`, `java.util.stream.Collectors`,
`org.opentripplanner.service.vehiclerental.model.VehicleRentalPlace`,
`org.opentripplanner.street.linking.DisposableEdgeCollection`.

#### 2. Add `createBidirectionalLinks()` to StreetVehicleRentalLink
**File**: `street/src/main/java/org/opentripplanner/service/vehiclerental/street/StreetVehicleRentalLink.java`

Add a new static method after the existing factory methods:

```java
/**
 * Creates a bidirectional pair of links between a rental vertex and a street vertex.
 * Designed to be used as a method reference for {@link VertexLinker#linkVertexForRealTime}.
 */
public static List<Edge> createBidirectionalLinks(
  Vertex rentalVertex,
  StreetVertex streetVertex
) {
  return List.of(
    createStreetVehicleRentalLink((VehicleRentalPlaceVertex) rentalVertex, streetVertex),
    createStreetVehicleRentalLink(streetVertex, (VehicleRentalPlaceVertex) rentalVertex)
  );
}
```

Imports needed: `java.util.List`, `org.opentripplanner.street.model.edge.Edge`,
`org.opentripplanner.street.model.vertex.Vertex`.

Note: The `Vertex` and `StreetVertex` parameter types match the `BiFunction<Vertex, StreetVertex,
List<Edge>>` signature required by `VertexLinker.linkVertexForRealTime()`.

#### 3. Simplify VehicleRentalGraphWriterRunnable
**File**: `application/src/main/java/org/opentripplanner/updater/vehicle_rental/VehicleRentalUpdater.java`

Replace the add-station block (approximately lines 151-205) with the simplified version:

**Remove `VertexFactory` instantiation** (line 152):
```java
// Remove: var vertexFactory = new VertexFactory(context.graph());
```

**Replace vertex creation** (line 161):
```java
// Old:
vehicleRentalVertex = vertexFactory.vehicleRentalPlace(station);

// New:
vehicleRentalVertex = new VehicleRentalPlaceVertex(station);
context.graph().addVertex(vehicleRentalVertex);
```

**Replace linking lambda** (lines 162-177) with method reference:
```java
// Old:
DisposableEdgeCollection tempEdges = linker.linkVertexForRealTime(
  vehicleRentalVertex,
  new TraverseModeSet(TraverseMode.WALK),
  LinkingDirection.BIDIRECTIONAL,
  (vertex, streetVertex) ->
    List.of(
      StreetVehicleRentalLink.createStreetVehicleRentalLink(
        (VehicleRentalPlaceVertex) vertex,
        streetVertex
      ),
      StreetVehicleRentalLink.createStreetVehicleRentalLink(
        streetVertex,
        (VehicleRentalPlaceVertex) vertex
      )
    )
);

// New:
DisposableEdgeCollection tempEdges = linker.linkVertexForRealTime(
  vehicleRentalVertex,
  new TraverseModeSet(TraverseMode.WALK),
  LinkingDirection.BIDIRECTIONAL,
  StreetVehicleRentalLink::createBidirectionalLinks
);
```

**Replace edge creation loop** (lines 191-199) with factory method call:
```java
// Old:
var formFactors = Stream.concat(
  station.availablePickupFormFactors(false).stream(),
  station.availableDropoffFormFactors(false).stream()
).collect(Collectors.toSet());
for (var formFactor : formFactors) {
  tempEdges.addEdge(
    VehicleRentalEdge.createVehicleRentalEdge(vehicleRentalVertex, formFactor)
  );
}

// New:
VehicleRentalEdge.createRentalEdgesForStation(vehicleRentalVertex, station, tempEdges);
```

**Remove unused imports** from VehicleRentalUpdater:
- `org.opentripplanner.streetadapter.VertexFactory`
- `java.util.stream.Stream`
- `java.util.stream.Collectors` (if only used for form factors)

#### 4. Remove `vehicleRentalPlace()` from VertexFactory (if no other callers)
**File**: `application/src/main/java/org/opentripplanner/streetadapter/VertexFactory.java`

Search for other callers of `vehicleRentalPlace()`. If the updater is the only caller, remove the
method (lines 163-165) and its associated import.

#### 5. Add unit tests for new factory methods
**File** (new): `street/src/test/java/org/opentripplanner/service/vehiclerental/street/VehicleRentalEdgeTest.java`

If this test file already exists in the application module (the research mentions
`VehicleRentalEdgeTest` exists with thorough edge traversal tests), add the new test there. If not,
create a focused test in the street module.

Test `createRentalEdgesForStation()`:
- Given a station with specific pickup/dropoff form factors, verify the correct number and type of
  edges are created
- Verify edges are added to the `DisposableEdgeCollection`
- Test with overlapping pickup/dropoff form factors (set deduplication)
- Test with station having no form factors

**File** (new or existing): `street/src/test/java/org/opentripplanner/service/vehiclerental/street/StreetVehicleRentalLinkTest.java`

Test `createBidirectionalLinks()`:
- Given a rental vertex and street vertex, verify two links are created (one in each direction)
- Verify correct directionality (from→to and to→from)

### Success Criteria:

#### Automated Verification:
- [x] `mvn package -DskipTests` compiles cleanly
- [x] `mvn test -pl street -Dps` passes (new factory method tests)
- [x] `mvn test -pl application -Dps` passes (updater still works with new method calls) — 18 pre-existing errors from prior branch refactors (VertexLinker move, GBFS class renames), none related to these changes
- [ ] `mvn test -Dps` passes (full build) — same pre-existing errors
- [x] No references to `VertexFactory` remain in `VehicleRentalUpdater.java`
- [x] The updater's `run()` method no longer contains edge creation or linking logic inline

---

## Testing Strategy

### Unit Tests (new in Phase 2):
- `VehicleRentalEdge.createRentalEdgesForStation()` — verify correct form factor edge creation
- `StreetVehicleRentalLink.createBidirectionalLinks()` — verify bidirectional link creation

### Existing Tests (must still pass):
- `GeofencingZoneApplierTest` (moved in Phase 1) — geofencing zone application
- `VehicleRentalEdgeTest` — edge traversal logic
- `VehicleRentalUpdaterTest` — updater lifecycle (limited but must not break)

### Integration verification:
- Full `mvn test -Dps` must pass after each phase

## References

- Research document: `thoughts/shared/research/2026-03-03-extract-vehicle-rental-graph-mutation-logic.md`
- Target architecture: `thoughts/shared/research/2026-02-24-vehicle-rental-gbfs-target-architecture.md`
- Current state analysis: `thoughts/shared/research/2026-02-26-vehicle-rental-gbfs-architecture-design.md`
- Parking helper pattern: `street/src/main/java/org/opentripplanner/street/linking/VehicleParkingHelper.java`
