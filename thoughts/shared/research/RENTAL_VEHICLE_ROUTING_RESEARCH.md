# Research: Rental Vehicle Routing - Electric vs Non-Electric Vehicle Speed Handling

**Date**: 2025-11-06 07:52:19 UTC
**Researcher**: testower
**Git Commit**: ade1406fff6651e7b4fdb6167a40e1ba7a5ed031
**Branch**: feature/transmodel-api-scooter-preferences
**Repository**: OpenTripPlanner

## Research Question

How does OpenTripPlanner currently handle routing for different types of rental vehicles (regular bikes, electric bikes, electric scooters), and why do all rental vehicles appear to use routing rules designed for regular (non-motorized) bikes? Specifically:

1. Do electric bikes get penalized on uphills when they shouldn't?
2. Do electric scooters maintain constant speed regardless of terrain?
3. Is the `propulsionType` field (HUMAN, ELECTRIC_ASSIST, ELECTRIC) used in routing calculations?

## Summary

**Current Implementation**: All rental vehicles with the same `formFactor` (e.g., BICYCLE, SCOOTER) use identical routing rules regardless of their `propulsionType` (HUMAN vs ELECTRIC_ASSIST vs ELECTRIC). The propulsion type is stored in the data model but **completely ignored during routing**.

**Key Finding**: Electric bikes and regular bikes both suffer the same uphill penalties. Electric scooters and human-powered bicycles use the same slope-based speed calculations. This creates suboptimal routing for motorized rental vehicles.

**Root Cause**: The routing system maps rental vehicles to traverse modes based solely on form factor (RentalFormFactor → TraverseMode), and traverse modes determine speed/cost calculations. Propulsion type is never consulted.

## Detailed Findings

### 1. Vehicle Type Data Model

**Location**: `application/src/main/java/org/opentripplanner/service/vehiclerental/model/RentalVehicleType.java`

OTP stores rental vehicles with two classification fields:

```java
public final class RentalVehicleType {
  private final RentalFormFactor formFactor;      // BICYCLE, SCOOTER, CAR, etc.
  private final PropulsionType propulsionType;    // HUMAN, ELECTRIC_ASSIST, ELECTRIC, etc.

  public enum PropulsionType {
    HUMAN,
    ELECTRIC_ASSIST,
    ELECTRIC,
    COMBUSTION,
    COMBUSTION_DIESEL,
    HYBRID,
    PLUG_IN_HYBRID,
    HYDROGEN_FUEL_CELL,
  }
}
```

**Key Observation**: Both fields are stored and exposed via GraphQL APIs, but only `formFactor` is used for routing decisions.

### 2. Form Factor to Traverse Mode Mapping

**Location**: `application/src/main/java/org/opentripplanner/street/model/RentalFormFactor.java:5-20`

```java
public enum RentalFormFactor {
  BICYCLE(TraverseMode.BICYCLE),
  CARGO_BICYCLE(TraverseMode.BICYCLE),
  CAR(TraverseMode.CAR),
  MOPED(TraverseMode.BICYCLE),           // ← Uses BICYCLE traverse mode!
  SCOOTER(TraverseMode.SCOOTER),
  SCOOTER_STANDING(TraverseMode.SCOOTER),
  SCOOTER_SEATED(TraverseMode.SCOOTER),
  OTHER(TraverseMode.BICYCLE);

  public final TraverseMode traverseMode;
}
```

**Key Observation**:
- All bicycle-like vehicles → TraverseMode.BICYCLE
- All scooters → TraverseMode.SCOOTER
- No distinction between electric and non-electric

### 3. Speed Calculation

**Location**: `application/src/main/java/org/opentripplanner/street/model/edge/StreetEdge.java:203-224`

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
    case FLEX -> throw new IllegalArgumentException("getSpeed(): Invalid mode");
  };

  return isStairs() ? (speed / preferences.walk().stairsTimeFactor()) : speed;
}
```

**Key Observations**:
- Bicycle speed: Limited by `getCyclingSpeedLimit()` which is based on car speed
- Scooter speed: **Same pattern** - also limited by `getCyclingSpeedLimit()`
- Both are capped by the street's car speed limit
- No propulsion type consideration

**Cycling Speed Limit** (`StreetEdge.java:562-566`):

```java
private double getCyclingSpeedLimit() {
  return hasElevationExtension()
    ? getCarSpeed() * (elevationExtension.getEffectiveBikeDistance() / getDistanceMeters())
    : getCarSpeed();
}
```

**Critical Issue**: The speed is adjusted proportionally to `effectiveBikeDistance`, which includes elevation penalties calculated for human-powered bikes!

### 4. Elevation Impact on Speed and Cost

**Location**: `application/src/main/java/org/opentripplanner/routing/util/ElevationUtils.java:95-162`

The `getSlopeCosts()` method calculates elevation-adjusted factors:

```java
// Line 135-139: Energy calculation for uphills
double energy = hypotenuse *
  (ENERGY_PER_METER_ON_FLAT + ENERGY_SLOPE_FACTOR * slope³);

// Line 140-141: Slope speed coefficient
double slopeSpeedCoef = slopeSpeedCoefficient(slope, coordinates[i].y);
slopeSpeedEffectiveLength += run / slopeSpeedCoef;
```

**The slopeSpeedCoefficient function** is a B-spline approximation based on human cycling physics from http://www.analyticcycling.com/ForcesSpeed_Page.html.

**Constants**:
- `ENERGY_PER_METER_ON_FLAT = 1.0` (ElevationUtils.java:20)
- `ENERGY_SLOPE_FACTOR = 4000` (ElevationUtils.java:22)

**Formula**: Energy cost ∝ slope³ for positive slopes (uphills)

**Result**: Produces multipliers like:
- 5% grade: ~1.15x effective distance
- 10% grade: ~1.35x effective distance
- 15% grade: ~1.65x effective distance

**Problem**: These calculations are based on human muscle power physics and don't apply to electric motors!

### 5. Traversal Cost Calculation

**Location**: `application/src/main/java/org/opentripplanner/street/model/edge/StreetEdge.java:1106-1148`

```java
private TraversalCosts bicycleOrScooterTraversalCost(
  RoutingPreferences pref,
  TraverseMode mode,
  double speed
) {
  double time = getEffectiveBikeDistance() / speed;  // ← Uses elevation-adjusted distance!
  double weight;
  var optimizeType = mode == TraverseMode.BICYCLE
    ? pref.bike().optimizeType()
    : pref.scooter().optimizeType();

  switch (optimizeType) {
    case FLAT_STREETS -> weight = getEffectiveBikeDistanceForWorkCost() / speed;
    case SHORTEST_DURATION -> weight = getEffectiveBikeDistance() / speed;
    case TRIANGLE -> {
      double quick = getEffectiveBikeDistance();
      double slope = getEffectiveBikeDistanceForWorkCost();  // ← Energy-based!
      var triangle = mode == TraverseMode.BICYCLE
        ? pref.bike().optimizeTriangle()
        : pref.scooter().optimizeTriangle();
      weight = quick * triangle.time() + slope * triangle.slope() + ...;
    }
    // ... other cases
  }

  return new TraversalCosts(time, weight);
}
```

**Key Issues**:

1. **Time calculation**: Uses `getEffectiveBikeDistance()` which includes slope penalties
2. **FLAT_STREETS optimization**: Uses `getEffectiveBikeDistanceForWorkCost()` which is based on human energy expenditure (slope³ formula)
3. **TRIANGLE optimization**: Includes slope factor in weight calculation
4. **No propulsion type checking**: Same logic for all bikes and scooters

### 6. Current Speed Defaults

**Bicycle** (`BikePreferences.java:37`):
- Default: 5 m/s (~11 mph)
- Reluctance: 2.0

**Scooter** (`ScooterPreferences.java:35`):
- Default: 5 m/s (~11 mph)
- Reluctance: 2.0

**Problem**: Same default speed for electric and non-electric vehicles!

### 7. What Works vs What Doesn't

**What Works**:
- GBFS imports vehicle type data correctly (including propulsionType)
- GraphQL APIs expose propulsionType to clients
- Form factor filtering works (can't rent a scooter for bike routing)
- User can set custom bike/scooter speeds via preferences

**What Doesn't Work**:
- Propulsion type is never used in routing logic
- Electric bikes penalized on uphills like regular bikes
- Electric scooters penalized on uphills like bicycles
- No way to specify different speeds for electric vs non-electric
- Slope-based "work cost" calculations inappropriate for electric vehicles

## Code References

### Critical Files:
- `RentalFormFactor.java:5-20` - Maps form factor to traverse mode (no propulsion consideration)
- `StreetEdge.java:217-219` - Speed calculation (same for all bikes/scooters)
- `StreetEdge.java:562-566` - Cycling speed limit with elevation adjustment
- `StreetEdge.java:1106-1148` - Traversal cost (uses human-powered physics)
- `ElevationUtils.java:135-141` - Elevation energy calculation (slope³ formula)
- `RentalVehicleType.java:101-103` - Propulsion type accessor (unused in routing)

### Data Flow:
1. GBFS feed → `GbfsVehicleTypeMapper` → `RentalVehicleType` (with formFactor + propulsionType)
2. Routing request → `StreetModeToFormFactorMapper` → `RentalFormFactor`
3. `RentalFormFactor.traverseMode` → `TraverseMode.BICYCLE` or `.SCOOTER`
4. `StreetEdge.calculateSpeed(traverseMode)` → Uses bike/scooter preferences only
5. `StreetEdge.bicycleOrScooterTraversalCost()` → Applies elevation penalties

**Missing Link**: No code path from `propulsionType` to routing logic.

## Architecture Documentation

### Current Pattern:

```
RentalVehicleType (formFactor, propulsionType)
         ↓
    formFactor only
         ↓
   RentalFormFactor
         ↓
   TraverseMode (BICYCLE or SCOOTER)
         ↓
   Speed/Cost Calculation (ignores propulsionType)
```

### What's Missing:

The system needs one or more of these approaches (see detailed analysis in follow-up research sections below):

**Option A**: New Traverse Modes (type system change)
```
BICYCLE + HUMAN → TraverseMode.BICYCLE
BICYCLE + ELECTRIC → TraverseMode.EBIKE (new)
SCOOTER + ELECTRIC → TraverseMode.ESCOOTER (new)
```
Requires 14+ switch statement updates. Clean separation but combinatorial explosion risk.

**Option B**: Propulsion-Aware Cost Calculation (runtime change) ⭐ RECOMMENDED
```
Keep existing traverse modes, but thread PropulsionType through State
and check it in bicycleOrScooterTraversalCost()
```
Minimal changes (~80 lines), immediate improvement. See "Option B Deep Dive" section below.

**Option C**: Separate Preference Sets (configuration change)
```
BikePreferences, EbikePreferences, ScooterPreferences
with different default speeds and slope sensitivity
```
Adds user configurability. Can be combined with Option A or B.

**Option D**: TraverseMode as Record (architecture change)
```
record TraverseMode(ModeType type, PropulsionType propulsion) {
  enum ModeType { WALK, BICYCLE, SCOOTER, CAR, FLEX }
}
```
Most flexible, proper OOP composition. Major refactor (16+ switches, EnumSet, serialization). See "Option C Deep Dive" section below (note: labeled "Option C" in that section but renamed to "Option D" for consistency).

## Impact Analysis

### Electric Bikes (formFactor=BICYCLE, propulsionType=ELECTRIC_ASSIST or ELECTRIC)

**Current Behavior**:
- 10% uphill: ~1.35x time penalty (speed reduced proportionally)
- 10% downhill: Similar benefit as regular bike
- Routes avoid steep hills even though motor provides assistance

**Expected Behavior**:
- 10% uphill: Minimal penalty (motor compensates)
- 10% downhill: Less benefit (can't pedal faster than motor limit)
- Routes should not penalize moderate hills

**Real-World Example**:
- Route with 100m at 10% grade
- Regular bike: ~70 seconds (5 m/s reduced to ~3.7 m/s)
- E-bike in OTP: ~70 seconds (same penalty!)
- E-bike in reality: ~35 seconds (maintains 5+ m/s with motor assist)

### Electric Scooters (formFactor=SCOOTER, propulsionType=ELECTRIC)

**Current Behavior**:
- Uses TraverseMode.SCOOTER
- Same elevation logic as bicycles
- Penalized on uphills

**Expected Behavior**:
- Constant speed (limited by motor and battery)
- Slight slowdown on very steep hills only
- Routes should prefer flat routes only for safety/comfort, not speed

**Note**: Most e-scooters are speed-limited (20-25 km/h) and maintain that speed on moderate inclines.

## Historical Context

**GBFS v2.1** (2019) introduced the `vehicle_types.json` file with:
- `form_factor`: bicycle, cargo_bicycle, scooter, moped, car, other
- `propulsion_type`: human, electric_assist, electric, combustion, etc.

**OTP Implementation**: Added GBFS v2 support with full data model but routing logic was never updated to differentiate by propulsion type.

**Similar Issue**: Mopeds use TraverseMode.BICYCLE (RentalFormFactor.java:9) even though they're motorized!

## Related Files and Components

### Data Import:
- `GbfsVehicleTypeMapper.java` (v2 and v3) - Correctly imports propulsion type
- `GbfsFreeVehicleStatusMapper.java` - Maps vehicles to types
- `VehicleRentalStation.java` - Stores available vehicle types

### API Exposure:
- GraphQL schema exposes `propulsionType` field
- Transmodel API includes vehicle type queries
- Clients can see propulsion type but routing ignores it

### Tests:
- `BikeRentalTest.java` - Integration test (doesn't test propulsion differences)
- `StreetEdgeTest.java` - Speed calculation tests (all assume one bike type)
- No tests for electric vs non-electric routing differences

## Recommendations for GitHub Issue

The research shows a clear gap between the data model (which supports propulsion types) and the routing logic (which ignores them). A GitHub issue should:

1. **Describe the problem**: Electric vehicles penalized like human-powered ones
2. **Show the impact**: Specific examples (e-bike uphill routing)
3. **Identify root cause**: Propulsion type unused in speed/cost calculations
4. **Provide technical details**: Reference the key files and logic
5. **Suggest approaches**: Multiple implementation options (new traverse modes vs modified cost functions)
6. **Consider scope**: Could be core feature or Sandbox extension initially

## Open Questions

1. Should electric bike speed be adjustable independent of regular bike speed?
2. Should `maxRangeMeters` from vehicle type be enforced as a routing constraint?
3. Do cargo e-bikes need different treatment than regular e-bikes?
4. Should mopeds have their own traverse mode instead of using BICYCLE?
5. How should battery drain be modeled (constant, elevation-dependent, speed-dependent)?

---

## Follow-up Research: TraverseMode Deep Dive (2025-11-26)

**Date**: 2025-11-26T21:04:46+0000
**Researcher**: testower
**Git Commit**: bf2b9ac8524b4384785836f43669e6ee3aad0ee9
**Branch**: dev-2.x

### Research Question

What is the TraverseMode system in detail, and how would adding new traverse modes (like EBIKE, ESCOOTER) work in the current architecture? This follow-up explores the traverse mode option mentioned in "Option A: Propulsion-aware traverse modes" from the original research.

### Summary

TraverseMode is a core enum in OTP's street routing system with five values: WALK, BICYCLE, SCOOTER, CAR, and FLEX. It flows through the entire routing process, affecting:
- Edge traversal permissions
- Speed calculations
- Cost/weight calculations
- Intersection handling
- State transitions during vehicle rental/parking

The enum is defined in `TraverseMode.java` with helper methods for mode classification (`isCyclingIsh()`, `isInCar()`, etc.). Adding new traverse modes would require modifications to:
1. The TraverseMode enum itself
2. All switch statements that branch on TraverseMode (14+ locations)
3. Preference classes to support mode-specific settings
4. RentalFormFactor mappings to select the appropriate mode

### TraverseMode Enum Definition

**Location**: `application/src/main/java/org/opentripplanner/street/search/TraverseMode.java:5-32`

```java
public enum TraverseMode {
  WALK,      // Pedestrian movement
  BICYCLE,   // Cycling (human-powered or electric - no distinction)
  SCOOTER,   // Scooter riding (also no electric vs non-electric)
  CAR,       // Driving
  FLEX;      // Flexible/on-demand transit

  private static final EnumSet<TraverseMode> STREET_MODES =
    EnumSet.of(WALK, BICYCLE, SCOOTER, CAR);

  public boolean isOnStreetNonTransit() {
    return STREET_MODES.contains(this);
  }

  public boolean isInCar() {
    return this == CAR;
  }

  public boolean isCyclingIsh() {
    return this == BICYCLE || this == SCOOTER;
  }

  public boolean isWalking() {
    return this == WALK;
  }
}
```

**Key Observations**:
- Currently 5 modes, with BICYCLE and SCOOTER treated similarly in many contexts
- FLEX mode excluded from STREET_MODES set
- Helper methods group modes by behavior
- No built-in concept of electric vs non-electric

### How TraverseMode Flows Through Routing

#### 1. State Initialization

**Location**: `application/src/main/java/org/opentripplanner/street/search/state/StateData.java:58-66`

The initial TraverseMode is determined by StreetMode:

```java
currentMode = switch (requestMode) {
  // Rental modes start walking until vehicle is picked up
  case WALK, BIKE_RENTAL, SCOOTER_RENTAL, CAR_RENTAL, FLEXIBLE -> TraverseMode.WALK;
  // Owned bike modes start cycling
  case BIKE, BIKE_TO_PARK -> TraverseMode.BICYCLE;
  // Owned car modes start driving
  case CAR, CAR_TO_PARK, CAR_PICKUP, CAR_HAILING -> TraverseMode.CAR;
};
```

#### 2. Mode Transitions During Rental

**Location**: `application/src/main/java/org/opentripplanner/street/search/state/StateEditor.java`

When picking up a rental vehicle (lines 262-303):
```java
// Forward search: switch to vehicle mode
child.stateData.currentMode = formFactor.traverseMode;

// Reverse search: switch back to walking
child.stateData.currentMode = TraverseMode.WALK;
```

The `formFactor.traverseMode` comes from `RentalFormFactor.java`:
```java
BICYCLE(TraverseMode.BICYCLE),
SCOOTER(TraverseMode.SCOOTER),
CAR(TraverseMode.CAR),
// etc.
```

#### 3. Speed Calculation

**Location**: `application/src/main/java/org/opentripplanner/street/model/edge/StreetEdge.java:214-222`

```java
final double speed = switch (traverseMode) {
  case WALK -> walkingBike
    ? preferences.bike().walking().speed()
    : preferences.walk().speed();
  case BICYCLE -> Math.min(preferences.bike().speed(), getCyclingSpeedLimit());
  case CAR -> getCarSpeed();
  case SCOOTER -> Math.min(preferences.scooter().speed(), getCyclingSpeedLimit());
  case FLEX -> throw new IllegalArgumentException("getSpeed(): Invalid mode");
};
```

**Critical**: Both BICYCLE and SCOOTER use the same `getCyclingSpeedLimit()` which applies elevation-based speed reductions.

#### 4. Cost Calculation Dispatch

**Location**: `application/src/main/java/org/opentripplanner/street/model/edge/StreetEdge.java:993-1003`

```java
var traversalCosts = switch (traverseMode) {
  case BICYCLE, SCOOTER -> bicycleOrScooterTraversalCost(request, traverseMode, speed);
  case WALK -> walkingTraversalCosts(request, traverseMode, speed, walkingBike, wheelchairEnabled);
  default -> otherTraversalCosts(request, traverseMode, walkingBike, speed);
};
```

BICYCLE and SCOOTER share the same cost calculation logic, which includes:
- Elevation-adjusted distances
- "Work cost" based on slope³ formula
- Mode-specific optimization types (FLAT_STREETS, TRIANGLE, etc.)

#### 5. Reluctance Calculation

**Location**: `application/src/main/java/org/opentripplanner/street/model/edge/StreetEdgeReluctanceCalculator.java:24-34`

```java
return switch (traverseMode) {
  case WALK -> walkingBike
    ? computeBikeWalkingReluctance(pref.bike().walking(), edgeIsStairs)
    : computeWalkReluctance(pref.walk(), edgeIsStairs);
  case BICYCLE -> pref.bike().reluctance();
  case CAR -> pref.car().reluctance();
  case SCOOTER -> pref.scooter().reluctance();
  default -> throw new IllegalArgumentException("getReluctance(): Invalid mode");
};
```

Each mode has separate reluctance preferences.

#### 6. Permission Checking

**Location**: `application/src/main/java/org/opentripplanner/street/model/StreetTraversalPermission.java:77-83`

```java
public boolean allows(TraverseMode mode) {
  if (mode == TraverseMode.WALK && allows(StreetTraversalPermission.PEDESTRIAN)) {
    return true;
  } else if (mode.isCyclingIsh() && allows(StreetTraversalPermission.BICYCLE)) {
    return true;
  } else {
    return mode == TraverseMode.CAR && allows(StreetTraversalPermission.CAR);
  }
}
```

**Critical**: Uses `isCyclingIsh()` which currently treats BICYCLE and SCOOTER identically.

### All Switch Statements on TraverseMode

Adding new traverse modes would require updating these locations:

1. **Speed calculation** - `StreetEdge.java:214-222`
2. **Traversal cost dispatch** - `StreetEdge.java:993-1003`
3. **Reluctance calculation** - `StreetEdgeReluctanceCalculator.java:24-34`
4. **Intersection mode reluctance** - `StreetEdge.java:1058-1066`
5. **Intersection traversal dispatch** - `SimpleIntersectionTraversalCalculator.java:34-40`
6. **No-thru-traffic check** - `StreetEdge.java:194-198`
7. **Initial state mode selection** - `StateData.java:59-66`
8. **Rental preferences accessor** - `RoutingPreferences.java:119-125`
9. **Parking preferences accessor** - `RoutingPreferences.java:112-115`

Plus numerous if/else conditionals checking specific modes.

### RentalFormFactor to TraverseMode Mapping

**Location**: `application/src/main/java/org/opentripplanner/street/model/RentalFormFactor.java:5-20`

```java
public enum RentalFormFactor {
  BICYCLE(TraverseMode.BICYCLE),
  CARGO_BICYCLE(TraverseMode.BICYCLE),
  CAR(TraverseMode.CAR),
  MOPED(TraverseMode.BICYCLE),           // Mopeds use BICYCLE mode!
  SCOOTER(TraverseMode.SCOOTER),
  SCOOTER_STANDING(TraverseMode.SCOOTER),
  SCOOTER_SEATED(TraverseMode.SCOOTER),
  OTHER(TraverseMode.BICYCLE);

  public final TraverseMode traverseMode;

  RentalFormFactor(TraverseMode traverseMode) {
    this.traverseMode = traverseMode;
  }
}
```

**Current Limitations**:
- Each RentalFormFactor maps to exactly one TraverseMode
- No way to map based on propulsionType
- MOPED (motorized) uses BICYCLE traverse mode

### Preference Classes

Each TraverseMode (except FLEX) has corresponding preference classes:

- **WALK** → `WalkPreferences.java` - speed, reluctance, stairs settings
- **BICYCLE** → `BikePreferences.java` - speed, reluctance, optimization type, triangle weights
- **SCOOTER** → `ScooterPreferences.java` - speed, reluctance, optimization type, triangle weights
- **CAR** → `CarPreferences.java` - speed, reluctance, parking settings

**Location**: `application/src/main/java/org/opentripplanner/routing/api/request/preference/`

Each preference class includes:
- Base speed (m/s)
- Reluctance multiplier
- Mode-specific settings (e.g., bike safety factors, car parking time)
- Sub-preferences (e.g., bike.walking(), bike.parking(), bike.rental())

### Impact of Adding New Traverse Modes (e.g., EBIKE)

#### Required Changes

**1. TraverseMode Enum** (`TraverseMode.java`)
Add new values:
```java
public enum TraverseMode {
  WALK,
  BICYCLE,
  EBIKE,        // New
  SCOOTER,
  ESCOOTER,     // New
  CAR,
  FLEX;
}
```

Update helper methods:
```java
public boolean isCyclingIsh() {
  return this == BICYCLE || this == EBIKE || this == SCOOTER || this == ESCOOTER;
}

public boolean isElectric() {  // New helper
  return this == EBIKE || this == ESCOOTER;
}
```

**2. RentalFormFactor Mapping** (`RentalFormFactor.java`)

Problem: Need to select TraverseMode based on both formFactor AND propulsionType.

Current approach (one-to-one mapping):
```java
BICYCLE(TraverseMode.BICYCLE),
```

Would need something like:
```java
public TraverseMode getTraverseMode(PropulsionType propulsionType) {
  return switch(this) {
    case BICYCLE -> propulsionType.isElectric()
      ? TraverseMode.EBIKE
      : TraverseMode.BICYCLE;
    case SCOOTER -> TraverseMode.ESCOOTER;  // Most scooters are electric
    // ...
  };
}
```

But this requires access to PropulsionType, which currently isn't available at this layer.

**3. Speed Calculation** (`StreetEdge.java:214-222`)

Add cases for new modes:
```java
final double speed = switch (traverseMode) {
  case WALK -> walkingBike ? ... : ...;
  case BICYCLE -> Math.min(preferences.bike().speed(), getCyclingSpeedLimit());
  case EBIKE -> Math.min(preferences.ebike().speed(), getCyclingSpeedLimit());  // New
  case SCOOTER -> Math.min(preferences.scooter().speed(), getCyclingSpeedLimit());
  case ESCOOTER -> Math.min(preferences.escooter().speed(), getCarSpeed());  // New - no elevation penalty
  case CAR -> getCarSpeed();
  case FLEX -> throw new IllegalArgumentException(...);
};
```

**4. Cost Calculation** (`StreetEdge.java:993-1003`)

Decide whether electric modes use different cost calculations:
```java
var traversalCosts = switch (traverseMode) {
  case BICYCLE -> bicycleTraversalCost(...);
  case EBIKE -> ebikeTraversalCost(...);  // Different slope sensitivity
  case SCOOTER -> scooterTraversalCost(...);
  case ESCOOTER -> escooterTraversalCost(...);  // Constant speed, minimal slope impact
  case WALK -> walkingTraversalCosts(...);
  default -> otherTraversalCosts(...);
};
```

**5. Preference Classes**

Create new preference classes:
- `EbikePreferences.java` - with different default speed, slope sensitivity
- `EscooterPreferences.java` - with constant speed model

Add to `RoutingPreferences.java`:
```java
private final EbikePreferences ebike;
private final EscooterPreferences escooter;

public VehicleRentalPreferences rental(TraverseMode mode) {
  return switch (mode) {
    case BICYCLE -> bike.rental();
    case EBIKE -> ebike.rental();
    case CAR -> car.rental();
    case SCOOTER -> scooter.rental();
    case ESCOOTER -> escooter.rental();
    default -> throw new IllegalArgumentException(...);
  };
}
```

**6. State Data and Initialization** (`StateData.java`)

Update initial mode selection:
```java
// Would need a way to know if rental is electric at initialization time
// This is tricky because we don't know which specific vehicle until routing
```

**7. Permission System** (`StreetTraversalPermission.java`)

Update permission checking:
```java
public boolean allows(TraverseMode mode) {
  if (mode == TraverseMode.WALK && allows(PEDESTRIAN)) {
    return true;
  } else if (mode.isCyclingIsh() && allows(BICYCLE)) {
    return true;  // EBIKE and ESCOOTER would use bike lanes
  } else {
    return mode == TraverseMode.CAR && allows(CAR);
  }
}
```

**8. Update All Other Switch Statements**

At minimum, 9 more locations need cases for EBIKE and ESCOOTER.

### Architectural Challenge: Dynamic Mode Selection

The biggest challenge with the traverse mode approach is **when to select the mode**:

**Current Flow**:
```
StreetMode (BIKE_RENTAL)
  → RentalFormFactor (BICYCLE)
  → TraverseMode (BICYCLE)
```

**Needed Flow**:
```
StreetMode (BIKE_RENTAL)
  → RentalFormFactor (BICYCLE) + PropulsionType (ELECTRIC)
  → TraverseMode (EBIKE)
```

**Problem**: The RentalFormFactor enum is selected at request parsing time, before we know which specific vehicle will be rented. The PropulsionType is only known when we reach a specific rental edge with specific vehicles available.

**Possible Solutions**:

**Option 1**: Late binding - determine TraverseMode when picking up vehicle
- Modify `StateEditor.beginVehicleRentingAtStation()` to select mode based on propulsion type
- Pass PropulsionType through VehicleRentalEdge to StateEditor
- Most flexible but requires plumbing PropulsionType through several layers

**Option 2**: Create form factors per propulsion type
- Add EBICYCLE, ESCOOTER form factors
- Map in GBFS import: (BICYCLE, ELECTRIC) → EBICYCLE form factor
- Simpler but proliferates form factors

**Option 3**: Make TraverseMode selection dynamic
- Add `getTraverseMode(PropulsionType)` method to RentalFormFactor
- Call at state transition time with actual vehicle's propulsion type
- Requires threading PropulsionType through state transitions

### Comparison: New Traverse Modes vs Other Approaches

**Option A: New Traverse Modes** (this research)

Pros:
- Clean separation of electric vs non-electric
- Each mode has distinct speed/cost calculations
- Natural fit for mode-specific preferences
- Permissions and state tracking work the same way

Cons:
- Extensive changes required (9+ switch statements)
- Need to solve dynamic mode selection problem
- More preference classes to maintain
- May need EBIKE, ESCOOTER, EMOPED, ECARGO_BIKE, etc.

**Option B: Propulsion-Aware Cost Calculation** (alternative)

Check propulsion type inside cost functions:
```java
private TraversalCosts bicycleOrScooterTraversalCost(...) {
  double slopeFactor = isElectric(currentVehicle) ? 1.0 : getSlopeMultiplier();
  // ...
}
```

Pros:
- Fewer code changes
- No new traverse modes needed
- Easier to add propulsion type to cost calculation

Cons:
- Need to thread PropulsionType through State
- Mixed concerns (mode + propulsion in same function)
- Harder to have completely different cost models

**Option C: Subtype TraverseMode** (alternative)

Make TraverseMode contain propulsion info:
```java
record TraverseMode(ModeType type, PropulsionType propulsion) {
  enum ModeType { WALK, BICYCLE, SCOOTER, CAR, FLEX }
}
```

Pros:
- Rich mode representation
- Can distinguish any propulsion type
- Flexible for future vehicle types

Cons:
- Major refactor (TraverseMode is an enum everywhere)
- All switch statements would need rewriting
- May be over-engineered

### Related Code References

**TraverseMode Core**:
- `TraverseMode.java:5-32` - Enum definition
- `TraverseModeSet.java:18-153` - Bitwise mode collections
- `State.java:321-323` - Current mode accessor
- `StateData.java:38-43` - Mode storage fields

**Mode Selection**:
- `RentalFormFactor.java:5-20` - Form factor to mode mapping
- `StreetModeToFormFactorMapper.java:11-29` - Request mode to form factor
- `StreetModeToRentalTraverseModeMapper.java:11-26` - Request mode to traverse mode
- `StateData.java:58-66` - Initial mode from StreetMode

**Mode-Based Branching**:
- `StreetEdge.java:214-222, 993-1003, 1058-1066` - Speed, cost, reluctance switches
- `StreetEdgeReluctanceCalculator.java:24-34` - Reluctance switch
- `StreetTraversalPermission.java:77-83` - Permission checking
- `SimpleIntersectionTraversalCalculator.java:34-40` - Intersection handling

**Mode Transitions**:
- `StateEditor.java:262-303` - Rental pickup/dropoff mode changes
- `VehicleRentalEdge.java:56-146` - Rental edge traversal with mode validation
- `BikeWalkableEdge.java:52-64` - Bike/walk mode switching

**Preferences**:
- `RoutingPreferences.java:112-125` - Mode to preferences mapping
- `BikePreferences.java`, `ScooterPreferences.java`, `CarPreferences.java`, `WalkPreferences.java` - Mode-specific preference classes

### Conclusion

Adding new traverse modes like EBIKE and ESCOOTER is architecturally feasible but requires:

1. **14+ code locations** to add switch cases
2. **New preference classes** for each electric mode
3. **Dynamic mode selection logic** to choose traverse mode based on propulsion type at rental pickup time
4. **Plumbing PropulsionType** from RentalVehicleType through VehicleRentalEdge to StateEditor

The main architectural challenge is that TraverseMode is currently selected early (at RentalFormFactor mapping) before the specific vehicle is known, but PropulsionType is only known when reaching a specific rental vehicle. This would require either:
- Late binding: select TraverseMode during state transition with access to PropulsionType
- Early enumeration: create form factors for each propulsion type combination

The switch-statement-heavy design makes adding modes somewhat tedious but straightforward. Each new mode follows the same pattern in speed calculation, cost calculation, reluctance, permissions, etc.

---

## Follow-up Research: Option D Deep Dive - TraverseMode as Record (2025-11-27)

**Note**: This section was originally labeled "Option C" but has been renamed to "Option D" for consistency with the updated option naming scheme.

**Date**: 2025-11-27T12:00:00+0000
**Researcher**: testower
**Git Commit**: bf2b9ac8524b4384785836f43669e6ee3aad0ee9
**Branch**: dev-2.x

### Research Question

What would Option D ("TraverseMode as Record" - making TraverseMode a record containing both mode type and propulsion info) entail? What is the current enum structure, how is it used throughout the codebase, and what would need to change to implement this approach?

### Summary

Option D proposes replacing the `TraverseMode` enum with a record:
```java
record TraverseMode(ModeType type, PropulsionType propulsion) {
  enum ModeType { WALK, BICYCLE, SCOOTER, CAR, FLEX }
}
```

This research documents the extensive use of TraverseMode as an enum throughout the codebase, identifying **16+ switch statements**, **EnumSet usage**, **bitwise operations in TraverseModeSet**, **API serialization patterns**, and **state management** that would all need modification. The codebase does have precedent for records with enum fields used in HashMap/HashSet collections, which is encouraging. However, the sheer scope of enum-specific features in use makes this a substantial refactoring effort.

### Current TraverseMode Enum Structure

**Location**: `application/src/main/java/org/opentripplanner/street/search/TraverseMode.java:5-32`

```java
public enum TraverseMode {
  WALK,
  BICYCLE,
  SCOOTER,
  CAR,
  FLEX;

  private static final EnumSet<TraverseMode> STREET_MODES = EnumSet.of(WALK, BICYCLE, SCOOTER, CAR);

  public boolean isOnStreetNonTransit() {
    return STREET_MODES.contains(this);
  }

  public boolean isInCar() {
    return this == CAR;
  }

  public boolean isCyclingIsh() {
    return this == BICYCLE || this == SCOOTER;
  }

  public boolean isWalking() {
    return this == WALK;
  }
}
```

**Key characteristics**:
- 5 enum values: WALK, BICYCLE, SCOOTER, CAR, FLEX
- Uses `EnumSet<TraverseMode>` internally for STREET_MODES
- Helper methods for mode classification
- Identity comparison (`== CAR`) used in helper methods

### Complete Inventory: Switch Statements on TraverseMode

The following 16 locations use switch statements or pattern matching on TraverseMode:

| # | File | Lines | Method/Context |
|---|------|-------|----------------|
| 1 | `StreetEdge.java` | 193-198 | `isNoThruTraffic()` |
| 2 | `StreetEdge.java` | 213-222 | `calculateSpeed()` |
| 3 | `StreetEdge.java` | 992-1003 | `traversalCosts` dispatch |
| 4 | `StreetEdge.java` | 1057-1066 | `modeReluctance` for intersections |
| 5 | `StreetEdgeReluctanceCalculator.java` | 24-35 | `computeReluctance()` |
| 6 | `StreetEdgeBuilder.java` | 227-231 | `withNoThruTrafficTraverseMode()` |
| 7 | `StreetTransitEntityLink.java` | 79-124 | `traverse()` |
| 8 | `StreetSearchRequest.java` | 210-216 | `rental(TraverseMode)` |
| 9 | `RoutingPreferences.java` | 114-121 | `rental(TraverseMode)` |
| 10 | `VehicleParking.java` | 223-236 | `hasSpacesAvailable()` |
| 11 | `VehicleParking.java` | 246-256 | `hasRealTimeDataForMode()` |
| 12 | `TraverseModeSet.java` | 145-151 | `getMaskForMode()` |
| 13 | `PruneIslands.java` | 304-310 | TraverseMode to StreetMode mapping |
| 14 | `ModeMapper.java` (test) | 14-21 | `mapToApi()` |
| 15 | `TestItineraryBuilder.java` (test) | 634-641 | `speed()` |
| 16 | `TemporaryConcreteEdge.java` (test) | 60-67 | `getSpeed()` |

**Common Patterns in Switch Statements**:
- `BICYCLE` and `SCOOTER` frequently grouped: `case BICYCLE, SCOOTER ->`
- `CAR` and `FLEX` sometimes grouped for motor vehicle behavior
- `FLEX` often throws exception or returns neutral value (0, 1, empty state)

### TraverseModeSet: Custom Bitwise Set Implementation

**Location**: `application/src/main/java/org/opentripplanner/street/search/TraverseModeSet.java:18-153`

TraverseModeSet is a custom set implementation using bitmask encoding in a single `byte`:

```java
public class TraverseModeSet implements Cloneable, Serializable {
  private static final int MODE_BICYCLE = 1;  // bit 0
  private static final int MODE_WALK = 2;     // bit 1
  private static final int MODE_CAR = 4;      // bit 2
  private static final int MODE_ALL = 7;      // all bits
  private byte modes = 0;

  private int getMaskForMode(TraverseMode mode) {
    return switch (mode) {
      case BICYCLE, SCOOTER -> MODE_BICYCLE;  // SCOOTER shares BICYCLE mask!
      case WALK -> MODE_WALK;
      case CAR -> MODE_CAR;
      case FLEX -> 0;  // Not tracked
    };
  }
}
```

**Key constraints for Option C**:
- SCOOTER and BICYCLE share the same bitmask
- FLEX has no representation (mask = 0)
- Used for turn restrictions, street permissions, vertex linking
- TODO comment in code: "Replace this with the use of a EnumSet"

**Impact of changing to record**: Would require completely rewriting TraverseModeSet to work with record types instead of bitmask operations, or replacing with a different collection type.

### EnumSet Usage with TraverseMode

**Internal to TraverseMode enum** (`TraverseMode.java:12`):
```java
private static final EnumSet<TraverseMode> STREET_MODES = EnumSet.of(WALK, BICYCLE, SCOOTER, CAR);
```

**Other Set<TraverseMode> usages**:
- `VertexLinker.java:88-92`: `Set.of(WALK, BICYCLE, CAR)` for no-thru-traffic modes
- `VertexPropertyMapper.java:57-77`: `HashSet<TraverseMode>` for inspector output
- Various test files: `Set.of()` and `List.of()` with TraverseMode

**Impact of changing to record**: `EnumSet` only works with enums. Would need to replace with `Set<TraverseMode>` backed by `HashSet` or similar, losing the O(1) bitwise set operations of EnumSet.

### State Management: How TraverseMode Flows Through Routing

**StateData** (`StateData.java:33-43`):
```java
protected TraverseMode currentMode;  // Preferred mode (having bike, car, etc.)
protected TraverseMode backMode;     // Mode used to traverse previous edge
```

**Initial mode selection** (`StateData.java:58-67`):
```java
currentMode = switch (requestMode) {
  case NOT_SET, WALK, BIKE_RENTAL, SCOOTER_RENTAL, CAR_RENTAL, FLEXIBLE -> TraverseMode.WALK;
  case BIKE, BIKE_TO_PARK -> TraverseMode.BICYCLE;
  case CAR, CAR_TO_PARK, CAR_PICKUP, CAR_HAILING -> TraverseMode.CAR;
};
```

**Mode transitions** occur in `StateEditor.java`:
- Vehicle rental: `currentMode = formFactor.traverseMode` (line ~275)
- Vehicle parking: `currentMode = nonTransitMode` (line ~356)
- Car pickup: switches between WALK and CAR (lines 372-379)

**Impact of changing to record**: Would need to create record instances instead of using enum values, and all identity comparisons (`== CAR`) would need to change to equals-based or pattern matching.

### API Serialization

**GTFS GraphQL API** (`LegImpl.java:169-181`):
```java
return s.getMode().name();  // Returns "WALK", "BICYCLE", etc.
```
- Relies on enum `.name()` method

**Transmodel GraphQL API** (`EnumTypes.java:169-189`):
```java
.value("bicycle", TraverseMode.BICYCLE)
.value("foot", TraverseMode.WALK)
```
- Maps enum values to GraphQL enum type

**Graph Serialization (Kryo)**:
- Default enum serialization by ordinal value
- TraverseMode stored in StateData fields

**Impact of changing to record**: Would need custom serializers for both GraphQL APIs and Kryo. The `.name()` approach would no longer work directly.

### Precedent: Records with Enum Fields in OTP Codebase

The codebase has several examples of records containing enum fields used in collections:

**MainAndSubMode** (`MainAndSubMode.java:12-57`):
```java
public record MainAndSubMode(TransitMode mainMode, @Nullable SubMode subMode) {
  // Used in List<MainAndSubMode> with .contains() relying on record equals
}
```

**EntityKey.DirectionAndRoute** (`EntityKey.java:29-32`):
```java
record DirectionAndRoute(FeedScopedId routeId, Direction direction) implements EntityKey {}
// Used as HashMap key in TransitAlertServiceImpl
```

**StopRelationship** (`RealtimeVehicle.java:104-111`):
```java
public record StopRelationship(StopLocation stop, StopStatus status) {}
// StopStatus is an enum
```

These patterns show that records with enum fields work well as collection elements and map keys, relying on auto-generated equals/hashCode.

### Proposed Option D Structure

```java
public record TraverseMode(ModeType type, PropulsionType propulsion) {
  public enum ModeType { WALK, BICYCLE, SCOOTER, CAR, FLEX }

  // Convenience constructors for non-rental modes
  public static TraverseMode walk() { return new TraverseMode(ModeType.WALK, null); }
  public static TraverseMode bicycle() { return new TraverseMode(ModeType.BICYCLE, PropulsionType.HUMAN); }
  public static TraverseMode ebike() { return new TraverseMode(ModeType.BICYCLE, PropulsionType.ELECTRIC_ASSIST); }
  public static TraverseMode scooter() { return new TraverseMode(ModeType.SCOOTER, PropulsionType.ELECTRIC); }
  public static TraverseMode car() { return new TraverseMode(ModeType.CAR, null); }

  public boolean isOnStreetNonTransit() {
    return type != ModeType.FLEX;
  }

  public boolean isInCar() {
    return type == ModeType.CAR;
  }

  public boolean isCyclingIsh() {
    return type == ModeType.BICYCLE || type == ModeType.SCOOTER;
  }

  public boolean isWalking() {
    return type == ModeType.WALK;
  }

  public boolean isElectric() {
    return propulsion != null &&
           (propulsion == PropulsionType.ELECTRIC || propulsion == PropulsionType.ELECTRIC_ASSIST);
  }
}
```

### Detailed Impact Analysis for Option D

#### 1. Switch Statement Updates (16+ locations)

Each switch would need to change from:
```java
switch (traverseMode) {
  case WALK -> ...;
  case BICYCLE -> ...;
}
```

To either:
```java
switch (traverseMode.type()) {
  case WALK -> ...;
  case BICYCLE -> ... // Can check propulsion here if needed
}
```

Or using pattern matching:
```java
switch (traverseMode) {
  case TraverseMode(WALK, _) -> ...;
  case TraverseMode(BICYCLE, var prop) -> ... // Access propulsion
}
```

#### 2. EnumSet Replacement

Current:
```java
private static final EnumSet<TraverseMode> STREET_MODES = EnumSet.of(WALK, BICYCLE, SCOOTER, CAR);
```

Would become:
```java
private static final Set<ModeType> STREET_MODE_TYPES = EnumSet.of(WALK, BICYCLE, SCOOTER, CAR);
public boolean isOnStreetNonTransit() {
  return STREET_MODE_TYPES.contains(this.type);
}
```

#### 3. TraverseModeSet Rewrite

Options:
- **Option 3a**: Keep bitwise approach but map `mode.type()` to masks
- **Option 3b**: Replace with `Set<TraverseMode>` (loses compactness)
- **Option 3c**: Rewrite using `EnumSet<ModeType>` plus propulsion handling

#### 4. Identity Comparisons

Current:
```java
if (mode == TraverseMode.CAR) { ... }
```

Would become:
```java
if (mode.type() == ModeType.CAR) { ... }
// OR
if (mode.isInCar()) { ... }  // Using helper method
```

#### 5. API Serialization Updates

**GTFS API**: Need to serialize based on mode type (and optionally propulsion)
```java
// Change from: s.getMode().name()
// To: s.getMode().type().name() or custom serialization
```

**Transmodel API**: Update GraphQL enum mapping
```java
.value("bicycle", TraverseMode.bicycle())
.value("ebike", TraverseMode.ebike())
// Need new enum values for electric variants?
```

**Kryo**: Register custom serializer for TraverseMode record

#### 6. State Management Updates

StateData field types remain the same (TraverseMode), but:
- Creation of modes changes: `TraverseMode.bicycle()` instead of `TraverseMode.BICYCLE`
- `RentalFormFactor.traverseMode` would need to be a method that takes `PropulsionType`

### Comparison: Option D vs Other Options

**Note**: This section was originally labeled "Option C" but has been renamed to "Option D" for consistency with the updated option naming scheme used throughout this document and the GitHub issue.

| Aspect | Option A (New Enum Values) | Option B (Propulsion in Cost) | Option D (Record) |
|--------|---------------------------|-------------------------------|-------------------|
| Code locations changed | 14+ switch statements | Cost calculation only | 16+ switches + serialization |
| New types introduced | EBIKE, ESCOOTER enums | None | ModeType inner enum |
| EnumSet compatibility | Yes | Yes | No (needs replacement) |
| TraverseModeSet | Works as-is | Works as-is | Needs rewrite |
| API changes | Add new enum values | None | Significant restructuring |
| Flexibility | Limited to known propulsion types | Propulsion check at runtime | Full type+propulsion combo |
| Performance | Same (enum) | Same | Slightly more allocations |

### Code References

**Core TraverseMode**:
- `TraverseMode.java:5-32` - Enum definition

**All Switch Locations**:
- `StreetEdge.java:193-198, 213-222, 992-1003, 1057-1066`
- `StreetEdgeReluctanceCalculator.java:24-35`
- `StreetEdgeBuilder.java:227-231`
- `StreetTransitEntityLink.java:79-124`
- `StreetSearchRequest.java:210-216`
- `RoutingPreferences.java:114-121`
- `VehicleParking.java:223-236, 246-256`
- `TraverseModeSet.java:145-151`
- `PruneIslands.java:304-310`

**EnumSet Usage**:
- `TraverseMode.java:12` - STREET_MODES
- `VertexLinker.java:88-92` - NO_THRU_MODES

**State Management**:
- `StateData.java:33-43` - Mode field declarations
- `StateData.java:58-67` - Initial mode selection
- `StateEditor.java:244-379` - Mode transitions

**API Serialization**:
- `LegImpl.java:169-181` - GTFS mode serialization
- `EnumTypes.java:169-189` - Transmodel LEG_MODE enum

**Record Precedents**:
- `MainAndSubMode.java:12-57` - Record with enum field in collections
- `EntityKey.java:12-32` - Records as map keys

### Conclusion

Option D (TraverseMode as a record) is technically feasible and would provide the most flexible representation of mode + propulsion combinations. However, it requires the most extensive changes:

1. **16+ switch statement updates** - Each needs to switch on `mode.type()` or use pattern matching
2. **EnumSet replacement** - Cannot use EnumSet with records; need alternative
3. **TraverseModeSet rewrite** - Bitwise operations tied to enum ordinals
4. **Serialization updates** - Both GraphQL APIs and Kryo need custom handling
5. **Identity comparison changes** - All `== BICYCLE` comparisons must change

The codebase does have good precedent for records with enum fields used in collections, but the pervasive use of enum-specific features (EnumSet, switch exhaustiveness checking, `.name()`, `.ordinal()`) makes this a substantial undertaking.

**Estimated scope**: 30-50 files modified, touching core routing logic, API layer, and graph serialization.

**Risk level**: High - changes touch fundamental routing infrastructure

---

## Follow-up Research: Option B Deep Dive - Propulsion-Aware Cost Calculation (2025-11-27)

**Date**: 2025-11-27T14:30:00+0000
**Researcher**: testower
**Git Commit**: bf2b9ac8524b4384785836f43669e6ee3aad0ee9
**Branch**: dev-2.x

### Research Question

What would Option B (Propulsion-Aware Cost Calculation) entail in detail? This approach keeps existing traverse modes but modifies cost calculations based on propulsion type. Specifically:
1. How would e-scooters (constant speed) be handled?
2. How would e-bikes (variable speed with reduced slope impact) be handled?

### Summary

Option B modifies cost calculations at runtime based on propulsion type, without adding new traverse modes. The key changes are:
1. Thread `PropulsionType` through the routing State (similar to how `RentalFormFactor` is already tracked)
2. Modify `bicycleOrScooterTraversalCost()` to adjust effective distances based on propulsion
3. E-scooters use flat distance (constant speed model)
4. E-bikes use interpolated distance (reduced slope sensitivity model)

This approach requires fewer changes than Option A (new traverse modes) and is more targeted than Option C (TraverseMode as record).

### Current Architecture: Where Slope Affects Routing

#### 1. Build-Time: Effective Distances Pre-Computed

**Location**: `ElevationUtils.getSlopeCosts()` at `ElevationUtils.java:95-162`

Two key factors are computed for each street edge:

| Factor | Formula | Purpose |
|--------|---------|---------|
| `slopeSpeedFactor` | `sum(run / slopeSpeedCoef) / flatLength` | Time/speed penalty |
| `slopeWorkFactor` | `sum(energy) / flatLength` where `energy = hypotenuse * (1 + 4000 * slope³)` | Energy/work penalty |

These are stored as:
- `effectiveBikeDistance = distanceMeters * slopeSpeedFactor`
- `effectiveBikeDistanceForWorkCost = distanceMeters * slopeWorkFactor`

#### 2. Runtime: Cost Calculation Uses Effective Distances

**Location**: `StreetEdge.bicycleOrScooterTraversalCost()` at `StreetEdge.java:1104-1141`

```java
private TraversalCosts bicycleOrScooterTraversalCost(
  StreetSearchRequest req,
  TraverseMode mode,
  double speed
) {
  double time = getEffectiveBikeDistance() / speed;  // Line 1109 - ALWAYS uses slope-adjusted distance
  double weight;
  // ... switch on optimizeType ...
  // FLAT_STREETS uses getEffectiveBikeDistanceForWorkCost()
  // SHORTEST_DURATION uses getEffectiveBikeDistance()
  // TRIANGLE uses both
}
```

**Critical observation**: The `time` calculation at line 1109 ALWAYS uses `getEffectiveBikeDistance()`, which includes slope penalties. This affects travel time estimates for ALL bicycle/scooter routes.

### The PropulsionType Gap

#### Where PropulsionType Exists

`RentalVehicleType.java:101-102`:
```java
public PropulsionType propulsionType() {
  return propulsionType;
}
```

Available values (`RentalVehicleType.java:211-220`):
```java
public enum PropulsionType {
  HUMAN,
  ELECTRIC_ASSIST,  // E-bikes (pedal-assist)
  ELECTRIC,         // Fully electric (e-scooters)
  COMBUSTION,
  COMBUSTION_DIESEL,
  HYBRID,
  PLUG_IN_HYBRID,
  HYDROGEN_FUEL_CELL,
}
```

#### Where PropulsionType is Lost

`VehicleRentalUpdater.java:192-199` - When creating rental edges, only FormFactor is used:
```java
for (RentalFormFactor formFactor : formFactors) {
  tempEdges.addEdge(
    VehicleRentalEdge.createVehicleRentalEdge(vehicleRentalVertex, formFactor)
  );
}
// PropulsionType is NOT passed to the edge
```

`StateEditor.beginVehicleRentingAtStation()` at `StateEditor.java:282-303`:
```java
public void beginVehicleRentingAtStation(
  RentalFormFactor formFactor,
  String network,
  boolean mayKeep,
  boolean reverse
) {
  // ...
  child.stateData.rentalVehicleFormFactor = formFactor;
  // NO PropulsionType stored!
}
```

`StateData.java:49` - Only FormFactor is tracked:
```java
public RentalFormFactor rentalVehicleFormFactor;
// NO PropulsionType field exists
```

### Option B Implementation: Threading PropulsionType

#### Step 1: Add PropulsionType to StateData

**File**: `StateData.java`

Add new field at line 50:
```java
public RentalFormFactor rentalVehicleFormFactor;
public PropulsionType rentalVehiclePropulsionType;  // NEW
```

#### Step 2: Update StateEditor Methods

**File**: `StateEditor.java`

Modify `beginVehicleRentingAtStation()` (line 282):
```java
public void beginVehicleRentingAtStation(
  RentalFormFactor formFactor,
  PropulsionType propulsionType,  // NEW PARAMETER
  String network,
  boolean mayKeep,
  boolean reverse
) {
  // ...
  if (!reverse) {
    child.stateData.rentalVehicleFormFactor = formFactor;
    child.stateData.rentalVehiclePropulsionType = propulsionType;  // NEW
  } else {
    child.stateData.rentalVehicleFormFactor = null;
    child.stateData.rentalVehiclePropulsionType = null;  // NEW
  }
}
```

Similarly update:
- `beginFloatingVehicleRenting()` (line 262)
- `dropOffRentedVehicleAtStation()` (line 305)
- `dropOffFloatingVehicle()` (line 337)

#### Step 3: Pass PropulsionType from VehicleRentalEdge

**File**: `VehicleRentalEdge.java`

The edge currently stores only `RentalFormFactor`. For floating vehicles, PropulsionType is accessible:
```java
// In traverse() method:
VehicleRentalVehicle vehicle = (VehicleRentalVehicle) station;
PropulsionType propulsion = vehicle.vehicleType().propulsionType();
```

For station-based rentals, need to determine which vehicle type is being rented. Options:
1. Use the "most common" propulsion type at the station
2. Add PropulsionType to the edge (per vehicle type available)
3. Default to HUMAN if mixed types available

#### Step 4: Add State Accessor

**File**: `State.java`

Add method to access propulsion type:
```java
public PropulsionType getRentalVehiclePropulsionType() {
  return stateData.rentalVehiclePropulsionType;
}
```

#### Step 5: Modify Cost Calculation

**File**: `StreetEdge.java`

Modify `bicycleOrScooterTraversalCost()`:

```java
private TraversalCosts bicycleOrScooterTraversalCost(
  StreetSearchRequest req,
  TraverseMode mode,
  double speed,
  PropulsionType propulsion  // NEW PARAMETER (or get from State)
) {
  // Calculate effective distances based on propulsion type
  double effectiveTimeDistance = calculateEffectiveTimeDistance(propulsion);
  double effectiveWorkDistance = calculateEffectiveWorkDistance(propulsion);

  double time = effectiveTimeDistance / speed;  // Was: getEffectiveBikeDistance() / speed
  double weight;

  switch (optimizeType) {
    case FLAT_STREETS -> weight = effectiveWorkDistance / speed;
    case SHORTEST_DURATION -> weight = effectiveTimeDistance / speed;
    case TRIANGLE -> {
      double quick = effectiveTimeDistance;
      double slope = effectiveWorkDistance;
      // ...
    }
    // ...
  }
}

private double calculateEffectiveTimeDistance(PropulsionType propulsion) {
  if (propulsion == null) {
    return getEffectiveBikeDistance();  // Default: full slope effect
  }

  return switch (propulsion) {
    case ELECTRIC -> getDistanceMeters();  // Constant speed (e-scooters)
    case ELECTRIC_ASSIST -> interpolateDistance(0.3);  // Reduced slope effect (e-bikes)
    default -> getEffectiveBikeDistance();  // Full slope effect
  };
}

private double interpolateDistance(double slopeSensitivity) {
  // Interpolate between flat distance and slope-adjusted distance
  double flatDistance = getDistanceMeters();
  double slopedDistance = getEffectiveBikeDistance();
  return flatDistance + (slopedDistance - flatDistance) * slopeSensitivity;
}
```

### E-Scooter Model: Constant Speed

#### Physics Rationale

Electric scooters (kick scooters with motors) typically:
- Have speed-limited motors (20-25 km/h in most jurisdictions)
- Maintain constant speed on moderate grades (up to ~10%)
- May slow slightly on steep uphills (>15%) due to motor/battery limits
- Are typically speed-limited on downhills (motor cuts out at max speed)

#### Implementation Approach

For `PropulsionType.ELECTRIC` (fully electric, no human power):

```java
case ELECTRIC -> {
  // Constant speed model - ignore slope for time calculation
  effectiveTimeDistance = getDistanceMeters();

  // Work cost could be zero (motor-powered) or slight penalty for battery drain on uphills
  effectiveWorkDistance = getDistanceMeters();
  // OR: slight uphill penalty for battery drain
  // effectiveWorkDistance = getDistanceMeters() * (1 + getMaxSlope() * 0.1);
}
```

**Key decisions**:
1. **Time**: Use flat distance (constant speed)
2. **Work/Energy**: Use flat distance (motor does work) or slight uphill penalty for battery considerations
3. **Safety**: Keep existing safety distance (steep downhills are dangerous on scooters)

### E-Bike Model: Variable Speed with Reduced Slope Impact

#### Physics Rationale

Electric-assist bicycles (pedelecs) typically:
- Provide motor assistance up to a speed limit (25 km/h in EU, 32 km/h in US)
- Motor assistance compensates for 60-80% of uphill effort
- Still require some human effort (pedaling triggers assist)
- Downhill: behave like regular bikes (motor cuts out above speed limit)

#### Implementation Approach

For `PropulsionType.ELECTRIC_ASSIST`:

```java
case ELECTRIC_ASSIST -> {
  // Reduced slope sensitivity - motor compensates for ~70% of uphill penalty
  double slopeSensitivity = 0.3;  // 30% of human-powered effect

  double flatDist = getDistanceMeters();
  double slopedDist = getEffectiveBikeDistance();

  // Uphill: reduced penalty
  // Downhill: same as regular bike (can go faster than motor limit)
  if (slopedDist > flatDist) {
    // Uphill segment - reduce penalty
    effectiveTimeDistance = flatDist + (slopedDist - flatDist) * slopeSensitivity;
  } else {
    // Downhill segment - keep full benefit (can coast faster than motor limit)
    effectiveTimeDistance = slopedDist;
  }

  // Work cost: significantly reduced (motor does most of the work)
  effectiveWorkDistance = flatDist +
    (getEffectiveBikeDistanceForWorkCost() - flatDist) * slopeSensitivity;
}
```

**Slope sensitivity factor considerations**:
- `0.0` = fully electric (no slope impact) - too aggressive for e-bikes
- `0.3` = motor compensates 70% of effort - reasonable for modern e-bikes
- `0.5` = motor compensates 50% of effort - conservative estimate
- `1.0` = no motor assist - same as regular bike

This could be made configurable in `BikePreferences` or `ScooterPreferences`.

#### Asymmetric Model (Advanced)

A more accurate model would be asymmetric:
- Uphill: 20-40% of human-powered penalty (motor helps significantly)
- Downhill: 80-100% of human-powered benefit (motor doesn't help, can coast fast)

```java
case ELECTRIC_ASSIST -> {
  double flatDist = getDistanceMeters();
  double slopedDist = getEffectiveBikeDistance();
  double delta = slopedDist - flatDist;

  if (delta > 0) {
    // Uphill: motor helps a lot
    effectiveTimeDistance = flatDist + delta * 0.25;  // 25% of uphill penalty
  } else {
    // Downhill: can coast fast like regular bike
    effectiveTimeDistance = flatDist + delta * 0.9;  // 90% of downhill benefit
  }
}
```

### Code Changes Summary

| File | Change | Lines Affected |
|------|--------|----------------|
| `StateData.java` | Add `rentalVehiclePropulsionType` field | +1 line |
| `State.java` | Add `getRentalVehiclePropulsionType()` accessor | +3 lines |
| `StateEditor.java` | Add PropulsionType to 4 rental methods | ~20 lines modified |
| `VehicleRentalEdge.java` | Pass PropulsionType to StateEditor | ~10 lines modified |
| `StreetEdge.java` | Add propulsion-aware distance calculation | ~40 lines added |
| Tests | Update tests for new parameter | Variable |

**Estimated scope**: 6-8 files modified, ~80 lines of core changes

**Risk level**: Medium - changes are targeted to rental vehicle routing, don't affect owned bike/car routing

### Comparison: Option B vs Other Options

| Aspect | Option A (New Enum Values) | Option B (This Research) | Option D (Record) |
|--------|---------------------------|--------------------------|-------------------|
| Files changed | 14+ | 6-8 | 30-50 |
| Lines of code | ~200 | ~80 | ~500+ |
| API changes | Add EBIKE, ESCOOTER enums | None | Major restructuring |
| Graph changes | None | None | Serialization changes |
| Risk level | Medium-High | Medium | High |
| Flexibility | Fixed propulsion types | Runtime calculation | Full flexibility |
| Performance | Same as current | Slight overhead | Slight overhead |

**Note**: Option C (Separate Preference Sets) is complementary and can be combined with any of these approaches.

### Open Questions for Implementation

1. **Station-based rentals**: How to determine PropulsionType when a station has multiple vehicle types?
   - Option: Use the most common type
   - Option: Create separate edges per propulsion type
   - Option: Default to HUMAN (conservative)

2. **Configurable sensitivity**: Should the slope sensitivity factor be configurable per deployment?
   - Could add `ebikeSlopeSensitivity` to preferences
   - Default: 0.3 (70% motor compensation)

3. **Downhill behavior for e-bikes**: Should we apply asymmetric model?
   - Uphill: reduced penalty (motor helps)
   - Downhill: full benefit (coasting, motor off)

4. **Safety considerations**: Should steep downhills still be penalized for e-scooters?
   - High-speed e-scooter descents can be dangerous
   - Keep safety distance calculation for all modes?

5. **Battery range**: Should `maxRangeMeters` be enforced as a routing constraint?
   - Could add range check to state validation
   - Separate from slope handling

### Recommended Approach

Start with a simple implementation:

1. **Add PropulsionType to State** (Steps 1-4 above)
2. **Implement constant speed for e-scooters** (`ELECTRIC` → flat distance)
3. **Implement reduced slope for e-bikes** (`ELECTRIC_ASSIST` → 30% sensitivity)
4. **Keep safety calculations unchanged** (steep slopes penalized for safety)
5. **Add configuration option** for slope sensitivity factor
6. **Add unit tests** for propulsion-aware cost calculations

This provides immediate improvement for e-scooter and e-bike routing with minimal code changes and risk.

---

## Supplemental Research: Option B Architecture Details (2025-11-27)

**Date**: 2025-11-27
**Researcher**: testower (supplement to earlier research)
**Focus**: Pre-computed values architecture and existing extension points

### Pre-Computed Values Architecture

#### StreetElevationExtension Storage

**Location**: `StreetElevationExtension.java:8-145`

The pre-computed slope values are stored in `StreetElevationExtension` with these fields:

```java
private final double effectiveBicycleSafetyDistance;  // Line 19
private final double effectiveBikeDistance;           // Line 21 - slope-speed-adjusted
private final double effectiveBikeDistanceForWorkCost; // Line 23 - energy/work cost
private final double effectiveWalkDistance;           // Line 25
private final float maxSlope;                         // Line 31 - steepest segment grade
```

The constructor (lines 35-66) computes final values by multiplying factors by distance:
```java
this.effectiveBikeDistance = effectiveBikeDistanceFactor * distanceMeters;
this.effectiveBikeDistanceForWorkCost = effectiveBikeWorkFactor * distanceMeters;
```

**Key Insight**: The factors are human-powered cycling physics from `ElevationUtils.getSlopeCosts()`. These would need different values for electric vehicles.

### Existing Extension Point: StreetEdgeCostExtension

**Location**: `StreetEdgeCostExtension.java:9-16`

OTP has an existing mechanism for adding extra costs to street edges:

```java
public interface StreetEdgeCostExtension {
  double calculateExtraCost(State state, int edgeLength, TraverseMode traverseMode);
}
```

**Usage in StreetEdge** (line 67 and ~1076):
```java
private StreetEdgeCostExtension costExtension;  // One extension per edge

// In doTraverse():
if (costExtension != null) {
  weight += costExtension.calculateExtraCost(s0, length_mm, traverseMode);
}
```

**Example**: `DataOverlayStreetEdgeCostExtension` (sandbox module) adds costs based on environmental data overlays.

#### Option B3: Extension-Based Approach

Could implement propulsion-aware routing as a `StreetEdgeCostExtension`:

```java
public class PropulsionAwareCostExtension implements StreetEdgeCostExtension {
  @Override
  public double calculateExtraCost(State state, int edgeLength, TraverseMode mode) {
    PropulsionType propulsion = state.getRentalVehiclePropulsionType();
    if (propulsion == null || !isElectric(propulsion)) return 0;

    StreetEdge edge = (StreetEdge) state.getBackEdge();
    double currentPenalty = edge.getEffectiveBikeDistance() - edge.getDistanceMeters();
    if (currentPenalty <= 0) return 0;  // Downhill

    double slopeSensitivity = propulsion == PropulsionType.ELECTRIC ? 0.0 : 0.3;
    double penaltyReduction = currentPenalty * (1 - slopeSensitivity);
    return -penaltyReduction / state.getRequest().bike().speed();  // Negative to reduce
  }
}
```

**Limitations**:
1. Only ONE extension per edge
2. Called AFTER main cost calculation
3. Doesn't affect `time` calculation, only `weight`

### Station-Based Rental: PropulsionType Selection

For stations with multiple vehicle types:

```java
PropulsionType selectPropulsion(VehicleRentalStation station, RentalFormFactor formFactor) {
  return station.vehicleTypesAvailable().entrySet().stream()
    .filter(e -> e.getKey().formFactor() == formFactor)
    .filter(e -> e.getValue() > 0)
    .map(e -> e.getKey().propulsionType())
    .min(Comparator.comparing(p -> p.isElectric() ? 0 : 1))  // Prefer electric
    .orElse(PropulsionType.HUMAN);
}
```

### Option B Implementation Variants Comparison

| Variant | Memory | CPU | Precision | Sandbox-Friendly |
|---------|--------|-----|-----------|------------------|
| **B1: Runtime Adjustment** | +1 field StateData | +1 calc/edge | ~Good | No |
| **B2: Pre-Computed Values** | +16 bytes/edge | None | Exact | No |
| **B3: Cost Extension** | +1 field StateData | +1 calc/edge | ~Good | Yes |

### Recommended Implementation Order

1. **Phase 1: Foundation** (Required for all variants)
   - Add `PropulsionType` field to `StateData`
   - Add accessor to `State`
   - Update `StateEditor` rental methods
   - Update `VehicleRentalEdge` to extract PropulsionType

2. **Phase 2: Choose Variant**
   - **B1 (Recommended)**: Modify `bicycleOrScooterTraversalCost()`
   - **B2 (Alternative)**: Add electric values to `StreetElevationExtension`
   - **B3 (Sandbox)**: Implement as `StreetEdgeCostExtension`

### Code Reference Summary

**State Threading**:
- `StateData.java:49` - Add field
- `StateEditor.java:262-303` - Update rental methods
- `VehicleRentalEdge.java:114-128` - Extract PropulsionType

**Pre-Computed Values**:
- `StreetElevationExtension.java:19-27` - Stored values
- `ElevationUtils.java:95-162` - `getSlopeCosts()` computation
- `ElevationUtils.java:20-22` - Human cycling constants (`ENERGY_SLOPE_FACTOR = 4000`)

**Cost Calculation**:
- `StreetEdge.java:1104-1141` - `bicycleOrScooterTraversalCost()`
- `StreetEdge.java:231-245` - Effective distance accessors
- `StreetEdgeCostExtension.java:9-16` - Extension interface
