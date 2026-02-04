---
date: 2025-11-06T06:15:54+0000
researcher: testower
git_commit: 24fa7de58a4f0a28ef1c2cbb343d98d1a71ddb47
branch: feature/transmodel-api-scooter-preferences
repository: entur/OpenTripPlanner
topic: "TransModel API Scooter Preferences and Issue #6572"
tags: [research, transmodel-api, scooter-preferences, vehicle-rental, issue-6572]
status: complete
last_updated: 2025-11-06
last_updated_by: testower
---

# Research: TransModel API Scooter Preferences and Issue #6572

**Date**: 2025-11-06T06:15:54+0000
**Researcher**: testower
**Git Commit**: 24fa7de58a4f0a28ef1c2cbb343d98d1a71ddb47
**Branch**: feature/transmodel-api-scooter-preferences
**Repository**: entur/OpenTripPlanner

## Research Question

How does the TransModel API handle scooter preferences, what is the relationship to bicycle preferences fallback mentioned in issue #6572, and how should the overall issue be solved once the API part is fixed?

## Summary

Issue #6572 identified three problems with scooter and bike rental speed configuration in OTP v2.6.0. PR #6599 fixed two of the three problems (GTFS API egress routing and access routing heuristic). The remaining problem is that **the TransModel API lacks native scooter preference parameters** and currently uses bicycle preference field names as a workaround.

### Key Findings

1. **TransModel API Current State**: Uses `bikeSpeed` and `bicycleOptimisationMethod` field names for both bicycle AND scooter routing due to backward compatibility requirements.

2. **GTFS GraphQL API**: Already has separate, properly-named `ScooterPreferencesInput` and `BicyclePreferencesInput` types with distinct field names.

3. **Internal Domain Model**: Maintains completely separate `BikePreferences` and `ScooterPreferences` classes with different capabilities (bikes support parking, transit boarding, and walking; scooters are rental-only).

4. **PR #6599 Fix**: Corrected the A* heuristic to use the correct speed for each vehicle type instead of defaulting to bicycle speed for scooter/car rentals.

5. **Solution Path**: Add native scooter preference fields to TransModel GraphQL schema and ScooterPreferencesMapper while maintaining backward compatibility with existing bicycle field names.

## Detailed Findings

### Component 1: TransModel API Current Implementation

**Location**: `/application/src/main/java/org/opentripplanner/apis/transmodel/`

#### GraphQL Schema Definition

**File**: `schema.graphql`

The TransModel schema currently has mode enums that include both bicycle and scooter modes but uses bicycle-named preference fields for both:

- **Street modes**: BICYCLE, SCOOTER (among others)
- **Preference fields**: All preferences use "bicycle" or "bike" naming convention
- **No scooter-specific fields**: The schema lacks `scooterSpeed`, `scooterReluctance`, etc.

#### ScooterPreferencesMapper Current Implementation

**File**: `/mapping/preferences/ScooterPreferencesMapper.java:9-33`

```java
public class ScooterPreferencesMapper {

  public static void mapScooterPreferences(
    ScooterPreferences.Builder scooter,
    DataFetcherDecorator callWith
  ) {
    // IMPORTANT: TransModel reuses "bikeSpeed" for scooter speed
    callWith.argument("bikeSpeed", scooter::withSpeed);

    // IMPORTANT: TransModel reuses "bicycleOptimisationMethod" for scooter
    callWith.argument("bicycleOptimisationMethod", scooter::withOptimizeType);

    // Backwards compatibility: walkReluctance sets scooter reluctance
    callWith.argument("walkReluctance", r -> {
      scooter.withReluctance((double) r);
    });

    // Conditional triangle mapping
    if (scooter.optimizeType() == VehicleRoutingOptimizeType.TRIANGLE) {
      scooter.withOptimizeTriangle(triangle -> {
        callWith.argument("triangleFactors.time", triangle::withTime);
        callWith.argument("triangleFactors.slope", triangle::withSlope);
        callWith.argument("triangleFactors.safety", triangle::withSafety);
      });
    }

    // Shared rental preferences mapper
    scooter.withRental(rental -> mapRentalPreferences(rental, callWith));
  }
}
```

**Key Issue**: This mapper explicitly reuses bicycle field names (`bikeSpeed`, `bicycleOptimisationMethod`) for scooter configuration, which is the backward compatibility workaround mentioned in issue #6572.

#### BikePreferencesMapper for Comparison

**File**: `/mapping/preferences/BikePreferencesMapper.java:9-47`

Uses the same field names as scooter mapper:
- `bikeSpeed` → bike speed
- `bicycleOptimisationMethod` → bike optimization type
- `walkReluctance` → bike reluctance (with additional derived calculation for walking reluctance)
- `triangleFactors.*` → triangle optimization

This confirms that both vehicle types read from the same GraphQL arguments.

### Component 2: GTFS GraphQL API (Already Fixed)

**Location**: `/application/src/main/java/org/opentripplanner/apis/gtfs/`

#### GraphQL Schema Definition

**File**: `schema.graphqls`

The GTFS API has separate, properly-named input types:

**BicyclePreferencesInput** (lines 4025-4044):
```graphql
input BicyclePreferencesInput {
  speed: Speed
  reluctance: Reluctance
  boardCost: Cost
  walk: BicycleWalkPreferencesInput
  rental: BicycleRentalPreferencesInput
  parking: BicycleParkingPreferencesInput
  optimization: CyclingOptimizationInput
}
```

**ScooterPreferencesInput** (lines 4624-4633):
```graphql
input ScooterPreferencesInput {
  speed: Speed
  reluctance: Reluctance
  rental: ScooterRentalPreferencesInput
  optimization: ScooterOptimizationInput
}
```

**Key Difference**: Separate types with distinct field names and capabilities (bicycle has parking/walking, scooter doesn't).

#### Preference Mappers

**BicyclePreferencesMapper** (`/mapping/routerequest/BicyclePreferencesMapper.java:17-166`)
- Maps `GraphQLBicyclePreferencesInput` to `BikePreferences.Builder`
- Handles speed, reluctance, boardCost, walking, parking, rental, optimization

**ScooterPreferencesMapper** (`/mapping/routerequest/ScooterPreferencesMapper.java:8-96`)
- Maps `GraphQLScooterPreferencesInput` to `ScooterPreferences.Builder`
- Handles speed, reluctance, rental, optimization (no parking/walking/boardCost)

### Component 3: Internal Domain Model

**Location**: `/application/src/main/java/org/opentripplanner/routing/api/request/preference/`

#### RoutingPreferences Container

**File**: `RoutingPreferences.java:15-215`

Maintains separate fields for each vehicle type:

```java
private final BikePreferences bike;        // Line 26
private final ScooterPreferences scooter;  // Line 28
```

Separate accessor methods:
```java
public BikePreferences bike() { return bike; }        // Lines 92-94
public ScooterPreferences scooter() { return scooter; } // Lines 100-102
```

Mode-specific rental preference routing (lines 114-121):
```java
public VehicleRentalPreferences rental(TraverseMode mode) {
  return switch (mode) {
    case BICYCLE -> bike.rental();
    case CAR -> car.rental();
    case SCOOTER -> scooter.rental();
    default -> throw new IllegalArgumentException("rental(): Invalid mode " + mode);
  };
}
```

#### BikePreferences

**File**: `BikePreferences.java:23-269`

**Fields**:
- `speed`: Default 5 m/s
- `reluctance`: Default 2.0
- `boardCost`: Cost to board transit with bike
- `parking`: VehicleParkingPreferences
- `rental`: VehicleRentalPreferences
- `optimizeType`: VehicleRoutingOptimizeType
- `optimizeTriangle`: TimeSlopeSafetyTriangle
- `walking`: VehicleWalkingPreferences (for walking the bike)

**Bike-Specific Features**:
- Can board transit (`boardCost`)
- Can be parked (`parking`)
- Can be walked (`walking` with mount/dismount times)

#### ScooterPreferences

**File**: `ScooterPreferences.java:24-202`

**Fields**:
- `speed`: Default 5 m/s
- `reluctance`: Default 2.0
- `rental`: VehicleRentalPreferences
- `optimizeType`: VehicleRoutingOptimizeType
- `optimizeTriangle`: TimeSlopeSafetyTriangle

**Scooter-Specific Characteristics**:
- NO `boardCost` - cannot board transit with scooter
- NO `parking` - scooters use rental only
- NO `walking` - cannot walk a scooter
- Comment at line 20: "Only Scooter rental is supported currently"

#### VehicleRentalPreferences (Shared)

**File**: `VehicleRentalPreferences.java:17-285`

Shared by bike, car, and scooter:

**Fields**:
- `pickupTime`: Default 1 minute
- `pickupCost`: Default 2 minutes cost
- `dropOffTime`: Default 30 seconds
- `dropOffCost`: Default 30 seconds cost
- `useAvailabilityInformation`: Default false
- `arrivingInRentalVehicleAtDestinationCost`: Default 0
- `allowArrivingInRentedVehicleAtDestination`: Default false
- `allowedNetworks`: Set of allowed rental networks
- `bannedNetworks`: Set of banned rental networks

### Component 4: PR #6599 Fix (Already Merged)

**Title**: "Don't use bicycle as street routing mode when car or scooter rental is requested"

**Problem Before Fix**: The A* heuristic used bicycle speed for all non-car modes, causing suboptimal routing for scooter and car rentals.

#### File Modified: EuclideanRemainingWeightHeuristic.java

**Location**: `/street/search/strategy/EuclideanRemainingWeightHeuristic.java:62-74`

**Fixed Method**:
```java
private double getStreetSpeedUpperBound(RoutingPreferences preferences, StreetMode streetMode) {
  // Assume carSpeed > bikeSpeed > walkSpeed
  if (streetMode.includesDriving()) {
    return maxCarSpeed;
  }
  if (streetMode.includesBiking()) {
    return preferences.bike().speed();
  }
  if (streetMode.includesScooter()) {
    return preferences.scooter().speed();  // <-- Key addition
  }
  return preferences.walk().speed();
}
```

**What Changed**: Added explicit check for `includesScooter()` to use `preferences.scooter().speed()` instead of falling through to walk speed or incorrectly using bike speed.

#### File Supporting Fix: StreetMode.java

**Location**: `/routing/api/request/StreetMode.java`

**Relevant Modes**:
- `SCOOTER_RENTAL` (line 36): Includes `Feature.WALKING`, `Feature.SCOOTER`, `Feature.RENTING` - does NOT include `Feature.CYCLING`
- `BIKE_RENTAL` (line 31): Includes `Feature.WALKING`, `Feature.CYCLING`, `Feature.RENTING`
- `CAR_RENTAL` (line 56): Includes `Feature.WALKING`, `Feature.DRIVING`, `Feature.RENTING`

**Feature Query Methods**:
- `includesDriving()` (lines 107-109): Returns true for car modes
- `includesBiking()` (lines 103-105): Returns true for bicycle modes
- `includesScooter()` (lines 111-113): Returns true for scooter modes

**Impact**: The heuristic initialization flow now correctly uses vehicle-specific speeds:
1. `StreetSearchBuilder.initializeHeuristic()` passes the street mode to heuristic
2. `EuclideanRemainingWeightHeuristic.initialize()` calls `getStreetSpeedUpperBound()` with the mode
3. `getStreetSpeedUpperBound()` returns the correct speed based on mode features
4. A* search uses accurate speed estimate, leading to correct path selection

**What Was Fixed by PR #6599**:
1. ✅ GTFS API egress routing: Now uses correct vehicle speed
2. ✅ Access routing heuristic: Now uses correct vehicle speed
3. ❌ TransModel API limitation: Still pending (this is what remains to be fixed)

### Component 5: Routing Algorithm Usage

**Location**: `/street/model/edge/StreetEdge.java`

#### Speed Calculation

**Method**: `calculateSpeed()` (lines 203-224)

```java
public double calculateSpeed(
  RoutingPreferences preferences,
  TraverseMode traverseMode,
  boolean walkingBike
) {
  final double speed = switch (traverseMode) {
    case WALK -> walkingBike
      ? preferences.bike().walking().speed()
      : preferences.walk().speed();
    case BICYCLE -> Math.min(preferences.bike().speed(), getCyclingSpeedLimit());
    case CAR -> getCarSpeed();
    case SCOOTER -> Math.min(preferences.scooter().speed(), getCyclingSpeedLimit());
    case FLEX -> throw new IllegalArgumentException(...);
  };
  return isStairs() ? (speed / preferences.walk().stairsTimeFactor()) : speed;
}
```

**Key Point**: Explicit handling of `BICYCLE` vs `SCOOTER` traverse modes with separate preference lookups.

#### Traversal Cost Calculation

**Method**: `bicycleOrScooterTraversalCost()` (lines 1106-1148)

Unified cost calculation for both bicycle and scooter:

```java
private TraversalCosts bicycleOrScooterTraversalCost(
  RoutingPreferences pref,
  TraverseMode mode,
  double speed
) {
  double time = getEffectiveBikeDistance() / speed;
  double weight;

  // Get optimization type from appropriate preferences
  var optimizeType = mode == TraverseMode.BICYCLE
    ? pref.bike().optimizeType()
    : pref.scooter().optimizeType();

  switch (optimizeType) {
    case TRIANGLE -> {
      // Get triangle from appropriate preferences
      var triangle = mode == TraverseMode.BICYCLE
        ? pref.bike().optimizeTriangle()
        : pref.scooter().optimizeTriangle();
      // ... triangle calculation
    }
    // ... other optimization types
  }

  // Apply reluctance
  var reluctance = StreetEdgeReluctanceCalculator.computeReluctance(pref, mode, false, isStairs());
  weight *= reluctance;
  return new TraversalCosts(time, weight);
}
```

**Key Point**: The routing algorithm explicitly branches on `TraverseMode.BICYCLE` vs `TraverseMode.SCOOTER` to retrieve the appropriate preference values, even though the calculation logic is unified.

## Code References

### TransModel API
- `/application/src/main/java/org/opentripplanner/apis/transmodel/mapping/preferences/ScooterPreferencesMapper.java:15-16` - Reuses `bikeSpeed` and `bicycleOptimisationMethod` for scooter
- `/application/src/main/java/org/opentripplanner/apis/transmodel/mapping/preferences/BikePreferencesMapper.java:17-23` - Uses `bikeSpeed` and `bicycleOptimisationMethod` for bicycle
- `/application/src/main/resources/org/opentripplanner/apis/transmodel/schema.graphql` - GraphQL schema definition

### GTFS GraphQL API
- `/application/src/main/resources/org/opentripplanner/apis/gtfs/schema.graphqls:4025-4044` - BicyclePreferencesInput
- `/application/src/main/resources/org/opentripplanner/apis/gtfs/schema.graphqls:4624-4633` - ScooterPreferencesInput
- `/application/src/main/java/org/opentripplanner/apis/gtfs/mapping/routerequest/BicyclePreferencesMapper.java:19-46` - Bicycle mapper
- `/application/src/main/java/org/opentripplanner/apis/gtfs/mapping/routerequest/ScooterPreferencesMapper.java:10-28` - Scooter mapper

### Domain Model
- `/application/src/main/java/org/opentripplanner/routing/api/request/preference/RoutingPreferences.java:26,28,92-94,100-102,114-121` - Separate bike/scooter fields
- `/application/src/main/java/org/opentripplanner/routing/api/request/preference/BikePreferences.java:27-34` - Bike fields
- `/application/src/main/java/org/opentripplanner/routing/api/request/preference/ScooterPreferences.java:28-32` - Scooter fields
- `/application/src/main/java/org/opentripplanner/routing/api/request/preference/VehicleRentalPreferences.java:20-30` - Shared rental fields

### PR #6599 Fix
- `/application/src/main/java/org/opentripplanner/street/search/strategy/EuclideanRemainingWeightHeuristic.java:62-74` - Fixed heuristic speed selection
- `/application/src/main/java/org/opentripplanner/routing/api/request/StreetMode.java:36,103-105,111-113` - Mode feature definitions

### Routing Algorithm
- `/application/src/main/java/org/opentripplanner/street/model/edge/StreetEdge.java:203-224` - Speed calculation
- `/application/src/main/java/org/opentripplanner/street/model/edge/StreetEdge.java:1106-1148` - Cost calculation
- `/application/src/main/java/org/opentripplanner/street/search/TraverseMode.java:6-8` - BICYCLE and SCOOTER enum values

## Architecture Documentation

### Current TransModel API Limitation

The TransModel API uses a **field reuse pattern** where bicycle field names serve double duty for scooter preferences. This is evidenced by:

1. **ScooterPreferencesMapper explicitly maps bicycle fields to scooter preferences**
2. **GraphQL schema lacks scooter-specific field definitions**
3. **Both mappers read from identical GraphQL argument names**

### Why This Exists

The CLAUDE.md file emphasizes backward compatibility and the project's high code quality expectations. The TransModel API predates separate scooter support, so when scooter functionality was added internally (separate `ScooterPreferences` class), the TransModel API maintained backward compatibility by reusing existing bicycle field names.

### Contrast with GTFS GraphQL API

The GTFS GraphQL API has **proper separation**:
- Separate `BicyclePreferencesInput` and `ScooterPreferencesInput` types
- Distinct field names
- Type-safe code generation via `GraphQLTypes.java`
- No field reuse or fallback

This indicates the GTFS API was either:
1. Created after scooter support was added, or
2. Refactored to add scooter support properly

## Solution Architecture for TransModel API

### Problem Statement

The TransModel API needs native scooter preference parameters while maintaining backward compatibility with existing clients using bicycle field names for scooter routing.

### Recommended Solution

Add scooter-specific fields to the TransModel GraphQL schema and mapper while preserving backward compatibility.

#### Step 1: Extend TransModel GraphQL Schema

**File**: `/application/src/main/resources/org/opentripplanner/apis/transmodel/schema.graphql`

Add new optional scooter preference arguments alongside existing bicycle fields:

```graphql
# New scooter-specific fields (add to trip planning query arguments)
scooterSpeed: Float
scooterReluctance: Float
scooterOptimisationMethod: OptimisationMethod

# Existing bicycle fields (keep for backward compatibility)
bikeSpeed: Float
bicycleOptimisationMethod: OptimisationMethod
walkReluctance: Float
```

#### Step 2: Update ScooterPreferencesMapper

**File**: `/application/src/main/java/org/opentripplanner/apis/transmodel/mapping/preferences/ScooterPreferencesMapper.java`

Implement preference hierarchy: scooter fields → bicycle fields → defaults

```java
public static void mapScooterPreferences(
  ScooterPreferences.Builder scooter,
  DataFetcherDecorator callWith
) {
  // NEW: Try scooter-specific field first, fall back to bicycle field
  callWith.argument("scooterSpeed", scooter::withSpeed);
  if (scooter.speed() == ScooterPreferences.DEFAULT.speed()) {
    // Fallback to bicycle field for backward compatibility
    callWith.argument("bikeSpeed", scooter::withSpeed);
  }

  // NEW: Try scooter-specific optimization, fall back to bicycle
  callWith.argument("scooterOptimisationMethod", scooter::withOptimizeType);
  if (scooter.optimizeType() == ScooterPreferences.DEFAULT.optimizeType()) {
    // Fallback to bicycle field for backward compatibility
    callWith.argument("bicycleOptimisationMethod", scooter::withOptimizeType);
  }

  // NEW: Try scooter-specific reluctance first
  callWith.argument("scooterReluctance", scooter::withReluctance);
  if (scooter.reluctance() == ScooterPreferences.DEFAULT.reluctance()) {
    // Fallback to walkReluctance for backward compatibility
    callWith.argument("walkReluctance", (Double r) -> scooter.withReluctance(r));
  }

  // Triangle factors work for both bicycle and scooter
  if (scooter.optimizeType() == VehicleRoutingOptimizeType.TRIANGLE) {
    scooter.withOptimizeTriangle(triangle -> {
      callWith.argument("triangleFactors.time", triangle::withTime);
      callWith.argument("triangleFactors.slope", triangle::withSlope);
      callWith.argument("triangleFactors.safety", triangle::withSafety);
    });
  }

  // Shared rental preferences mapper
  scooter.withRental(rental -> mapRentalPreferences(rental, callWith));
}
```

#### Step 3: Add Tests

**File**: `/application/src/test/java/org/opentripplanner/apis/transmodel/mapping/preferences/ScooterPreferencesMapperTest.java`

Add test cases for:
1. Scooter-specific fields take precedence
2. Bicycle fields work as fallback
3. Defaults apply when neither is specified
4. Both specified: scooter fields win

```java
@Test
void testScooterFieldTakesPrecedenceOverBikeField() {
  var preferences = ScooterPreferences.of();
  mapScooterPreferences(
    preferences,
    TestDataFetcherDecorator.of(
      Map.of(
        "scooterSpeed", 10.0,
        "bikeSpeed", 5.0  // Should be ignored
      )
    )
  );
  assertEquals(10.0, preferences.build().speed());
}

@Test
void testBikeFieldUsedAsFallback() {
  var preferences = ScooterPreferences.of();
  mapScooterPreferences(
    preferences,
    TestDataFetcherDecorator.of("bikeSpeed", 7.0)
  );
  assertEquals(7.0, preferences.build().speed());
}

@Test
void testDefaultUsedWhenNeitherSpecified() {
  var preferences = ScooterPreferences.of();
  mapScooterPreferences(
    preferences,
    TestDataFetcherDecorator.of(Map.of())
  );
  assertEquals(ScooterPreferences.DEFAULT.speed(), preferences.build().speed());
}
```

#### Step 4: Update Documentation

**File**: `/doc/user/apis/TransmodelApi.md`

Document the new scooter preference fields and note that bicycle fields still work for backward compatibility:

```markdown
### Scooter Preferences

Scooter-specific routing preferences:

- `scooterSpeed` (Float): Maximum scooter speed in m/s. Defaults to 5.0 m/s (~11 mph).
- `scooterReluctance` (Float): Reluctance for scooter routing. Higher values prefer other modes. Defaults to 2.0.
- `scooterOptimisationMethod` (OptimisationMethod): Routing optimization strategy. Options: SAFEST_STREETS, SAFE_STREETS, FLAT_STREETS, SHORTEST_DURATION, TRIANGLE.

**Backward Compatibility**: For backward compatibility, if scooter-specific fields are not provided, the API will fall back to bicycle fields (`bikeSpeed`, `bicycleOptimisationMethod`) for scooter routing. This allows existing clients to continue working unchanged while new clients can use scooter-specific fields for clearer semantics.

**Recommendation**: New integrations should use scooter-specific fields (`scooterSpeed`, etc.) for scooter routing to improve code clarity and avoid confusion.
```

### Why This Solution Works

1. **Backward Compatible**: Existing clients specifying `bikeSpeed` for scooter mode continue working
2. **Forward Compatible**: New clients can use explicit `scooterSpeed` fields
3. **Clear Semantics**: Makes intention explicit (configuring scooter, not bicycle)
4. **Matches GTFS API**: Brings TransModel API in line with GTFS API's separation
5. **Simple Migration**: Clients can migrate at their own pace
6. **No Breaking Changes**: Zero impact on existing deployments

### Migration Path for API Clients

**Phase 1 (Current)**: Clients specify `bikeSpeed` for scooter mode (fallback behavior)

```graphql
query {
  trip(
    modes: [{ mode: SCOOTER }]
    bikeSpeed: 5.0  # Used for scooter because no scooterSpeed specified
  ) { ... }
}
```

**Phase 2 (After Fix)**: Clients can optionally specify `scooterSpeed` for clarity

```graphql
query {
  trip(
    modes: [{ mode: SCOOTER }]
    scooterSpeed: 5.0  # Explicit scooter preference
    bikeSpeed: 7.0     # Ignored for scooter mode, used only for bicycle mode
  ) { ... }
}
```

**Phase 3 (Future)**: Consider deprecating bicycle field fallback after migration period

Add deprecation notice to documentation:
```markdown
**Deprecated**: Using `bikeSpeed` for scooter mode is deprecated. Use `scooterSpeed` instead.
```

### Alternative Solutions Considered

#### Alternative 1: Breaking Change - Remove Fallback

**Approach**: Require all scooter requests to specify scooter fields, throw error if using bicycle fields for scooter mode.

**Pros**:
- Cleaner semantics
- Forces proper usage

**Cons**:
- **Breaking change** - violates OTP project guidelines
- Requires coordinated migration of all clients
- High deployment risk

**Verdict**: ❌ Not recommended due to backward compatibility requirements

#### Alternative 2: Mode-Specific Argument Nesting

**Approach**: Nest preferences under mode-specific objects:

```graphql
bicycle: {
  speed: 7.0
  optimisationMethod: SAFE_STREETS
}
scooter: {
  speed: 5.0
  optimisationMethod: SAFE_STREETS
}
```

**Pros**:
- Very clear separation
- Matches GTFS API structure

**Cons**:
- **Major schema restructure** - significant breaking change
- Requires rewriting preference mappers
- High migration cost for clients

**Verdict**: ❌ Too disruptive, doesn't meet backward compatibility requirement

#### Alternative 3: Keep Current Behavior (Do Nothing)

**Approach**: Document that `bikeSpeed` should be used for scooter speed in TransModel API.

**Pros**:
- Zero implementation effort
- No compatibility issues

**Cons**:
- Confusing API semantics
- Inconsistent with GTFS API
- Doesn't solve issue #6572
- Poor developer experience

**Verdict**: ❌ Doesn't address the problem

## Testing Strategy

### Unit Tests

**File**: `ScooterPreferencesMapperTest.java`

Test matrix:

| scooterSpeed | bikeSpeed | Expected Result |
|--------------|-----------|-----------------|
| 10.0 | 5.0 | 10.0 (scooter wins) |
| null | 7.0 | 7.0 (bike fallback) |
| null | null | 5.0 (default) |
| 0.0 | 5.0 | 0.0 (explicit zero) |

### Integration Tests

**File**: `TransmodelGraphQLIntegrationTest.java` (create if needed)

Test scenarios:
1. Trip request with scooter mode and `scooterSpeed` uses scooter speed
2. Trip request with scooter mode and `bikeSpeed` (no `scooterSpeed`) uses bike speed as fallback
3. Trip request with bicycle mode and `bikeSpeed` uses bike speed (not affected by scooter fields)
4. Trip request with both modes and both speed fields uses appropriate speed for each mode

### Regression Tests

Verify PR #6599 fix still works:
1. Scooter rental mode uses scooter preferences in heuristic (not bike)
2. Access egress routing calculates correctly with scooter speed
3. Routing produces different paths for different scooter speeds

## Related Issues and Pull Requests

- **Issue #6572**: "Scooter and Bike Rental Speed Configuration" - https://github.com/opentripplanner/OpenTripPlanner/issues/6572
- **PR #6599**: "Don't use bicycle as street routing mode when car or scooter rental is requested" - Fixed GTFS API issues, merged

## Conclusion

Issue #6572 is 2/3 solved by PR #6599. The remaining 1/3 is adding native scooter preference parameters to the TransModel API. The recommended solution maintains backward compatibility by implementing a preference hierarchy (scooter-specific → bicycle fallback → default) while providing clear semantics for new clients. This approach aligns with OTP's project guidelines emphasizing backward compatibility and high code quality standards.

The solution requires:
1. Adding ~3 new optional fields to TransModel GraphQL schema
2. Updating ScooterPreferencesMapper with fallback logic (~20 lines)
3. Adding comprehensive test coverage (~5 test cases)
4. Updating API documentation

This represents a low-risk, high-value fix that resolves the issue without breaking existing deployments.
