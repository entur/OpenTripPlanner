# Vehicle Rental Street — Geofencing Enforcement

This package contains the geofencing zone enforcement system for vehicle rental routing.
It intercepts edge traversals to enforce zone restrictions (no-drop-off, no-traversal,
business area boundaries) during A* street searches.

## Architecture

The system uses an interceptor pattern with strategy-based enforcement:

```
StreetEdge.traverse()
  └─ GeofencingInterceptor.apply()
       ├─ GeofencingEnforcement       — strategy interface, dispatched per zone type
       │    ├─ RestrictedZoneEnforcement
       │    └─ BusinessAreaEnforcement
       ├─ DeferredForkHandler          — completes deferred renting forks (arriveBy)
       ├─ WalkerBoundaryHandler        — HAVE_RENTED walkers at boundaries (arriveBy)
       └─ NetworkCommitmentHandler     — generic arriveBy network commitment
```

**GeofencingInterceptor** is the entry point, called from `StreetEdge.traverse()`. It returns
`State[]` to override normal traversal, or `null` to let it proceed.

**GeofencingEnforcement** is the strategy interface. Implementations are stateless singletons
resolved via `GeofencingEnforcement.forZone(zone)`. The interface has two flavors of method:
per-boundary-position methods dispatched when the edge has boundary markers, and a set-level
`enforceInside` check dispatched on every traversal.

**DeferredForkHandler** creates the deferred RENTING_FLOATING fork one edge after a zone
boundary, so the renting state's backEdge is safely outside the zone when the itinerary is
reversed to forward time.

**WalkerBoundaryHandler** dispatches HAVE_RENTED walker enforcement at zone boundaries the
walker crossed in forward time.

**NetworkCommitmentHandler** handles generic (null-network) RENTING_FLOATING states in arriveBy
searches. When crossing a zone boundary, it forks committed branches for each applicable network
and continues the generic state with updated `committedNetworks`.

## Boundary Infrastructure

- **GeofencingBoundaryExtension** — `record(zone, entering)` placed on boundary-crossing vertices
  by `GeofencingZoneApplier`. `entering=true` means traversing away from this vertex enters the zone.
- **GeofencingZoneIndex** — STRtree spatial index for zone containment queries.
- **GeofencingZoneApplier** — applies boundary extensions and builds the spatial index from a
  collection of GBFS geofencing zones.

## Zone State Tracking

Zone state lives on routing `State` (via `StateData.currentGeofencingZones`), updated at
boundary-crossing edges by `StateEditor.updateGeofencingZones()`. Per-field enforcement
(`isDropOffBannedByCurrentZones`, `isTraversalBannedByCurrentZones`) resolves restrictions via
priority-based precedence across overlapping zones.
