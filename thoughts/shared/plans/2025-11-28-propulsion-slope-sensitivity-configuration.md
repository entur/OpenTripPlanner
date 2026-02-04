# Phase 1: Add Slope Sensitivity to VehicleRentalPreferences

## Overview

Make the hardcoded electric-assist slope sensitivity factor (0.3) configurable via `VehicleRentalPreferences`, following OTP's standard preference pattern. This allows per-request configuration of how much slope effect e-bike riders experience.

## Current State Analysis

The slope sensitivity factor is currently hardcoded in `StreetEdge.java`:

```java
// StreetEdge.java:52-69
private static final double ELECTRIC_ASSIST_SLOPE_SENSITIVITY = 0.3;
```

This constant is used in two methods:
- `getEffectiveDistanceForPropulsion()` (line 1191-1194)
- `getEffectiveWorkDistanceForPropulsion()` (line 1208-1211)

### Key Discoveries:
- `VehicleRentalPreferences` follows OTP's immutable preferences pattern with Builder (`VehicleRentalPreferences.java`)
- `RentalRequest` is the street-level equivalent mapped via `StreetSearchRequestMapper.mapRental()` (lines 137-152)
- During traversal, preferences are accessed via `state.getRequest().bike().rental()` or `state.getRequest().rental(traverseMode)`
- The cost calculation happens in `bicycleOrScooterTraversalCost()` which has access to both `StreetSearchRequest` and `State`

## Desired End State

After implementation:
1. `VehicleRentalPreferences` has `electricAssistSlopeSensitivity` field with default value 0.3
2. `RentalRequest` has corresponding field mapped from VehicleRentalPreferences
3. `StreetEdge` uses the configurable value from preferences instead of hardcoded constant
4. Existing tests pass without modification
5. New test verifies custom slope sensitivity values work correctly

### Verification:
- All propulsion cost tests pass: `mvn test -Dtest=StreetEdgePropulsionCostTest`
- VehicleRentalPreferences tests pass: `mvn test -Dtest=VehicleRentalPreferencesTest`
- Project compiles: `mvn package -DskipTests`

## What We're NOT Doing

- Configuration parsing for router-config.json (can be added later)
- GraphQL API exposure for the new parameter
- Separate sensitivity values for speed vs work cost calculations
- Per-network or per-vehicle sensitivity configuration

## Implementation Approach

Follow the existing OTP preference pattern by:
1. Adding the field to `VehicleRentalPreferences` (API layer)
2. Adding the field to `RentalRequest` (street layer)
3. Updating the mapper to copy the value
4. Modifying `StreetEdge` to use preferences instead of constant

## Phase 1.1: Add to VehicleRentalPreferences

### Changes Required:

#### 1. VehicleRentalPreferences.java
**File**: `application/src/main/java/org/opentripplanner/routing/api/request/preference/VehicleRentalPreferences.java`

**Add default constant after line 19:**
```java
public static final double DEFAULT_ELECTRIC_ASSIST_SLOPE_SENSITIVITY = 0.3;
```

**Add field after line 30:**
```java
private final double electricAssistSlopeSensitivity;
```

**Initialize in default constructor (after line 41):**
```java
this.electricAssistSlopeSensitivity = DEFAULT_ELECTRIC_ASSIST_SLOPE_SENSITIVITY;
```

**Initialize from builder (after line 55):**
```java
this.electricAssistSlopeSensitivity = builder.electricAssistSlopeSensitivity;
```

**Add getter (after line 122):**
```java
/**
 * How much of the slope effect is felt by electric-assist bike riders.
 * <p>
 * Value between 0.0 (motor fully compensates) and 1.0 (no motor help on slopes).
 * Default 0.3 means motor compensates for 70% of slope difficulty.
 */
public double electricAssistSlopeSensitivity() {
  return electricAssistSlopeSensitivity;
}
```

**Update equals() - add to comparison chain around line 145:**
```java
Double.compare(electricAssistSlopeSensitivity, that.electricAssistSlopeSensitivity) == 0 &&
```

**Update hashCode() - add field to hash around line 161:**
```java
electricAssistSlopeSensitivity,
```

**Update toString() - add field around line 182:**
```java
.addNum("electricAssistSlopeSensitivity", electricAssistSlopeSensitivity, DEFAULT_ELECTRIC_ASSIST_SLOPE_SENSITIVITY)
```

**Update Builder class:**

Add field after line 197:
```java
private double electricAssistSlopeSensitivity;
```

Initialize from original in Builder constructor (after line 211):
```java
this.electricAssistSlopeSensitivity = original.electricAssistSlopeSensitivity;
```

Add setter method after line 277:
```java
public Builder withElectricAssistSlopeSensitivity(double electricAssistSlopeSensitivity) {
  this.electricAssistSlopeSensitivity = electricAssistSlopeSensitivity;
  return this;
}
```

---

## Phase 1.2: Add to RentalRequest

### Changes Required:

#### 1. RentalRequest.java
**File**: `application/src/main/java/org/opentripplanner/street/search/request/RentalRequest.java`

**Add default constant after line 17:**
```java
public static final double DEFAULT_ELECTRIC_ASSIST_SLOPE_SENSITIVITY = 0.3;
```

**Add field after line 28:**
```java
private final double electricAssistSlopeSensitivity;
```

**Initialize in default constructor (after line 39):**
```java
this.electricAssistSlopeSensitivity = DEFAULT_ELECTRIC_ASSIST_SLOPE_SENSITIVITY;
```

**Initialize from builder (after line 53):**
```java
this.electricAssistSlopeSensitivity = builder.electricAssistSlopeSensitivity;
```

**Add getter (after line 120):**
```java
/**
 * How much of the slope effect is felt by electric-assist bike riders.
 * Value between 0.0 and 1.0. Default 0.3.
 */
public double electricAssistSlopeSensitivity() {
  return electricAssistSlopeSensitivity;
}
```

**Update equals() - add to comparison chain around line 143:**
```java
Double.compare(electricAssistSlopeSensitivity, that.electricAssistSlopeSensitivity) == 0 &&
```

**Update hashCode() - add field around line 159:**
```java
electricAssistSlopeSensitivity,
```

**Update toString() - add field around line 180:**
```java
.addNum("electricAssistSlopeSensitivity", electricAssistSlopeSensitivity, DEFAULT.electricAssistSlopeSensitivity)
```

**Update Builder class:**

Add field after line 195:
```java
private double electricAssistSlopeSensitivity;
```

Initialize from original in Builder constructor (after line 209):
```java
this.electricAssistSlopeSensitivity = original.electricAssistSlopeSensitivity;
```

Add setter method after line 259:
```java
public Builder withElectricAssistSlopeSensitivity(double electricAssistSlopeSensitivity) {
  this.electricAssistSlopeSensitivity = electricAssistSlopeSensitivity;
  return this;
}
```

---

## Phase 1.3: Update StreetSearchRequestMapper

### Changes Required:

#### 1. StreetSearchRequestMapper.java
**File**: `application/src/main/java/org/opentripplanner/street/search/request/StreetSearchRequestMapper.java`

**Update mapRental() method - add after line 151:**
```java
.withElectricAssistSlopeSensitivity(rental.electricAssistSlopeSensitivity())
```

---

## Phase 1.4: Modify StreetEdge to Use Preferences

### Changes Required:

#### 1. StreetEdge.java
**File**: `application/src/main/java/org/opentripplanner/street/model/edge/StreetEdge.java`

**Keep the constant but add deprecation note or use it as fallback (line 52-69):**
The constant can be kept for documentation/fallback or removed. For now, keep it as a fallback.

**Modify `getEffectiveDistanceForPropulsion()` to accept sensitivity parameter:**

Change method signature and body (lines 1185-1197):
```java
private double getEffectiveDistanceForPropulsion(PropulsionType propulsion, double electricAssistSlopeSensitivity) {
  if (propulsion == null) {
    return getEffectiveBikeDistance();
  }
  return switch (propulsion) {
    case ELECTRIC -> getDistanceMeters();
    case ELECTRIC_ASSIST -> interpolateSlopeEffect(
      getEffectiveBikeDistance(),
      electricAssistSlopeSensitivity
    );
    default -> getEffectiveBikeDistance();
  };
}
```

**Modify `getEffectiveWorkDistanceForPropulsion()` similarly (lines 1202-1214):**
```java
private double getEffectiveWorkDistanceForPropulsion(PropulsionType propulsion, double electricAssistSlopeSensitivity) {
  if (propulsion == null) {
    return getEffectiveBikeDistanceForWorkCost();
  }
  return switch (propulsion) {
    case ELECTRIC -> getDistanceMeters();
    case ELECTRIC_ASSIST -> interpolateSlopeEffect(
      getEffectiveBikeDistanceForWorkCost(),
      electricAssistSlopeSensitivity
    );
    default -> getEffectiveBikeDistanceForWorkCost();
  };
}
```

**Modify `bicycleOrScooterTraversalCost()` to extract sensitivity and pass it (lines 1132-1176):**

After line 1140 (after PropulsionType extraction), add:
```java
double slopeSensitivity = getSlopeSensitivityFromRequest(req, mode);
```

Update calls to use the new parameter:
- Line 1142: `getEffectiveDistanceForPropulsion(propulsion, slopeSensitivity)`
- Line 1159: `getEffectiveWorkDistanceForPropulsion(propulsion, slopeSensitivity)`
- Line 1164: `getEffectiveWorkDistanceForPropulsion(propulsion, slopeSensitivity)`

**Add helper method to extract sensitivity:**
```java
private double getSlopeSensitivityFromRequest(StreetSearchRequest req, TraverseMode mode) {
  var rental = mode == TraverseMode.BICYCLE
    ? req.bike().rental()
    : req.scooter().rental();
  return rental.electricAssistSlopeSensitivity();
}
```

---

## Success Criteria

### Automated Verification:
- [ ] All propulsion cost tests pass: `mvn test -Dtest=StreetEdgePropulsionCostTest`
- [ ] VehicleRentalPreferences tests pass (if they exist): `mvn test -Dtest=VehicleRentalPreferencesTest`
- [ ] Full build passes: `mvn package`
- [ ] No linting errors: `mvn prettier:check`

### Manual Verification:
- [ ] Default behavior unchanged (0.3 sensitivity applied by default)
- [ ] Custom sensitivity values work when configured via API

---

## Testing Strategy

### Unit Tests:
The existing `StreetEdgePropulsionCostTest` tests should continue to pass since they use default preferences.

### Optional: Add New Test:
Add test in `StreetEdgePropulsionCostTest` to verify custom sensitivity:
```java
@Test
void customSlopeSensitivityIsRespected() {
  // Create hilly edge (same setup as other tests)
  // Use custom sensitivity of 0.5
  var req = StreetSearchRequest.of()
    .withMode(StreetMode.BIKE_RENTAL)
    .withBike(bike -> bike
      .withSpeed(SPEED)
      .withOptimizeType(VehicleRoutingOptimizeType.TRIANGLE)
      .withOptimizeTriangle(it -> it.withTime(1))
      .withReluctance(1)
      .withRental(r -> r.withElectricAssistSlopeSensitivity(0.5))
    )
    .build();
  // Verify the expected weight uses 0.5 sensitivity
}
```

---

## References

- Handoff document: `thoughts/shared/handoffs/general/2025-11-28_22-04-30_propulsion-test-refactoring-plan.md`
- VehicleRentalPreferences pattern: `application/src/main/java/org/opentripplanner/routing/api/request/preference/VehicleRentalPreferences.java`
- RentalRequest: `application/src/main/java/org/opentripplanner/street/search/request/RentalRequest.java`
- StreetEdge cost calculation: `application/src/main/java/org/opentripplanner/street/model/edge/StreetEdge.java:1132-1226`
