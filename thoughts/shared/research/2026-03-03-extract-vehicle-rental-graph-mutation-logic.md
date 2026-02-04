---
date: 2026-03-03T20:34:00+01:00
researcher: Claude
git_commit: acb39e314d
branch: refactor/extract-vehicle-rental-graph-logic
repository: OpenTripPlanner
topic: "Extract Vehicle Rental Graph Mutation Logic from Updater"
tags: [architecture, design, vehicle-rental, graph-mutation, refactoring, street-module, geofencing]
status: complete
last_updated: 2026-03-03
last_updated_by: Claude
last_updated_note: "Solidified plan after holistic review of broader vehicle rental architecture"
related: 2026-02-26-vehicle-rental-gbfs-architecture-design.md
---

# Research: Extract Vehicle Rental Graph Mutation Logic from Updater

**Date**: 2026-03-03T20:34:00+01:00
**Researcher**: Claude
**Git Commit**: acb39e314d
**Branch**: refactor/extract-vehicle-rental-graph-logic
**Repository**: OpenTripPlanner

## Research Question

How should we extract the graph mutation logic from `VehicleRentalUpdater`'s inner
`VehicleRentalGraphWriterRunnable` — and also move and rename `GeofencingVertexUpdater` — following
OOP best practices and OTP's design principles, while proceeding in small, reviewable steps?

## Context: Where This Fits in the Broader Refactoring

This work is part of a progressive cleanup of the vehicle rental domain. Prior steps already
completed on this branch:

| Step | Status | What |
|---|---|---|
| GBFS extraction | **Done** | Moved GBFS protocol code to top-level `o.o.gbfs/` package |
| Linker move | **Done** | Moved `VertexLinker`, `DisposableEdgeCollection`, etc. to `street` module |
| Graph mutation extraction | **This task** | Extract business logic from updater, move geofencing to street module |
| Edge/vertex move | Future | Move `VehicleRentalEdge`/`StreetVehicleRentalLink`/`VehicleRentalPlaceVertex` to `street/model/edge/` and `street/model/vertex/` (align with parking precedent) |

The [target architecture](2026-02-24-vehicle-rental-gbfs-target-architecture.md) and [current state
analysis](2026-02-26-vehicle-rental-gbfs-architecture-design.md) provide the full picture.

---

## 1. Current State: What Lives Where

### 1.1 The Graph Writer Runnable (lines 135-246 of VehicleRentalUpdater.java)

The inner class `VehicleRentalGraphWriterRunnable` performs three phases:

**Phase A — Add/update stations** (lines 155-205):
1. Upserts each station into `VehicleRentalRepository`
2. For new stations: creates `VehicleRentalPlaceVertex` via `VertexFactory`, links to streets via
   `VertexLinker.linkVertexForRealTime()`, creates `VehicleRentalEdge` self-loops per form factor
3. For existing stations: calls `setStation()` on the vertex (data-only update)

**Phase B — Remove absent stations** (lines 207-222):
1. Identifies stations in `verticesByStation` not in the current update
2. Removes from repository
3. Calls `DisposableEdgeCollection.disposeEdges()` to remove all edges and orphaned vertices

**Phase C — Apply geofencing zones** (lines 224-244):
1. Change-detection via set equality on `latestAppliedGeofencingZones`
2. Strips previous extensions from street edges
3. Creates `GeofencingVertexUpdater` and re-applies all zones from scratch

### 1.2 State Held by the Outer Class

| Field | Type | Purpose |
|---|---|---|
| `verticesByStation` | `Map<FeedScopedId, VehicleRentalPlaceVertex>` | Tracks vertices by station ID across cycles |
| `tempEdgesByStation` | `Map<FeedScopedId, DisposableEdgeCollection>` | Tracks disposable edges for cleanup |
| `latestModifiedEdges` | `Map<StreetEdge, RentalRestrictionExtension>` | Edges with geofencing extensions from last cycle |
| `latestAppliedGeofencingZones` | `Set<GeofencingZone>` | Last applied zones (for change detection) |

### 1.3 GeofencingVertexUpdater — Misplaced and Misnamed

`GeofencingVertexUpdater` (in `application/.../updater/vehicle_rental/`) is already a standalone
class, but has two problems:

**Wrong location.** All its dependencies are in the `street` module:
- `GeofencingZone`, `GeofencingZoneExtension`, `BusinessAreaBorder` — `service.vehiclerental`
- `StreetEdge`, `RentalRestrictionExtension` — `street.model`
- `GeometryUtils` — `street.geometry`

It has zero imports from the updater package. It's package-private in the updater package purely
because that's where it was written.

**Misleading name.** "Updater" in OTP means a `PollingGraphUpdater` — this class isn't one. And
it operates on **edges**, not vertices (the class Javadoc acknowledges: "this updater operates
mostly on edges"). Its only side effect is calling `streetEdge.addRentalRestriction(ext)`.

The `speed-restrictions` branch independently moved this class to `service/vehiclerental/street/`,
validating our direction. The sandbox used the name `GeofencingZoneApplier`, which more accurately
describes what the class does: it applies geofencing zones to street edges.

### 1.4 Test Coverage Gaps

| What | Tested? | Notes |
|---|---|---|
| Edge traversal (`VehicleRentalEdge.traverse()`) | Yes | `VehicleRentalEdgeTest` — thorough |
| Geofencing zone application | Partially | `GeofencingVertexUpdaterTest` — stubs spatial index |
| Adding stations to graph (vertex+edges) | No | `VehicleRentalUpdaterTest` discards the runnable |
| Updating station data | No | |
| Removing stations (cleanup) | No | |
| Geofencing zone removal/re-application | No | |

---

## 2. Design Principles

From `doc/dev/`:

1. **Package by domain, not technology** (`NamingConventions.md`): Code belongs near its domain,
   not grouped by architectural layer.

2. **Canonical service pattern** (`service/package.md`): Service packages contain model, service,
   repository. Street integration classes (edges, vertices) follow the parking precedent in
   `street.model.edge` / `street.model.vertex`.

3. **OOP best practices**: Classes should have clear single responsibilities. Avoid procedural
   "Helper/Util" classes that lack domain identity — prefer putting behavior on the classes that
   own the data.

4. **Scout rule**: Leave code better than you found it, but scope changes pragmatically.

5. **Test at the lowest practical level**: Extract logic to enable unit tests without needing the
   full updater lifecycle.

6. **Business logic should be testable in isolation and free from updater framework friction**,
   so that future bug fixes and features are easy to implement.

---

## 3. Why Not Just Copy the VehicleParkingHelper Pattern

`VehicleParkingHelper` exists for good reasons specific to parking:
- Parking has **multi-entrance** facilities requiring pairwise edge creation (N vertices, N^2
  edges) — complex enough to justify a helper
- Parking is used at **both build-time and runtime** — the helper is shared across contexts
- The multi-entrance linking logic (`isUsableForParking`) has business rules worth isolating

Vehicle rental is different:
- One vertex per place, simple form-factor loop edges — much simpler graph topology
- Runtime only — no build-time path
- The "Helper" name is an OOP anti-pattern: a class named "Helper" has no clear domain identity
  and tends to become a procedural grab-bag

The parking helper is a reference point, not a blueprint. We should design for rental's actual
needs.

---

## 4. Design: Enrich Existing Classes with Factory Methods

Put creation logic **on the classes that own it**. This follows OOP principles (behavior on the
owning class), doesn't over-engineer, and keeps each piece independently testable.

**`VehicleRentalEdge`** — already has `createVehicleRentalEdge(vertex, formFactor)`. Add a
method to create edges for all form factors at a station:

```java
// In VehicleRentalEdge.java
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

**`StreetVehicleRentalLink`** — already has two `createStreetVehicleRentalLink` overloads. Add a
method to create the standard bidirectional pair:

```java
// In StreetVehicleRentalLink.java
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

### Why Not Extract the Inner Class?

The `VehicleRentalGraphWriterRunnable` inner class follows the established updater convention —
`VehicleParkingUpdater` and `VehicleParkingAvailabilityUpdater` use the same pattern. The inner
class exists because it needs access to the outer class's lifecycle state (`verticesByStation`,
`tempEdgesByStation`, etc.) that persists across polling cycles.

Extracting it would mean explicitly passing 8 fields, which adds ceremony without clear benefit.
The important thing is that **business logic** (edge creation, link creation, geofencing zone
application) is testable in isolation. The inner class is left as pure orchestration — calling
building blocks in sequence and managing state maps. That's updater plumbing, not business logic.

---

## 5. GeofencingVertexUpdater → GeofencingZoneApplier

### Rename Rationale

| Current name | Problem |
|---|---|
| "Geofencing**Vertex**Updater" | Operates on edges, not vertices |
| "Geofencing**Vertex**Updater" | "Updater" implies `PollingGraphUpdater` in OTP |

The class does exactly one thing: applies geofencing zones to street edges by calling
`streetEdge.addRentalRestriction(ext)`. The name `GeofencingZoneApplier` matches the method name
`applyGeofencingZones()` and was already used in the sandbox.

### Move to `service/vehiclerental/street/`

This is where `GeofencingZoneExtension` and `BusinessAreaBorder` (which it creates) already live.
The class applies rental-domain restrictions to street edges — it belongs with the street-level
rental infrastructure.

Move + rename in one commit:
```
FROM: application/src/main/java/.../updater/vehicle_rental/GeofencingVertexUpdater.java
  TO: street/src/main/java/.../service/vehiclerental/street/GeofencingZoneApplier.java

FROM: application/src/test/java/.../updater/vehicle_rental/GeofencingVertexUpdaterTest.java
  TO: street/src/test/java/.../service/vehiclerental/street/GeofencingZoneApplierTest.java
```

Change visibility from package-private to `public` (cross-module access).

### Reusability

Once in the street module, this class becomes available to both:
- **Runtime updater** (`VehicleRentalUpdater`) — current consumer
- **Build-time sandbox** (`GbfsGeofencingGraphBuilder`) — when the sandbox lands, it can use this
  shared class instead of creating its own copy (`GeofencingZoneApplier` in the sandbox was
  acknowledged as "essentially a copy" of `GeofencingVertexUpdater`)

### Noted for Later

The `addExtensionToIntersectingStreetEdges` method takes a
`Function<GeofencingZone, RentalRestrictionExtension>` parameter that is only ever called with
`GeofencingZoneExtension::new`. This unnecessary indirection can be simplified in a follow-up.

---

## 6. Future Feature Compatibility

A key goal is that future features (speed restrictions, per-vehicle-type rules) can be implemented
without touching updater plumbing.

### Speed Restrictions Example

Adding GBFS `maximum_speed_kph` support (see
[speed restrictions research](2026-02-05-gbfs-geofencing-speed-restrictions.md)) would touch:

| Layer | Package | Change |
|---|---|---|
| GBFS parsing | `gbfs/` | Map `rule.getMaximumSpeedKph()` |
| Domain model | `service/vehiclerental/model/` | Add field to `GeofencingZone` record |
| Restriction extension | `service/vehiclerental/street/` | Carry speed data on extension |
| Zone applier | `service/vehiclerental/street/` | Unchanged — still applies zones to edges |
| Routing state | `street/search/state/` | Derive speed restriction from zone set |
| Edge traversal | `street/model/edge/` | Consult zones in `calculateSpeed()` |

**None of these touch the updater.** The updater just polls, passes data through, and
orchestrates. All business logic lives in the street module.

### Edge Creation Changes

If future features require different edges per vehicle type (e.g., form-factor-specific speed
limits), `VehicleRentalEdge.createRentalEdgesForStation()` is the natural place to add that logic
— on the domain class, independently testable, without touching updater plumbing.

---

## 7. Plan

### PR 1: Move+rename `GeofencingVertexUpdater` → `GeofencingZoneApplier`

Move to `service/vehiclerental/street/` in the street module, rename, make `public`, move test.
Update import in `VehicleRentalUpdater.java`.

**Pure structural change — no logic changes.**

### PR 2: Add factory methods and simplify updater

1. **`VehicleRentalEdge`** — add `createRentalEdgesForStation(vertex, station, tempEdges)`
2. **`StreetVehicleRentalLink`** — add `createBidirectionalLinks(rentalVertex, streetVertex)`
3. **Replace `VertexFactory`** with inline `new VehicleRentalPlaceVertex(station)` +
   `context.graph().addVertex(vehicleRentalVertex)`
4. **Simplify updater** — replace inline code with factory method calls, lambda becomes method
   reference `StreetVehicleRentalLink::createBidirectionalLinks`
5. **Add tests** for the new static methods

### PR 3 (optional): Add updater-level graph mutation tests

Update `VehicleRentalUpdaterTest` to actually execute the `GraphWriterRunnable`. Test
add/update/remove cycles.

### Future: Move edges/vertices to `street/model/`

Move `VehicleRentalEdge`, `StreetVehicleRentalLink`, `VehicleRentalPlaceVertex` from
`service/vehiclerental/street/` to `street/model/edge/` and `street/model/vertex/` — aligning
with the parking precedent. Independent of this work.

---

## 8. What Stays in the Updater

The updater continues to own:
- **Lifecycle state**: `verticesByStation`, `tempEdgesByStation` maps
- **Geofencing state**: `latestModifiedEdges`, `latestAppliedGeofencingZones`
- **Orchestration**: the sequence of add → update → remove → geofencing
- **Repository calls**: `service.addVehicleRentalStation()` / `removeVehicleRentalStation()`
- **VertexLinker call**: `linker.linkVertexForRealTime(...)` — this is updater infrastructure

These are correctly updater concerns — they manage the lifecycle of stations across polling
cycles and coordinate between the data source, repository, and graph.

---

## 9. Result: Simplified VehicleRentalGraphWriterRunnable

After PRs 1-2, the inner class would look like:

```java
@Override
public void run(RealTimeUpdateContext context) {
  Set<FeedScopedId> stationSet = new HashSet<>();

  for (VehicleRentalPlace station : stations) {
    service.addVehicleRentalStation(station);
    stationSet.add(station.id());
    VehicleRentalPlaceVertex vehicleRentalVertex = verticesByStation.get(station.id());

    if (vehicleRentalVertex == null) {
      vehicleRentalVertex = new VehicleRentalPlaceVertex(station);
      context.graph().addVertex(vehicleRentalVertex);

      DisposableEdgeCollection tempEdges = linker.linkVertexForRealTime(
        vehicleRentalVertex,
        new TraverseModeSet(TraverseMode.WALK),
        LinkingDirection.BIDIRECTIONAL,
        StreetVehicleRentalLink::createBidirectionalLinks  // method reference
      );

      if (vehicleRentalVertex.getOutgoing().isEmpty()) {
        // ... throttled warning (unchanged) ...
      }

      VehicleRentalEdge.createRentalEdgesForStation(vehicleRentalVertex, station, tempEdges);

      verticesByStation.put(station.id(), vehicleRentalVertex);
      tempEdgesByStation.put(station.id(), tempEdges);
    } else {
      vehicleRentalVertex.setStation(station);
    }
  }

  // removal logic unchanged

  if (!geofencingZones.isEmpty() && !geofencingZones.equals(latestAppliedGeofencingZones)) {
    latestModifiedEdges.forEach(StreetEdge::removeRentalExtension);
    var applier = new GeofencingZoneApplier(context.graph()::findEdges);  // renamed
    latestModifiedEdges = applier.applyGeofencingZones(geofencingZones);
    latestAppliedGeofencingZones = geofencingZones;
  }
}
```

The method is now a clean coordinator:
- **Vertex creation**: `new VehicleRentalPlaceVertex(station)` — plain constructor
- **Graph addition**: `context.graph().addVertex(...)` — caller's responsibility
- **Street linking**: `linker.linkVertexForRealTime(...)` — connects to street network
- **Domain edges**: `VehicleRentalEdge.createRentalEdgesForStation(...)` — behavior on the class
  that owns it
- **Link edges**: `StreetVehicleRentalLink::createBidirectionalLinks` — method reference
- **Geofencing**: `GeofencingZoneApplier.applyGeofencingZones(...)` — renamed, in street module

No "Helper" class. Each domain class owns its creation logic. The updater only orchestrates.

---

## 10. Key Files

| File | Role |
|---|---|
| `application/.../updater/vehicle_rental/VehicleRentalUpdater.java` | Updater to simplify |
| `application/.../updater/vehicle_rental/GeofencingVertexUpdater.java` | Class to move+rename → `GeofencingZoneApplier` |
| `street/.../service/vehiclerental/street/VehicleRentalEdge.java` | Add `createRentalEdgesForStation()` |
| `street/.../service/vehiclerental/street/StreetVehicleRentalLink.java` | Add `createBidirectionalLinks()` |
| `street/.../service/vehiclerental/street/VehicleRentalPlaceVertex.java` | Vertex class |
| `application/.../streetadapter/VertexFactory.java` | Remove `vehicleRentalPlace()` usage |
| `application/.../updater/vehicle_rental/VehicleRentalUpdaterTest.java` | Test to enhance |
| `application/.../updater/vehicle_rental/GeofencingVertexUpdaterTest.java` | Test to move+rename |

---

## 11. Related Research

- [Vehicle Rental GBFS Architecture Design (Feb 26)](2026-02-26-vehicle-rental-gbfs-architecture-design.md) — Current state analysis after street module refactoring
- [Vehicle Rental GBFS Target Architecture (Feb 24)](2026-02-24-vehicle-rental-gbfs-target-architecture.md) — Full target architecture with theme breakdown
- [GBFS Geofencing Speed Restrictions (Feb 5)](2026-02-05-gbfs-geofencing-speed-restrictions.md) — Gap analysis for speed restriction support
