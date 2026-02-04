# Propulsion Slope Sensitivity Configuration Implementation Plan

## Overview

Make the electric-assist slope sensitivity factor (currently hardcoded as `0.3` in `StreetEdge.java`) configurable via `VehicleRentalPreferences`, allowing per-request customization of how much slope effect is felt by riders of electric-assist bikes.

## Current State Analysis

The slope sensitivity is currently hardcoded:
- **Location**: `StreetEdge.java:69`
- **Constant**: `private static final double ELECTRIC_ASSIST_SLOPE_SENSITIVITY = 0.3;`
- **Usage**: Lines 1193, 1210 in `getEffectiveDistanceForPropulsion()` and `getEffectiveWorkDistanceForPropulsion()`

The hardcoded value means:
- The motor compensates for 70% of slope difficulty
- The rider feels 30% of the slope effect
- All e-bike rentals use this same value regardless of actual motor power

### Key Discoveries:
- `bicycleOrScooterTraversalCost()` at `StreetEdge.java:1132-1176` already has access to `StreetSearchRequest req` and `State state`
- The request provides rental preferences via `req.bike().rental()` and `req.scooter().rental()` (both return `RentalRequest`)
- The `TraverseMode mode` parameter distinguishes between BICYCLE and SCOOTER
- OTP uses a two-layer preference system: `VehicleRentalPreferences` (serializable, for RouteRequest) and `RentalRequest` (for street search)
- The mapping occurs in `StreetSearchRequestMapper.mapRental()` at line 137

## Desired End State

After implementation:
1. `VehicleRentalPreferences` has a configurable `electricAssistSlopeSensitivity` field with default `0.3`
2. The value flows through `RentalRequest` to `StreetEdge` cost calculations
3. Users can configure this via `router-config.json` under `routingDefaults.bike.rental.electricAssistSlopeSensitivity`
4. Tests verify the configurable behavior using `VehicleRentalPreferences.DEFAULT_ELECTRIC_ASSIST_SLOPE_SENSITIVITY` instead of hardcoded `0.3`

### Verification:
- All existing tests pass with no changes to expected values
- New test verifies that custom slope sensitivity values are honored
- Build completes successfully

## What We're NOT Doing

- NOT exposing this via GraphQL API in this phase (can be added later)
- NOT adding separate sensitivity values for time vs work calculations (use same value for both)
- NOT adding different defaults for bike vs scooter (both use `VehicleRentalPreferences`)
- NOT modifying the ELECTRIC (fully electric) propulsion type behavior (it already ignores slope completely)

## Implementation Approach

Add the field to both layers of the preference system (VehicleRentalPreferences → RentalRequest), then modify StreetEdge to pass the value from the request to the calculation methods.

---

## Phase 1: Add Field to VehicleRentalPreferences

### Overview
Add `electricAssistSlopeSensitivity` field to the preference layer following OTP's established patterns.

### Changes Required:

#### 1. VehicleRentalPreferences.java
**File**: `application/src/main/java/org/opentripplanner/routing/api/request/preference/VehicleRentalPreferences.java`

**Add import:**
```java
import static org.opentripplanner.framework.model.Units.ratio;
```

**Add constant after line 19 (after DEFAULT declaration):**
```java
/**
 * Default slope sensitivity for electric-assist vehicles.
 * 0.0 = motor fully compensates (ignore slope), 1.0 = no assistance (full slope effect).
 */
public static final double DEFAULT_ELECTRIC_ASSIST_SLOPE_SENSITIVITY = 0.3;
```

**Add field (after `bannedNetworks` field, around line 31):**
```java
private final double electricAssistSlopeSensitivity;
```

**Update default constructor (add after bannedNetworks initialization):**
```java
this.electricAssistSlopeSensitivity = DEFAULT_ELECTRIC_ASSIST_SLOPE_SENSITIVITY;
```

**Update builder constructor (add after bannedNetworks):**
```java
this.electricAssistSlopeSensitivity = ratio(builder.electricAssistSlopeSensitivity);
```

**Add getter (after bannedNetworks() method):**
```java
/**
 * How sensitive electric-assist vehicles are to slope. A value between 0 and 1:
 * <ul>
 *   <li>0.0 = motor fully compensates for slopes (like fully electric)</li>
 *   <li>1.0 = no motor assistance on slopes (like human-powered)</li>
 *   <li>0.3 (default) = motor compensates for 70% of slope difficulty</li>
 * </ul>
 */
public double electricAssistSlopeSensitivity() {
  return electricAssistSlopeSensitivity;
}
```

**Update equals() - add to the comparison chain:**
```java
doubleEquals(electricAssistSlopeSensitivity, that.electricAssistSlopeSensitivity) &&
```

**Update hashCode() - add to Objects.hash():**
```java
electricAssistSlopeSensitivity,
```

**Update toString() - add before .toString():**
```java
.addNum(
  "electricAssistSlopeSensitivity",
  electricAssistSlopeSensitivity,
  DEFAULT.electricAssistSlopeSensitivity
)
```

**Add to Builder class - field:**
```java
private double electricAssistSlopeSensitivity;
```

**Add to Builder constructor:**
```java
this.electricAssistSlopeSensitivity = original.electricAssistSlopeSensitivity;
```

**Add Builder setter:**
```java
public Builder withElectricAssistSlopeSensitivity(double electricAssistSlopeSensitivity) {
  this.electricAssistSlopeSensitivity = electricAssistSlopeSensitivity;
  return this;
}
```

---

## Phase 2: Add Field to RentalRequest

### Overview
Add the same field to the street search layer's `RentalRequest` class.

### Changes Required:

#### 1. RentalRequest.java
**File**: `application/src/main/java/org/opentripplanner/street/search/request/RentalRequest.java`

**Add import:**
```java
import org.opentripplanner.routing.api.request.preference.VehicleRentalPreferences;
```

**Add field (after bannedNetworks, around line 28):**
```java
private final double electricAssistSlopeSensitivity;
```

**Update default constructor:**
```java
this.electricAssistSlopeSensitivity = VehicleRentalPreferences.DEFAULT_ELECTRIC_ASSIST_SLOPE_SENSITIVITY;
```

**Update builder constructor:**
```java
this.electricAssistSlopeSensitivity = builder.electricAssistSlopeSensitivity;
```

**Add getter:**
```java
/**
 * Slope sensitivity for electric-assist rental vehicles (0-1).
 * @see VehicleRentalPreferences#electricAssistSlopeSensitivity()
 */
public double electricAssistSlopeSensitivity() {
  return electricAssistSlopeSensitivity;
}
```

**Update equals():**
```java
doubleEquals(electricAssistSlopeSensitivity, that.electricAssistSlopeSensitivity) &&
```

Note: Need to add import `import static org.opentripplanner.utils.lang.DoubleUtils.doubleEquals;`

**Update hashCode():**
```java
electricAssistSlopeSensitivity,
```

**Update toString():**
```java
.addNum(
  "electricAssistSlopeSensitivity",
  electricAssistSlopeSensitivity,
  DEFAULT.electricAssistSlopeSensitivity
)
```

**Add to Builder - field:**
```java
private double electricAssistSlopeSensitivity;
```

**Add to Builder constructor:**
```java
this.electricAssistSlopeSensitivity = original.electricAssistSlopeSensitivity;
```

**Add Builder setter:**
```java
public Builder withElectricAssistSlopeSensitivity(double electricAssistSlopeSensitivity) {
  this.electricAssistSlopeSensitivity = electricAssistSlopeSensitivity;
  return this;
}
```

---

## Phase 3: Add Mapping and Wire to StreetEdge

### Overview
Map the preference to RentalRequest and modify StreetEdge to use the configurable value.

### Changes Required:

#### 1. StreetSearchRequestMapper.java
**File**: `application/src/main/java/org/opentripplanner/street/search/request/StreetSearchRequestMapper.java`

**Update mapRental() method - add at end of builder chain:**
```java
.withElectricAssistSlopeSensitivity(rental.electricAssistSlopeSensitivity())
```

#### 2. StreetEdge.java
**File**: `application/src/main/java/org/opentripplanner/street/model/edge/StreetEdge.java`

**Remove the hardcoded constant (line 69):**
Delete:
```java
private static final double ELECTRIC_ASSIST_SLOPE_SENSITIVITY = 0.3;
```

Also delete the Javadoc comment above it (lines 54-68).

**Modify bicycleOrScooterTraversalCost() to get sensitivity from request:**

After line 1140 (after getting propulsion type), add:
```java
double electricAssistSlopeSensitivity = mode == TraverseMode.BICYCLE
  ? req.bike().rental().electricAssistSlopeSensitivity()
  : req.scooter().rental().electricAssistSlopeSensitivity();
```

**Modify getEffectiveDistanceForPropulsion() signature and body:**

Change from:
```java
private double getEffectiveDistanceForPropulsion(PropulsionType propulsion) {
```

To:
```java
private double getEffectiveDistanceForPropulsion(
  PropulsionType propulsion,
  double electricAssistSlopeSensitivity
) {
```

Update the ELECTRIC_ASSIST case:
```java
case ELECTRIC_ASSIST -> interpolateSlopeEffect(
  getEffectiveBikeDistance(),
  electricAssistSlopeSensitivity
);
```

**Modify getEffectiveWorkDistanceForPropulsion() similarly:**

Change signature:
```java
private double getEffectiveWorkDistanceForPropulsion(
  PropulsionType propulsion,
  double electricAssistSlopeSensitivity
) {
```

Update the ELECTRIC_ASSIST case:
```java
case ELECTRIC_ASSIST -> interpolateSlopeEffect(
  getEffectiveBikeDistanceForWorkCost(),
  electricAssistSlopeSensitivity
);
```

**Update call sites in bicycleOrScooterTraversalCost():**

Line 1142:
```java
double effectiveTimeDistance = getEffectiveDistanceForPropulsion(propulsion, electricAssistSlopeSensitivity);
```

Line 1159:
```java
getEffectiveWorkDistanceForPropulsion(propulsion, electricAssistSlopeSensitivity) / speed;
```

Line 1164:
```java
double slope = getEffectiveWorkDistanceForPropulsion(propulsion, electricAssistSlopeSensitivity);
```

---

## Phase 4: Add Configuration Parsing

### Overview
Enable configuration via router-config.json.

### Changes Required:

#### 1. RouteRequestConfig.java
**File**: `application/src/main/java/org/opentripplanner/standalone/config/routerequest/RouteRequestConfig.java`

**Find mapRental() method and add:**
```java
.withElectricAssistSlopeSensitivity(
  c
    .of("electricAssistSlopeSensitivity")
    .since(V2_8)
    .summary("How sensitive electric-assist rental vehicles are to slopes.")
    .description(
      """
      A value between 0 and 1 where:
      - 0.0 means the motor fully compensates for slopes (like fully electric vehicles)
      - 1.0 means no motor assistance on slopes (like human-powered vehicles)
      - 0.3 (default) means the motor compensates for 70% of slope difficulty
      """
    )
    .asDouble(dft.electricAssistSlopeSensitivity())
)
```

---

## Phase 5: Update Tests

### Overview
Update tests to use the constant instead of hardcoded 0.3, and add a test for custom values.

### Changes Required:

#### 1. StreetEdgePropulsionCostTest.java
**File**: `application/src/test/java/org/opentripplanner/street/model/edge/StreetEdgePropulsionCostTest.java`

**Add import:**
```java
import org.opentripplanner.routing.api.request.preference.VehicleRentalPreferences;
```

**Update test electricAssistBikeHasReducedSlopeEffect() around line 242-243:**

Change from:
```java
// 30% sensitivity: flat + (sloped - flat) * 0.3
double expectedEffectiveDistance = flatDistance + (slopedDistance - flatDistance) * 0.3;
```

To:
```java
// Use default slope sensitivity from preferences
double slopeSensitivity = VehicleRentalPreferences.DEFAULT_ELECTRIC_ASSIST_SLOPE_SENSITIVITY;
double expectedEffectiveDistance = flatDistance + (slopedDistance - flatDistance) * slopeSensitivity;
```

**Add new test for custom slope sensitivity:**
```java
/**
 * Tests that a custom electric-assist slope sensitivity is honored.
 */
@Test
void customElectricAssistSlopeSensitivity() {
  // Create edge with elevation profile
  Coordinate c1 = new Coordinate(-122.575033, 45.456773);
  Coordinate c2 = new Coordinate(-122.576668, 45.451426);

  StreetVertex from = intersectionVertex("from", c1.y, c1.x);
  StreetVertex to = intersectionVertex("to", c2.y, c2.x);

  var geometry = org.opentripplanner.framework.geometry.GeometryUtils.getGeometryFactory()
    .createLineString(new Coordinate[] { c1, c2 });

  StreetEdge hillyEdge = new StreetEdgeBuilder<>()
    .withFromVertex(from)
    .withToVertex(to)
    .withGeometry(geometry)
    .withName("Hilly Street")
    .withMeterLength(LENGTH)
    .withPermission(StreetTraversalPermission.ALL)
    .buildAndConnect();

  // Add elevation profile
  Coordinate[] profile = new Coordinate[] {
    new Coordinate(0, 0),
    new Coordinate(LENGTH / 2, LENGTH / 20.0),
    new Coordinate(LENGTH, 0),
  };
  PackedCoordinateSequence elev = new PackedCoordinateSequence.Double(profile);
  StreetElevationExtensionBuilder.of(hillyEdge)
    .withElevationProfile(elev)
    .withComputed(false)
    .build()
    .ifPresent(hillyEdge::setElevationExtension);

  double flatDistance = hillyEdge.getDistanceMeters();
  double slopedDistance = hillyEdge.getEffectiveBikeDistance();

  // Use custom sensitivity of 0.5 (50% slope effect)
  double customSensitivity = 0.5;
  double expectedEffectiveDistance = flatDistance + (slopedDistance - flatDistance) * customSensitivity;

  // Electric-assist bike rental with custom slope sensitivity
  var req = StreetSearchRequest.of()
    .withMode(StreetMode.BIKE_RENTAL)
    .withBike(bike ->
      bike
        .withSpeed(SPEED)
        .withOptimizeType(VehicleRoutingOptimizeType.TRIANGLE)
        .withOptimizeTriangle(it -> it.withTime(1))
        .withReluctance(1)
        .withRental(rental -> rental.withElectricAssistSlopeSensitivity(customSensitivity))
    )
    .build();

  var editor = new StateEditor(from, req);
  editor.beginFloatingVehicleRenting(
    RentalFormFactor.BICYCLE,
    PropulsionType.ELECTRIC_ASSIST,
    "network",
    false
  );
  var state = editor.makeState();

  var result = hillyEdge.traverse(state)[0];

  double expectedWeight = expectedEffectiveDistance / SPEED;
  assertEquals(expectedWeight, result.getWeight() - state.getWeight(), DELTA);
}
```

#### 2. VehicleRentalPreferencesTest.java (if exists, otherwise create)
**File**: `application/src/test/java/org/opentripplanner/routing/api/request/preference/VehicleRentalPreferencesTest.java`

Add test for the new field:
```java
@Test
void electricAssistSlopeSensitivity() {
  var prefs = VehicleRentalPreferences.of()
    .withElectricAssistSlopeSensitivity(0.5)
    .build();
  assertEquals(0.5, prefs.electricAssistSlopeSensitivity(), 0.001);
}

@Test
void electricAssistSlopeSensitivityDefault() {
  assertEquals(
    VehicleRentalPreferences.DEFAULT_ELECTRIC_ASSIST_SLOPE_SENSITIVITY,
    VehicleRentalPreferences.DEFAULT.electricAssistSlopeSensitivity(),
    0.001
  );
}
```

---

## Success Criteria

### Automated Verification:
- [x] Build succeeds: `mvn package -DskipTests`
- [ ] All tests pass: `mvn test`
- [x] Propulsion tests pass: `mvn test -Dtest=StreetEdgePropulsionCostTest`
- [x] Preferences tests pass: `mvn test -Dtest=VehicleRentalPreferencesTest`

### Manual Verification:
- [ ] Default behavior unchanged (routes should be identical before and after)
- [ ] Custom sensitivity values affect routing weights as expected

---

## Testing Strategy

### Unit Tests:
- `VehicleRentalPreferencesTest`: Field getter, builder, equals/hashCode
- `RentalRequestTest`: Same pattern (if test exists)
- `StreetEdgePropulsionCostTest`: Updated to use constant, new test for custom value

### Integration Tests:
- Existing routing tests should pass unchanged (default value maintains behavior)

---

## References

- Handoff document: `thoughts/shared/handoffs/general/2025-11-28_22-04-30_propulsion-test-refactoring-plan.md`
- VehicleRentalPreferences pattern: `application/src/main/java/org/opentripplanner/routing/api/request/preference/VehicleRentalPreferences.java`
- RentalRequest pattern: `application/src/main/java/org/opentripplanner/street/search/request/RentalRequest.java`
- StreetEdge cost calculation: `application/src/main/java/org/opentripplanner/street/model/edge/StreetEdge.java:1132-1226`
