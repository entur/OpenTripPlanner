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

### Real-Time Update Flow (Mermaid)

```mermaid
flowchart TB
    subgraph Feeds["Real-Time Feed Sources"]
        SIRI[SIRI-ET Feed]
        GTFS[GTFS-RT Feed]
    end
    
    subgraph Updaters["Entry Points / Updaters"]
        SiriUpdater[SiriETUpdater<br/>entry point]
        GtfsUpdater[PollingTripUpdater<br/>entry point]
    end
    
    subgraph Parsers["Format-Specific Parsers"]
        SiriParser[SiriTripUpdate Parser<br/>format-specific]
        GtfsParser[GtfsRtTripUpdate Parser<br/>format-specific]
    end
    
    subgraph CommonModel["Common Model Layer"]
        ParsedUpdate[ParsedTripUpdate<br/>common model]
    end
    
    subgraph Applier["Shared Application Logic"]
        TripApplier[TripUpdateApplier<br/>common component]
    end
    
    subgraph Output["Output Layer"]
        RTUpdate[RealTimeTripUpdate<br/>existing record]
    end
    
    subgraph Storage["Storage Layer"]
        SnapshotMgr[TimetableSnapshot Manager]
    end
    
    SIRI -->|XML/JSON| SiriUpdater
    GTFS -->|Protobuf| GtfsUpdater
    
    SiriUpdater -->|EstimatedVehicleJourney| SiriParser
    GtfsUpdater -->|TripUpdate protobuf| GtfsParser
    
    SiriParser -->|converts to| ParsedUpdate
    GtfsParser -->|converts to| ParsedUpdate
    
    ParsedUpdate -->|applies| TripApplier
    
    TripApplier -->|produces| RTUpdate
    
    RTUpdate -->|stored in| SnapshotMgr
    
    style ParsedUpdate fill:#90EE90,stroke:#2E8B57,stroke-width:3px
    style TripApplier fill:#87CEEB,stroke:#4682B4,stroke-width:3px
    style RTUpdate fill:#FFD700,stroke:#DAA520,stroke-width:2px
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

#### SIRI-ET Architecture (Mermaid)

```mermaid
graph TB
    SiriUpdater[SiriETUpdater]
    ETHandler[EstimatedTimetableHandler]
    Adapter[SiriRealTimeTripUpdateAdapter]
    ModifiedBuilder[ModifiedTripBuilder<br/>update existing trips]
    AddedBuilder[AddedTripBuilder<br/>new trips]
    ExtraBuilder[ExtraCallTripBuilder<br/>add stops]
    TripUpdate[TripUpdate<br/>SIRI-specific output]
    RTUpdate[RealTimeTripUpdate]
    Snapshot[TimetableSnapshotManager]
    
    SiriUpdater --> ETHandler
    ETHandler --> Adapter
    Adapter --> ModifiedBuilder
    Adapter --> AddedBuilder
    Adapter --> ExtraBuilder
    ModifiedBuilder --> TripUpdate
    AddedBuilder --> TripUpdate
    ExtraBuilder --> TripUpdate
    TripUpdate --> RTUpdate
    RTUpdate --> Snapshot
    
    style Adapter fill:#FFB6C1
    style TripUpdate fill:#FFD700
```

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

#### GTFS-RT Architecture (Mermaid)

```mermaid
graph TB
    Updater[PollingTripUpdater]
    Runnable[TripUpdateGraphWriterRunnable]
    Adapter[GtfsRealTimeTripUpdateAdapter]
    TimesUpdater[TripTimesUpdater]
    Patch[TripTimesPatch<br/>GTFS-specific output]
    RTUpdate[RealTimeTripUpdate]
    Snapshot[TimetableSnapshotManager]
    
    Updater --> Runnable
    Runnable --> Adapter
    Adapter --> TimesUpdater
    TimesUpdater --> Patch
    Patch --> RTUpdate
    RTUpdate --> Snapshot
    
    style Adapter fill:#FFB6C1
    style Patch fill:#FFD700
```

### Shared Infrastructure (Already Exists)

- `RealTimeTripUpdate`: Final output record for both updaters
- `TimetableSnapshotManager`: Buffer/commit pattern for updates
- `RealTimeTripTimesBuilder`: Builder for real-time trip times
- `TripPattern`, `StopPattern`: Domain models

## Common Model Design

### ParsedTripUpdate (Main Class)

```java
package org.opentripplanner.updater.trip.model;

/**
 * Format-independent representation of a trip update parsed from either
 * SIRI-ET or GTFS-RT. Immutable class with builder pattern.
 */
public final class ParsedTripUpdate {

  private final TripUpdateType updateType;
  private final TripReference tripReference;
  private final LocalDate serviceDate;
  private final List<ParsedStopTimeUpdate> stopTimeUpdates;
  @Nullable private final TripCreationInfo tripCreationInfo;
  @Nullable private final StopPatternModification stopPatternModification;
  private final TripUpdateOptions options;
  @Nullable private final String dataSource;
}
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

### TripReference (Trip Identification)

```java
public final class TripReference {

  @Nullable private final FeedScopedId tripId;
  @Nullable private final FeedScopedId routeId;
  @Nullable private final String startTime;
  @Nullable private final LocalDate startDate;
  @Nullable private final Direction direction;
  private final FuzzyMatchingHint fuzzyMatchingHint;

  public enum FuzzyMatchingHint {
    EXACT_MATCH_REQUIRED,
    FUZZY_MATCH_ALLOWED
  }
}
```

### ParsedStopTimeUpdate (Stop-Level Update)

```java
public final class ParsedStopTimeUpdate {

  private final StopReference stopReference;
  @Nullable private final Integer stopSequence;
  private final StopUpdateStatus status;
  @Nullable private final TimeUpdate arrivalUpdate;
  @Nullable private final TimeUpdate departureUpdate;
  @Nullable private final PickDrop pickup;
  @Nullable private final PickDrop dropoff;
  @Nullable private final I18NString stopHeadsign;
  @Nullable private final OccupancyStatus occupancy;
  private final boolean isExtraCall;
  private final boolean predictionInaccurate;
  private final boolean recorded;

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

Handles both SIRI's explicit times and GTFS-RT's delay-based times. Immutable class:

```java
public final class TimeUpdate {

  @Nullable private final Integer delaySeconds;
  @Nullable private final Integer absoluteTimeSecondsSinceMidnight;
  @Nullable private final Integer scheduledTimeSecondsSinceMidnight;
}
```

### StopReference (Stop Identification)

Supports both GTFS stop IDs and SIRI quay references. Immutable class:

```java
public final class StopReference {

  @Nullable private final FeedScopedId stopId;
  @Nullable private final String stopPointRef;
  @Nullable private final FeedScopedId assignedStopId;
}
```

### TripCreationInfo (For New Trips)

```java
public final class TripCreationInfo {

  private final FeedScopedId tripId;
  @Nullable private final FeedScopedId routeId;
  @Nullable private final RouteCreationInfo routeCreationInfo;
  @Nullable private final FeedScopedId serviceId;
  @Nullable private final I18NString headsign;
  @Nullable private final String shortName;
  @Nullable private final TransitMode mode;
  @Nullable private final String submode;
  @Nullable private final FeedScopedId operatorId;
  @Nullable private final Accessibility wheelchairAccessibility;
  private final List<FeedScopedId> replacedTrips;
}
```

### TripUpdateOptions (Processing Configuration)

```java
public final class TripUpdateOptions {
  private final ForwardsDelayPropagationType forwardsPropagation;
  private final BackwardsDelayPropagationType backwardsPropagation;
  private final boolean allowStopPatternModification;

  // SIRI provides explicit times; no delay interpolation needed
  public static TripUpdateOptions siriDefaults() {
    return new TripUpdateOptions(
      ForwardsDelayPropagationType.NONE,
      BackwardsDelayPropagationType.NONE,
      true
    );
  }

  // GTFS-RT may need delay interpolation
  public static TripUpdateOptions gtfsRtDefaults(
    ForwardsDelayPropagationType forward,
    BackwardsDelayPropagationType backward
  ) {
    return new TripUpdateOptions(forward, backward, true);
  }

  // Constructor, getters, builder, propagatesDelays(), equals, hashCode, toString
}
```

### Common Model Class Diagram

```mermaid
classDiagram
    class ParsedTripUpdate {
        +TripUpdateType updateType
        +TripReference tripReference
        +LocalDate serviceDate
        +List~ParsedStopTimeUpdate~ stopTimeUpdates
        +TripCreationInfo tripCreationInfo
        +TripUpdateOptions options
        +String dataSource
    }
    
    class TripUpdateType {
        <<enumeration>>
        UPDATE_EXISTING
        CANCEL_TRIP
        DELETE_TRIP
        ADD_NEW_TRIP
        MODIFY_TRIP
    }
    
    class ParsedStopTimeUpdate {
        +StopReference stopReference
        +Integer stopSequence
        +StopUpdateStatus status
        +TimeUpdate arrivalUpdate
        +TimeUpdate departureUpdate
        +PickDrop pickup
        +PickDrop dropoff
        +boolean isExtraCall
        +boolean predictionInaccurate
        +boolean recorded
    }
    
    class TimeUpdate {
        +Integer delaySeconds
        +Integer absoluteTimeSecondsSinceMidnight
        +Integer scheduledTimeSecondsSinceMidnight
        +resolveTime(int scheduledTime) int
    }
    
    class StopUpdateStatus {
        <<enumeration>>
        SCHEDULED
        SKIPPED
        CANCELLED
        NO_DATA
        ADDED
    }
    
    class TripReference {
        +FeedScopedId tripId
        +FeedScopedId routeId
        +String startTime
        +LocalDate startDate
        +Direction direction
        +FuzzyMatchingHint hint
    }
    
    class StopReference {
        +FeedScopedId stopId
        +String stopPointRef
        +FeedScopedId assignedStopId
    }
    
    class TripUpdateOptions {
        +DelayPropagation delayPropagation
        +boolean allowStopPatternModification
    }
    
    ParsedTripUpdate "1" --> "*" ParsedStopTimeUpdate
    ParsedTripUpdate --> TripUpdateType
    ParsedTripUpdate --> TripReference
    ParsedTripUpdate --> TripUpdateOptions
    ParsedStopTimeUpdate --> TimeUpdate : arrival
    ParsedStopTimeUpdate --> TimeUpdate : departure
    ParsedStopTimeUpdate --> StopUpdateStatus
    ParsedStopTimeUpdate --> StopReference
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

public final class TripUpdateParserContext {
  private final String feedId;
  private final ZoneId timeZone;
  private final Supplier<LocalDate> localDateNow;
  // Constructor, getters, createId() helper method
}
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

public final class TripUpdateApplierContext {
  private final String feedId;
  @Nullable private final TimetableSnapshotManager snapshotManager;
  // Constructor, getters
}
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

### Parser Responsibilities Diagram

```mermaid
graph LR
    subgraph "SIRI Parser"
        S1[Parse SIRI-ET XML]
        S2[Extract EstimatedVehicleJourney]
        S3[Extract Calls<br/>RecordedCall/EstimatedCall]
        S4[Map to ParsedTripUpdate]
        S5[Handle SIRI-specific:<br/>- ExtraCall flag<br/>- StopPointRef<br/>- Quay references]
    end
    
    subgraph "GTFS-RT Parser"
        G1[Parse GTFS-RT Protobuf]
        G2[Extract TripUpdate]
        G3[Extract StopTimeUpdate]
        G4[Map to ParsedTripUpdate]
        G5[Handle GTFS-specific:<br/>- Delay values<br/>- MFDZ extensions<br/>- Stop IDs]
    end
    
    subgraph "Common Output"
        Output[ParsedTripUpdate]
    end
    
    S1 --> S2 --> S3 --> S4 --> S5 --> Output
    G1 --> G2 --> G3 --> G4 --> G5 --> Output
    
    style Output fill:#90EE90
```

### Applier Component Structure

```mermaid
graph TB
    subgraph "TripUpdateApplier"
        Input[ParsedTripUpdate]
        Router{Update Type?}

        subgraph "Handlers"
            UpdateHandler[UpdateExistingTripHandler]
            AddHandler[AddNewTripHandler]
            CancelHandler[CancelTripHandler]
            ModifyHandler[ModifyTripHandler]
        end

        subgraph "Shared Services"
            Resolver[EntityResolver<br/>stops, trips, routes]
            Matcher[TripMatcher<br/>fuzzy matching]
            Builder[RealTimeTripTimesBuilder]
            Interpolator[DelayInterpolator]
            Validator[UpdateValidator]
        end

        Output[RealTimeTripUpdate]
    end

    Input --> Router
    Router -->|UPDATE_EXISTING| UpdateHandler
    Router -->|ADD_NEW_TRIP| AddHandler
    Router -->|CANCEL_TRIP| CancelHandler
    Router -->|MODIFY_TRIP| ModifyHandler

    UpdateHandler --> Resolver
    UpdateHandler --> Matcher
    UpdateHandler --> Builder
    UpdateHandler --> Interpolator

    AddHandler --> Resolver
    AddHandler --> Builder

    CancelHandler --> Matcher

    ModifyHandler --> Resolver
    ModifyHandler --> Matcher
    ModifyHandler --> Builder

    UpdateHandler --> Output
    AddHandler --> Output
    CancelHandler --> Output
    ModifyHandler --> Output

    style Input fill:#90EE90
    style Output fill:#FFD700
```

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

### Update Flow by Type Diagram

```mermaid
stateDiagram-v2
    [*] --> DetermineType: ParsedTripUpdate
    
    DetermineType --> UpdateExisting: UPDATE_EXISTING
    DetermineType --> AddNewTrip: ADD_NEW_TRIP
    DetermineType --> CancelTrip: CANCEL_TRIP
    DetermineType --> ModifyTrip: MODIFY_TRIP
    
    UpdateExisting --> ResolveTrip: Find scheduled trip
    ResolveTrip --> UpdateTimes: Apply time updates
    UpdateTimes --> InterpolateDelays: If configured
    InterpolateDelays --> BuildResult: Create RealTimeTripUpdate
    
    AddNewTrip --> CreatePattern: Build new TripPattern
    CreatePattern --> SetTimes: Set stop times
    SetTimes --> BuildResult
    
    CancelTrip --> ResolveTrip2: Find scheduled trip
    ResolveTrip2 --> MarkCanceled: Set cancellation flag
    MarkCanceled --> BuildResult

    ModifyTrip --> ResolveTrip3: Find scheduled trip
    ResolveTrip3 --> ModifyPattern: Modify stop pattern
    ModifyPattern --> BuildResult

    BuildResult --> [*]: RealTimeTripUpdate
```

### SIRI Extra Call Sequence

```mermaid
sequenceDiagram
    participant Feed as SIRI-ET Feed
    participant Parser as SiriParser
    participant Model as ParsedTripUpdate
    participant Applier as TripUpdateApplier
    participant Handler as ModifyTripHandler
    participant Transit as Transit Model

    Feed->>Parser: EstimatedVehicleJourney<br/>with ExtraCall=true
    Parser->>Parser: Extract calls
    Parser->>Parser: Detect isExtraCall flag
    Parser->>Model: Create ParsedTripUpdate<br/>type=MODIFY_TRIP
    Model->>Model: Mark stop with isExtraCall=true
    Model->>Applier: Submit update
    Applier->>Handler: Route to ModifyTripHandler
    Handler->>Transit: Find scheduled trip
    Handler->>Handler: Validate extra call constraints
    Handler->>Handler: Insert extra stop in pattern
    Handler->>Transit: Create new TripPattern
    Handler->>Applier: Return RealTimeTripUpdate
    Applier->>Transit: Apply to TimetableSnapshot
```

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

### Migration Strategy Diagram

```mermaid
graph TB
    subgraph "Phase 1: Common Model"
        P1A[Define ParsedTripUpdate]
        P1B[Define ParsedStopTimeUpdate]
        P1C[Define TripUpdateType enum]
        P1D[Define supporting types]
    end
    
    subgraph "Phase 2: Parsers"
        P2A[Implement SiriParser]
        P2B[Implement GtfsRtParser]
        P2C[Add parser unit tests]
    end
    
    subgraph "Phase 3: Applier"
        P3A[Implement TripUpdateApplier]
        P3B[Implement update handlers]
        P3C[Port delay interpolation]
        P3D[Add applier unit tests]
    end
    
    subgraph "Phase 4: Integration"
        P4A[Wire parsers to updaters]
        P4B[Wire applier to snapshot manager]
        P4C[Run integration tests]
        P4D[Parallel comparison testing]
    end
    
    subgraph "Phase 5: Clean Up"
        P5A[Remove old implementations]
        P5B[Update documentation]
        P5C[Final testing]
    end
    
    P1A --> P1B --> P1C --> P1D
    P1D --> P2A
    P1D --> P2B
    P2A --> P2C
    P2B --> P2C
    P2C --> P3A
    P3A --> P3B --> P3C --> P3D
    P3D --> P4A
    P3D --> P4B
    P4A --> P4C
    P4B --> P4C
    P4C --> P4D
    P4D --> P5A --> P5B --> P5C
    
    style P1A fill:#FFE4B5
    style P2A fill:#FFE4B5
    style P3A fill:#FFE4B5
    style P4A fill:#FFE4B5
    style P5A fill:#FFE4B5
```

### Key Design Patterns

```mermaid
graph TB
    subgraph "Strategy Pattern"
        S1[TripMatcher interface]
        S2[SiriFuzzyTripMatcher impl]
        S3[GtfsRealtimeFuzzyTripMatcher impl]
        S1 --> S2
        S1 --> S3
    end
    
    subgraph "Builder Pattern"
        B1[RealTimeTripTimesBuilder]
        B2[Fluent API for constructing]
        B3[Validation before build]
        B1 --> B2 --> B3
    end
    
    subgraph "Adapter Pattern"
        A1[Format-specific input]
        A2[Parser adapter]
        A3[Common model output]
        A1 --> A2 --> A3
    end
    
    subgraph "Command Pattern"
        C1[ParsedTripUpdate = command]
        C2[TripUpdateApplier = invoker]
        C3[Handlers = receivers]
        C1 --> C2 --> C3
    end
```

## References

- [SIRI-ET Specification](https://www.vdv.de/siri.aspx)
- [GTFS-RT Specification](https://developers.google.com/transit/gtfs-realtime)
- OTP Documentation: `doc/user/UpdaterConfig.md`
- Existing Implementation: `application/src/main/java/org/opentripplanner/updater/trip/`
