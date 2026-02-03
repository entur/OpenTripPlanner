# Unified Real-Time Trip Updaters Design Document

## Overview

This document describes a proposed refactoring of the SIRI-ET and GTFS-RT real-time trip updaters in OpenTripPlanner to share common logic for applying updates to the transit model.

## Problem Statement

OTP supports real-time trip updates from two feed formats:
- **SIRI-ET** (Service Interface for Real-time Information - Estimated Timetable)
- **GTFS-RT** (General Transit Feed Specification - Realtime)

Both updaters implement the same use cases:
- Updating arrival/departure times for existing trips
- Cancelling trips
- Adding new trips not in the schedule
- Modifying stop patterns (skipping stops, adding stops)

Currently, each updater has its own implementation for applying these updates to the transit model, leading to:
- Duplicated logic
- Inconsistent behavior between formats
- Higher maintenance burden
- Harder to add new features consistently

## Proposed Solution

Split each updater into two sub-components:

1. **Parser** (format-specific): Parses real-time messages and converts them to a common model
2. **Applier** (shared): Applies the common model changes to the transit model

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           Real-Time Update Flow                              │
└─────────────────────────────────────────────────────────────────────────────┘

                SIRI-ET Feed                           GTFS-RT Feed
                     │                                      │
                     ▼                                      ▼
          ┌──────────────────┐                   ┌──────────────────┐
          │  SiriETUpdater   │                   │PollingTripUpdater│
          │  (entry point)   │                   │  (entry point)   │
          └────────┬─────────┘                   └────────┬─────────┘
                   │                                      │
                   ▼                                      ▼
          ┌──────────────────┐                   ┌──────────────────┐
          │SiriTripUpdate    │                   │GtfsRtTripUpdate  │
          │    Parser        │                   │    Parser        │
          │(format-specific) │                   │(format-specific) │
          └────────┬─────────┘                   └────────┬─────────┘
                   │                                      │
                   │      ParsedTripUpdate                │
                   │      (common model)                  │
                   │                                      │
                   └──────────────┬───────────────────────┘
                                  │
                                  ▼
                        ┌──────────────────┐
                        │ TripUpdateApplier│
                        │(common component)│
                        └────────┬─────────┘
                                 │
                                 ▼
                        ┌──────────────────┐
                        │RealTimeTripUpdate│
                        │(existing record) │
                        └────────┬─────────┘
                                 │
                                 ▼
                        ┌──────────────────┐
                        │TimetableSnapshot │
                        │    Manager       │
                        └──────────────────┘
```

## Current Architecture

### SIRI-ET Update Flow

```
SiriETUpdater
    └── EstimatedTimetableHandler
        └── SiriRealTimeTripUpdateAdapter
            ├── ModifiedTripBuilder (update existing trips)
            ├── AddedTripBuilder (new trips)
            └── ExtraCallTripBuilder (add stops)
                └── TripUpdate (SIRI-specific output)
                    └── RealTimeTripUpdate
                        └── TimetableSnapshotManager
```

**Key Classes:**
- `SiriRealTimeTripUpdateAdapter`: Main adapter coordinating updates
- `EntityResolver`: Resolves SIRI references to OTP entities
- `SiriFuzzyTripMatcher`: Matches trips when exact IDs unavailable
- `CallWrapper`: Unified interface for EstimatedCall/RecordedCall
- `TimetableHelper`: Applies time updates to trip times

### GTFS-RT Update Flow

```
PollingTripUpdater
    └── TripUpdateGraphWriterRunnable
        └── GtfsRealTimeTripUpdateAdapter
            └── TripTimesUpdater
                └── TripTimesPatch (GTFS-specific output)
                    └── RealTimeTripUpdate
                        └── TimetableSnapshotManager
```

**Key Classes:**
- `GtfsRealTimeTripUpdateAdapter`: Main adapter with update logic
- `TripTimesUpdater`: Creates/updates trip times from GTFS-RT messages
- `TripUpdate`, `StopTimeUpdate`, `TripDescriptor`: Wrapper classes for protobuf
- `ForwardsDelayInterpolator`, `BackwardsDelayInterpolator`: Delay propagation

### Shared Infrastructure (Already Exists)

- `RealTimeTripUpdate`: Final output record for both updaters
- `TimetableSnapshotManager`: Buffer/commit pattern for updates
- `RealTimeTripTimesBuilder`: Builder for real-time trip times
- `TripPattern`, `StopPattern`: Domain models

## Common Model Design

### ParsedTripUpdate (Main Record)

```java
package org.opentripplanner.updater.trip.model;

/**
 * Format-independent representation of a trip update parsed from either
 * SIRI-ET or GTFS-RT.
 */
public record ParsedTripUpdate(
    TripUpdateType updateType,
    TripReference tripReference,
    LocalDate serviceDate,
    List<ParsedStopTimeUpdate> stopTimeUpdates,
    @Nullable TripCreationInfo tripCreationInfo,
    @Nullable StopPatternModification stopPatternModification,
    TripUpdateOptions options,
    @Nullable String dataSource
) {}
```

### TripUpdateType (Enum)

Maps update semantics from both formats:

| Type | Description | SIRI-ET | GTFS-RT |
|------|-------------|---------|---------|
| `UPDATE_EXISTING` | Update times on existing trip | TRIP_UPDATE | SCHEDULED |
| `CANCEL_TRIP` | Cancel entire trip | Cancellation=true | CANCELED |
| `DELETE_TRIP` | Delete trip (remove from schedule) | — | DELETED |
| `ADD_NEW_TRIP` | Add trip not in schedule | ExtraJourney=true | NEW, ADDED |
| `MODIFY_TRIP` | Modify stop pattern | EXTRA_CALL | REPLACEMENT |

### Feature Comparison: UPDATE_EXISTING / TRIP_UPDATE

The `UPDATE_EXISTING` type (SIRI `TRIP_UPDATE` / GTFS-RT `SCHEDULED`) has different capabilities in each format:

| Feature | SIRI-ET | GTFS-RT |
|---------|---------|---------|
| Update arrival/departure times | ✅ | ✅ |
| Change pickup/dropoff types | ✅ | ✅ |
| Cancel individual stops | ✅ (`isCancellation`) | ✅ (`SKIPPED`) |
| Replace stop (same station) | ✅ | ✅ |
| Replace stop (any stop) | ❌ | ✅ (`assignedStopId`) |
| Add/remove stops | ❌ | ❌ |
| Cancel entire trip | ✅ | ✅ (via `CANCELED`) |

**Key Differences:**

1. **Stop Replacement Constraints:**
   - SIRI-ET: Stops can only be replaced by other stops belonging to the same station (`isPartOfSameStationAs`)
   - GTFS-RT: Any stop in the graph can replace another via `assignedStopId`

2. **Number of Stops:**
   - SIRI-ET: Must have exactly the same number of stops as the scheduled pattern (returns `TOO_FEW_STOPS` or `TOO_MANY_STOPS` errors)
   - GTFS-RT: Must have exactly the same number of stops as the scheduled pattern

3. **Adding Stops:**
   - SIRI-ET: Use `MODIFY_TRIP` update type with stops marked as `isExtraCall=true`
   - GTFS-RT: Use `MODIFY_TRIP` update type (maps to `REPLACEMENT`) to completely replace the stop pattern

### Feature Comparison: MODIFY_TRIP

The `MODIFY_TRIP` type allows modifying the stop pattern of an existing scheduled trip. This is used differently in each format:

| Feature | SIRI-ET (EXTRA_CALL) | GTFS-RT (REPLACEMENT) |
|---------|----------------------|----------------------|
| Requires existing scheduled trip | ✅ | ✅ |
| Insert new stops | ✅ (marked as extra call) | ✅ |
| Remove stops | ❌ | ✅ |
| Replace stop (same station) | ✅ | ✅ |
| Replace stop (any stop) | ❌ | ✅ |
| Change stop order | ❌ | ✅ |
| Completely new pattern | ❌ | ✅ |
| Update times | ✅ | ✅ |
| Change pickup/dropoff | ✅ | ✅ |

**Key Differences:**

1. **Pattern Modification Freedom:**
   - SIRI-ET EXTRA_CALL: Can only **insert** new stops (marked with `ExtraCall=true`). All other stops must match the original pattern (same stop or same-station replacement). Cannot remove stops or change stop order.
   - GTFS-RT REPLACEMENT: Complete freedom to define a new stop pattern. Can add, remove, replace, or reorder stops.

2. **Stop Matching:**
   - SIRI-ET: Non-extra-call stops must match the scheduled pattern by stop ID or belong to the same station
   - GTFS-RT: No matching required - the new pattern completely replaces the old one

3. **Use Cases:**
   - SIRI-ET EXTRA_CALL: A bus makes an unscheduled stop (e.g., temporary detour with additional stops)
   - GTFS-RT REPLACEMENT: A trip is rerouted with a completely different stop sequence

**Unified Model:**

Both SIRI-ET extra calls and GTFS-RT replacements use the single `MODIFY_TRIP` type:
- The `ParsedStopTimeUpdate.isExtraCall` flag identifies which stops are insertions (SIRI-specific)
- The applier enforces SIRI-specific constraints when `isExtraCall` stops are present (insertions only, other stops must match)
- GTFS-RT has no such constraints (full pattern replacement)

### TripReference (Trip Identification)

```java
public record TripReference(
    @Nullable FeedScopedId tripId,
    @Nullable FeedScopedId routeId,
    @Nullable String startTime,
    @Nullable LocalDate startDate,
    @Nullable Direction direction,
    FuzzyMatchingHint fuzzyMatchingHint
) {
    public enum FuzzyMatchingHint {
        EXACT_MATCH_REQUIRED,
        FUZZY_MATCH_ALLOWED
    }
}
```

### ParsedStopTimeUpdate (Stop-Level Update)

```java
public record ParsedStopTimeUpdate(
    StopReference stopReference,
    @Nullable Integer stopSequence,
    StopUpdateStatus status,
    @Nullable TimeUpdate arrivalUpdate,
    @Nullable TimeUpdate departureUpdate,
    @Nullable PickDrop pickup,
    @Nullable PickDrop dropoff,
    @Nullable I18NString stopHeadsign,
    @Nullable OccupancyStatus occupancy,
    boolean isExtraCall,
    boolean predictionInaccurate,
    boolean recorded
) {
    public enum StopUpdateStatus {
        SCHEDULED,  // Normal scheduled stop
        SKIPPED,    // Stop is skipped
        CANCELLED,  // Stop is cancelled
        NO_DATA,    // No prediction available
        ADDED       // Extra call (not in schedule)
    }
}
```

### TimeUpdate (Unified Time Representation)

Handles both SIRI's explicit times and GTFS-RT's delay-based times:

```java
public record TimeUpdate(
    @Nullable Integer delaySeconds,
    @Nullable Integer absoluteTimeSecondsSinceMidnight,
    @Nullable Integer scheduledTimeSecondsSinceMidnight
) {
    public static TimeUpdate ofDelay(int delaySeconds) {
        return new TimeUpdate(delaySeconds, null, null);
    }

    public static TimeUpdate ofAbsolute(int absoluteTime, @Nullable Integer scheduledTime) {
        return new TimeUpdate(null, absoluteTime, scheduledTime);
    }

    public int resolveTime(int scheduledTime) {
        if (absoluteTimeSecondsSinceMidnight != null) {
            return absoluteTimeSecondsSinceMidnight;
        }
        if (delaySeconds != null) {
            return scheduledTime + delaySeconds;
        }
        return scheduledTime;
    }
}
```

### StopReference (Stop Identification)

Supports both GTFS stop IDs and SIRI quay references:

```java
public record StopReference(
    @Nullable FeedScopedId stopId,
    @Nullable String stopPointRef,
    @Nullable FeedScopedId assignedStopId
) {}
```

### TripCreationInfo (For New Trips)

```java
public record TripCreationInfo(
    FeedScopedId tripId,
    @Nullable FeedScopedId routeId,
    @Nullable RouteCreationInfo routeCreationInfo,
    @Nullable FeedScopedId serviceId,
    @Nullable I18NString headsign,
    @Nullable String shortName,
    @Nullable TransitMode mode,
    @Nullable String submode,
    @Nullable FeedScopedId operatorId,
    @Nullable Accessibility wheelchairAccessibility,
    List<TripOnServiceDate> replacedTrips
) {}
```

### TripUpdateOptions (Processing Configuration)

```java
public record TripUpdateOptions(
    DelayPropagation delayPropagation,
    boolean allowStopPatternModification
) {
    public record DelayPropagation(
        boolean propagateForward,
        BackwardsDelayPropagationType backwardsPropagation
    ) {}

    // SIRI provides explicit times; no delay interpolation needed
    public static TripUpdateOptions siriDefaults() {
        return new TripUpdateOptions(
            new DelayPropagation(false, BackwardsDelayPropagationType.NONE),
            true
        );
    }

    // GTFS-RT may need delay interpolation
    public static TripUpdateOptions gtfsRtDefaults(
            ForwardsDelayPropagationType forward,
            BackwardsDelayPropagationType backward) {
        return new TripUpdateOptions(
            new DelayPropagation(
                forward != ForwardsDelayPropagationType.NONE,
                backward
            ),
            true
        );
    }
}
```

## Interface Design

### TripUpdateParser Interface

```java
package org.opentripplanner.updater.trip;

/**
 * Parses format-specific real-time messages into the common model.
 */
public interface TripUpdateParser<T> {
    /**
     * Parse a single format-specific update into the common model.
     *
     * @param update the format-specific update
     * @param context parsing context containing entity resolver, trip matcher, etc.
     * @return Result containing either parsed update or error
     */
    Result<ParsedTripUpdate, UpdateError> parse(T update, TripUpdateParserContext context);
}

public record TripUpdateParserContext(
    EntityResolver entityResolver,
    @Nullable TripMatcher tripMatcher,
    ZoneId timeZone,
    String feedId,
    Supplier<LocalDate> localDateNow
) {}
```

### TripUpdateApplier Interface

```java
package org.opentripplanner.updater.trip;

/**
 * Applies parsed trip updates to the transit model.
 * This is the common component shared by both SIRI-ET and GTFS-RT.
 */
public interface TripUpdateApplier {
    /**
     * Apply a parsed trip update to create/update trip times.
     *
     * @param parsedUpdate the format-independent parsed update
     * @param context application context
     * @return Result containing RealTimeTripUpdate for the snapshot manager
     */
    Result<RealTimeTripUpdate, UpdateError> apply(
        ParsedTripUpdate parsedUpdate,
        TripUpdateApplierContext context
    );
}

public record TripUpdateApplierContext(
    TransitEditorService transitService,
    TripPatternCache tripPatternCache,
    TimetableSnapshotManager snapshotManager
) {}
```

### TripMatcher Interface (Common Fuzzy Matching)

```java
package org.opentripplanner.updater.trip;

/**
 * Common interface for trip matching (fuzzy or exact).
 */
public interface TripMatcher {
    Result<TripAndPattern, UpdateError> matchTrip(
        TripReference reference,
        Function<TripPattern, Timetable> getTimetable
    );
}
```

## Implementation Plan

### Phase 1: Create Common Model Package

Create new package `org.opentripplanner.updater.trip.model` with:
- `ParsedTripUpdate.java`
- `TripUpdateType.java`
- `TripReference.java`
- `ParsedStopTimeUpdate.java`
- `TimeUpdate.java`
- `StopReference.java`
- `TripCreationInfo.java`
- `StopPatternModification.java`
- `TripUpdateOptions.java`

### Phase 2: Create Parser Interface and Implementations

1. **Create interface:** `TripUpdateParser<T>`

2. **Implement SiriTripUpdateParser:**
   - Extract parsing logic from `SiriRealTimeTripUpdateAdapter`
   - Reuse: `EntityResolver`, `SiriFuzzyTripMatcher`, `CallWrapper`
   - Convert `EstimatedVehicleJourney` → `ParsedTripUpdate`

3. **Implement GtfsRtTripUpdateParser:**
   - Extract parsing logic from `GtfsRealTimeTripUpdateAdapter`
   - Convert `GtfsRealtime.TripUpdate` → `ParsedTripUpdate`
   - Handle MFDZ extensions

### Phase 3: Create Common Applier

1. **Create interface:** `TripUpdateApplier`

2. **Implement DefaultTripUpdateApplier:**
   - Consolidate logic from:
     - `SiriRealTimeTripUpdateAdapter.addTripToGraphAndBuffer()`
     - `ModifiedTripBuilder`, `AddedTripBuilder`, `ExtraCallTripBuilder`
     - `GtfsRealTimeTripUpdateAdapter.handleScheduledTrip()`, etc.
     - `TripTimesUpdater.createUpdatedTripTimesFromGtfsRt()`

3. **Merge TripPatternCache implementations:**
   - Combine `SiriTripPatternCache` and `TripPatternCache`

### Phase 4: Integrate into Existing Updaters

Modify updaters to use new architecture while maintaining backward compatibility:

```java
// Example: SiriETUpdater integration
public void applyEstimatedTimetable(
    List<EstimatedTimetableDeliveryStructure> deliveries,
    UpdateIncrementality incrementality
) {
    if (incrementality == FULL_DATASET) {
        snapshotManager.clearBuffer(feedId);
    }

    for (var delivery : deliveries) {
        for (var frame : delivery.getEstimatedJourneyVersionFrames()) {
            for (var journey : frame.getEstimatedVehicleJourneys()) {
                var parseResult = siriParser.parse(journey, parserContext);
                if (parseResult.isSuccess()) {
                    var applyResult = applier.apply(parseResult.get(), applierContext);
                    if (applyResult.isSuccess()) {
                        snapshotManager.updateBuffer(applyResult.get());
                    }
                }
            }
        }
    }
}
```

### Phase 5: Clean Up

- Deprecate and remove duplicated code
- Update documentation
- Add migration notes to changelog

## Files to Modify

| File | Changes |
|------|---------|
| `updater/trip/siri/SiriRealTimeTripUpdateAdapter.java` | Extract parsing to parser, simplify |
| `updater/trip/gtfs/GtfsRealTimeTripUpdateAdapter.java` | Extract parsing to parser, simplify |
| `updater/trip/gtfs/TripTimesUpdater.java` | Move core logic to applier |
| `updater/trip/siri/ModifiedTripBuilder.java` | Logic moves to applier |
| `updater/trip/siri/AddedTripBuilder.java` | Logic moves to applier |
| `updater/trip/siri/ExtraCallTripBuilder.java` | Logic moves to applier |
| `updater/trip/siri/SiriTripPatternCache.java` | Merge with TripPatternCache |
| `updater/trip/gtfs/TripPatternCache.java` | Merge with SiriTripPatternCache |

## New Files to Create

| File | Purpose |
|------|---------|
| `updater/trip/model/ParsedTripUpdate.java` | Common model record |
| `updater/trip/model/TripUpdateType.java` | Update type enum |
| `updater/trip/model/TripReference.java` | Trip identification |
| `updater/trip/model/ParsedStopTimeUpdate.java` | Stop-level update |
| `updater/trip/model/TimeUpdate.java` | Time representation |
| `updater/trip/model/StopReference.java` | Stop identification |
| `updater/trip/model/TripCreationInfo.java` | New trip creation info |
| `updater/trip/model/TripUpdateOptions.java` | Processing options |
| `updater/trip/TripUpdateParser.java` | Parser interface |
| `updater/trip/TripUpdateApplier.java` | Applier interface |
| `updater/trip/DefaultTripUpdateApplier.java` | Common applier |
| `updater/trip/siri/SiriTripUpdateParser.java` | SIRI parser |
| `updater/trip/gtfs/GtfsRtTripUpdateParser.java` | GTFS-RT parser |
| `updater/trip/UnifiedTripPatternCache.java` | Merged pattern cache |

## Challenges and Mitigations

### 1. SIRI Extra Calls (Adding Stops)

**Challenge:** SIRI supports adding extra stops to existing trips, which GTFS-RT does not directly support.

**Mitigation:**
- Both use unified `MODIFY_TRIP` type
- `isExtraCall` flag on `ParsedStopTimeUpdate` identifies SIRI insertions
- Applier enforces SIRI constraints when `isExtraCall` stops are present

### 2. GTFS-RT Delay Interpolation

**Challenge:** GTFS-RT often provides partial updates requiring delay interpolation; SIRI provides explicit times.

**Mitigation:**
- `TripUpdateOptions.DelayPropagation` configuration per format
- SIRI parsers set `propagateForward=false`
- GTFS-RT parsers configure based on updater settings
- Applier applies interpolation only when configured

### 3. Different Fuzzy Matching

**Challenge:** Different fuzzy matching implementations for SIRI vs GTFS-RT.

**Mitigation:**
- Common `TripMatcher` interface
- Two implementations: `SiriFuzzyTripMatcher`, `GtfsRealtimeFuzzyTripMatcher`
- Parser context provides the appropriate matcher

### 4. Entity Resolution

**Challenge:** SIRI uses NeTEx-style quay references; GTFS uses stop IDs.

**Mitigation:**
- `StopReference` supports both `stopId` and `stopPointRef`
- Applier uses `EntityResolver` to resolve both formats
- SIRI parser creates `StopReference.ofStopPointRef()`
- GTFS parser creates `StopReference.ofStopId()`

### 5. MFDZ Extensions

**Challenge:** GTFS-RT MFDZ extensions for pickup/dropoff types and other properties.

**Mitigation:**
- Parser handles extensions during conversion
- Maps to common `PickDrop` enum
- Common model is extension-agnostic

## Testing Strategy

### Unit Tests

1. **Parser Tests:**
   - Test each parser with format-specific fixtures
   - Verify correct mapping to common model
   - Test error handling for invalid inputs
   - Test edge cases (missing fields, malformed data)

2. **Applier Tests:**
   - Test each update type independently
   - Use mock `TransitEditorService` and `TimetableSnapshotManager`
   - Verify correct `RealTimeTripUpdate` output
   - Test delay interpolation

3. **Round-Trip Tests:**
   - Parse → Apply → Verify identical behavior to old implementation

### Integration Tests

- Run existing SIRI-ET and GTFS-RT integration tests
- Add new integration tests for combined scenarios
- Regression tests for known edge cases

### Comparison Testing

- Run both old and new implementations in parallel
- Compare outputs for identical inputs
- Log any differences for investigation

## Benefits

1. **Reduced Code Duplication:** Common applier eliminates duplicated update logic

2. **Consistent Behavior:** Both formats use the same code path for applying updates

3. **Easier Maintenance:** Bug fixes and improvements apply to both formats

4. **Better Testability:** Common model enables format-agnostic testing

5. **Clearer Separation of Concerns:** Parsing vs application logic clearly separated

6. **Extensibility:** Adding new formats (e.g., GTFS-RT v3) only requires a new parser

## Risks and Mitigation

| Risk | Impact | Mitigation |
|------|--------|------------|
| Regression in existing behavior | High | Extensive testing, parallel running, feature flag |
| Performance degradation | Medium | Benchmark before/after, optimize hot paths |
| Incomplete common model | Medium | Iterative refinement, start with core cases |
| Migration complexity | Medium | Phased approach, backward compatibility |

## Timeline Estimate

| Phase | Effort |
|-------|--------|
| Phase 1: Common Model | 2-3 days |
| Phase 2: Parsers | 3-4 days |
| Phase 3: Applier | 4-5 days |
| Phase 4: Integration | 2-3 days |
| Phase 5: Clean Up | 1-2 days |
| **Total** | **12-17 days** |

## References

- [SIRI-ET Specification](https://www.vdv.de/siri.aspx)
- [GTFS-RT Specification](https://developers.google.com/transit/gtfs-realtime)
- OTP Documentation: `doc/user/UpdaterConfig.md`
- Existing Implementation: `application/src/main/java/org/opentripplanner/updater/trip/`

---

## Implementation Progress

### Phase 1: Common Model Package ✅ COMPLETE

**Status:** All model classes created with tests (67 tests passing)

**Package:** `org.opentripplanner.updater.trip.model`

| File | Status | Tests |
|------|--------|-------|
| `TripUpdateType.java` | ✅ Complete | 7 tests |
| `TimeUpdate.java` | ✅ Complete | 9 tests |
| `StopReference.java` | ✅ Complete | 8 tests |
| `TripReference.java` | ✅ Complete | 8 tests |
| `ParsedStopTimeUpdate.java` | ✅ Complete | 6 tests |
| `TripCreationInfo.java` | ✅ Complete | 4 tests |
| `RouteCreationInfo.java` | ✅ Complete | (used by TripCreationInfo) |
| `StopPatternModification.java` | ✅ Complete | 8 tests |
| `TripUpdateOptions.java` | ✅ Complete | 6 tests |
| `ParsedTripUpdate.java` | ✅ Complete | 11 tests |

**Key Design Decisions:**
- `TimeUpdate` handles both SIRI's absolute times and GTFS-RT's delay-based times
- `StopReference` supports both GTFS stop IDs and SIRI stop point refs
- `TripReference` includes fuzzy matching hint for different matching strategies
- `TripUpdateOptions` reuses existing `ForwardsDelayPropagationType` and `BackwardsDelayPropagationType` enums
- All classes use builders for complex construction with sensible defaults

### Phase 2: Interfaces ✅ COMPLETE

**Status:** All interfaces created with tests (7 tests passing)

**Package:** `org.opentripplanner.updater.trip`

| File | Status | Description |
|------|--------|-------------|
| `TripUpdateParser.java` | ✅ Complete | Generic interface for parsing format-specific messages |
| `TripUpdateParserContext.java` | ✅ Complete | Context for parsers (feedId, timeZone, localDateNow) |
| `TripUpdateApplier.java` | ✅ Complete | Interface for applying parsed updates to transit model |
| `TripUpdateApplierContext.java` | ✅ Complete | Context for applier (feedId, snapshotManager) |

**Key Design Decisions:**
- `TripUpdateParser<T>` is generic to support different input types (SIRI's `EstimatedVehicleJourney`, GTFS-RT's `TripUpdate`)
- Parser produces `ParsedTripUpdate`, Applier produces `RealTimeTripUpdate`
- Both use `Result<T, UpdateError>` for error handling, consistent with existing codebase
- Context classes contain minimal required fields

### Phase 2b: Parser Implementations

**Status:** ✅ BOTH PARSERS COMPLETE & TESTED

| Parser | Status | Tests | Test Results |
|--------|--------|-------|--------------|
| `GtfsRtTripUpdateParser` | ✅ Complete | ✅ 16 tests | ✅ **ALL PASSING** |
| `SiriTripUpdateParser` | ✅ Complete | ✅ 17 tests | ✅ **ALL PASSING** |

**GTFS-RT Parser Implementation:**

**Class:** `org.opentripplanner.updater.trip.gtfs.GtfsRtTripUpdateParser`

✅ **FULLY TESTED & WORKING** - All 16 test cases passing

Full implementation of `TripUpdateParser<GtfsRealtime.TripUpdate>` interface:

- ✅ Parses all GTFS-RT `ScheduleRelationship` types:
  - `SCHEDULED` → `TripUpdateType.UPDATE_EXISTING`
  - `CANCELED` → `TripUpdateType.CANCEL_TRIP`
  - `DELETED` → `TripUpdateType.DELETE_TRIP`
  - `ADDED`/`NEW` → `TripUpdateType.ADD_NEW_TRIP`
  - `REPLACEMENT` → `TripUpdateType.MODIFY_TRIP`
  - Returns errors for `UNSCHEDULED` and `DUPLICATED`

- ✅ Stop time update parsing:
  - Delay-based times for scheduled trips (uses delay in seconds)
  - Absolute times for new trips (uses time since midnight)
  - Stop sequences, stop headsigns, assigned stop IDs
  - Pickup/dropoff types from `StopTimeProperties`
  - Skipped stop detection

- ✅ Trip creation info parsing:
  - Route ID, headsign, short name
  - Wheelchair accessibility from vehicle descriptor
  - Service date and trip descriptor fields

- ✅ Configuration preservation:
  - Forwards and backwards delay propagation types
  - Creates `TripUpdateOptions` with GTFS-RT defaults

- ✅ Error handling:
  - Missing trip ID validation
  - Date parsing error handling
  - Returns `Result<ParsedTripUpdate, UpdateError>`

**Test Coverage:** `GtfsRtTripUpdateParserTest` (16 test cases)
- Scheduled trip updates with delays
- Cancelled and deleted trips
- New trip creation with absolute times
- Replacement trips
- Skipped stops
- Assigned stop IDs
- Trip and stop properties
- Direction handling
- Error cases (missing trip ID, unsupported types)
- Empty updates
- Configuration preservation

**Key Design Decisions:**
1. Different parsing logic for scheduled vs. new trips (delay vs. absolute time)
2. Uses existing `ForwardsDelayPropagationType` and `BackwardsDelayPropagationType` enums
3. Maps protobuf wheelchair accessibility values to OTP `Accessibility` enum
4. Returns empty update lists for cancellations (no stop time processing needed)

**SIRI Parser Implementation:** ✅ COMPLETE

**Class:** `org.opentripplanner.updater.trip.siri.SiriTripUpdateParser`

Full implementation of `TripUpdateParser<EstimatedVehicleJourney>` interface:

- ✅ Handles all SIRI update types:
  - `TRIP_UPDATE` → `TripUpdateType.UPDATE_EXISTING`
  - `REPLACEMENT_DEPARTURE` (extra journey) → `TripUpdateType.ADD_NEW_TRIP`
  - `EXTRA_CALL` → `TripUpdateType.MODIFY_TRIP` (with `isExtraCall` flag on stops)
  - Cancellation → `TripUpdateType.CANCEL_TRIP`

- ✅ Stop time update parsing:
  - Absolute times (SIRI provides actual times, not delays)
  - Both RecordedCall and EstimatedCall via `CallWrapper`
  - Recorded vs. estimated flags
  - Prediction inaccuracy flags
  - Extra call detection
  - Cancelled stop handling

- ✅ Trip creation info parsing:
  - Trip ID from `EstimatedVehicleJourneyCode`
  - Route and operator resolution
  - Transit mode and submode mapping
  - Headsign and short name extraction
  - Route creation info for new routes

- ✅ SIRI-specific features:
  - Entity resolution for IDs
  - Destination displays → stop headsigns
  - Occupancy mapping
  - Pickup/dropoff activity mapping
  - Service date resolution from multiple sources
  - FramedVehicleJourneyRef support

- ✅ Error handling:
  - Empty stop point ref validation
  - Not monitored journey detection (with cancellation exception)
  - Missing service date handling
  - Missing operator handling

**Test Coverage:** `SiriTripUpdateParserTest` (17 test cases - ✅ ALL PASSING)
- Update existing trip with expected times ✅
- Cancelled trips ✅
- Extra journeys (new trips) ✅
- Extra calls (added stops) ✅
- Cancelled stops ✅
- Recorded vs. estimated calls ✅
- Prediction inaccuracy ✅
- Destination displays ✅
- Not monitored handling ✅
- Empty stop point ref errors ✅
- Occupancy data ✅
- Absolute time handling (not delays) ✅
- SIRI default options (no delay propagation) ✅
- FramedVehicleJourneyRef parsing ✅
- Multiple stops ✅
- Data source tracking ✅
- Not monitored but cancelled exception ✅

**Key Design Decisions:**
1. SIRI provides absolute times, not delays - uses `TimeUpdate.ofAbsolute()`
2. No delay propagation needed (explicit times provided)
3. Trip ID resolution from multiple sources (FramedVehicleJourneyRef, DatedVehicleJourneyRef, EstimatedVehicleJourneyCode)
4. Service date resolution from multiple sources with fallback to current date
5. Recorded calls prioritize actual times over expected times
6. Integration with existing SIRI infrastructure (EntityResolver, CallWrapper, mappers)

### Phase 3: Common Applier Implementation

**Status:** In Progress - Fuzzy Trip Matching Complete

**Fuzzy Trip Matching Implementation:**

The applier now supports fuzzy trip matching through a unified interface. This allows the system to find trips when exact IDs are not available in real-time feeds.

**New Files Created:**
- `FuzzyTripMatcher.java` - Interface for fuzzy trip matching
- `TripAndPattern.java` - Result record containing matched trip and pattern
- `LastStopArrivalTimeMatcher.java` - SIRI-style matcher (matches by last stop arrival time)
- `RouteDirectionTimeMatcher.java` - GTFS-RT-style matcher (matches by route/direction/start time)
- `NoOpFuzzyTripMatcher.java` - No-op implementation for when fuzzy matching is disabled

**TripResolver Extended:**
- Added optional `FuzzyTripMatcher` parameter to constructor
- New method `resolveTripWithPattern(ParsedTripUpdate, LocalDate)` that:
  1. Tries exact match first (trip ID or TripOnServiceDate ID)
  2. Falls back to fuzzy matching if allowed and configured
  3. Returns `TripAndPattern` preserving the pattern from fuzzy match

**Handler Updated:**
- `UpdateExistingTripHandler` now uses `resolveTripWithPattern()` for unified trip resolution

**Test Coverage:**
- `NoOpFuzzyTripMatcherTest` - Tests no-op matcher behavior
- `TripResolverTest.FuzzyMatchingTests` - Tests fuzzy matching integration in resolver

### Phase 4: Integration

**Status:** Not started

### Phase 5: Clean Up

**Status:** Not started
