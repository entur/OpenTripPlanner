# Phase 2: Refactor StreetEdgePropulsionCostTest

## Overview

Refactor `StreetEdgePropulsionCostTest.java` to reduce testing of implementation details by removing or consolidating the state storage tests, while keeping the behavior-focused cost calculation tests.

## Current State Analysis

The test file contains 10 tests in two categories:

### Behavior Tests (KEEP - 4 tests):
| Test | Line | What it verifies |
|------|------|------------------|
| `rentalScooterCanTraverseEdge()` | 99-116 | Rental scooter can traverse edge and propulsion preserved |
| `electricScooterUsesConstantSpeedOnHillyTerrain()` | 137-203 | ELECTRIC uses flat distance (ignores slope) |
| `humanPoweredBikeUsesFullSlopeEffect()` | 209-271 | HUMAN uses full slope effect |
| `electricAssistBikeHasReducedSlopeEffect()` | 277-342 | ELECTRIC_ASSIST uses 30% slope sensitivity |

### State Storage Tests (REMOVE/CONSOLIDATE - 6 tests):
| Test | Line | What it tests |
|------|------|---------------|
| `electricScooterPropulsionTypeIsStoredInState()` | 44-57 | Internal state storage |
| `electricAssistBikePropulsionTypeIsStoredInState()` | 59-72 | Internal state storage |
| `humanPoweredBikePropulsionTypeIsStoredInState()` | 74-87 | Internal state storage |
| `ownedBikeHasNullPropulsionType()` | 89-97 | Internal state for non-rental |
| `allPropulsionTypesCanBeStoredAndRetrieved()` | 118-127 | Parameterized state storage |
| `propulsionTypeClearedAfterDropOff()` | 347-375 | Internal state cleared on drop-off |

### Key Discovery:
The state storage tests are redundant because:
- If `electricScooterUsesConstantSpeedOnHillyTerrain()` passes, the ELECTRIC propulsion type must be correctly stored
- If `humanPoweredBikeUsesFullSlopeEffect()` passes, the HUMAN propulsion type must be correctly stored
- If `electricAssistBikeHasReducedSlopeEffect()` passes, the ELECTRIC_ASSIST propulsion type must be correctly stored

## Desired End State

After refactoring:
1. State storage tests are removed (6 tests)
2. Behavior tests are preserved (4 tests)
3. If Phase 1 is complete, the hardcoded `0.3` in `electricAssistBikeHasReducedSlopeEffect()` is replaced with `VehicleRentalPreferences.DEFAULT_ELECTRIC_ASSIST_SLOPE_SENSITIVITY` or `RentalRequest.DEFAULT_ELECTRIC_ASSIST_SLOPE_SENSITIVITY`
4. Optionally: Add a test verifying custom slope sensitivity values work

### Verification:
- All tests pass: `mvn test -Dtest=StreetEdgePropulsionCostTest`
- Build passes: `mvn package -DskipTests`

## What We're NOT Doing

- Adding complex new tests
- Changing test infrastructure
- Modifying other test files
- Adding tests for state management (that's implementation detail)

## Implementation Approach

Simple surgical removal of the 6 state storage tests, keeping the 4 behavior tests intact.

---

## Phase 2.1: Remove State Storage Tests

### Changes Required:

#### 1. StreetEdgePropulsionCostTest.java
**File**: `application/src/test/java/org/opentripplanner/street/model/edge/StreetEdgePropulsionCostTest.java`

**Remove the following tests (delete the entire method blocks):**

1. **Lines 44-57**: Remove `electricScooterPropulsionTypeIsStoredInState()`
2. **Lines 59-72**: Remove `electricAssistBikePropulsionTypeIsStoredInState()`
3. **Lines 74-87**: Remove `humanPoweredBikePropulsionTypeIsStoredInState()`
4. **Lines 89-97**: Remove `ownedBikeHasNullPropulsionType()`
5. **Lines 118-127**: Remove `allPropulsionTypesCanBeStoredAndRetrieved()`
6. **Lines 347-375**: Remove `propulsionTypeClearedAfterDropOff()`

**Clean up unused imports after removal:**
Remove these if no longer used:
```java
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
```

Also potentially remove:
```java
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
```

---

## Phase 2.2: Update electricAssistBikeHasReducedSlopeEffect() (Conditional)

**Only do this if Phase 1 (slope sensitivity configuration) is complete.**

### Changes Required:

#### 1. StreetEdgePropulsionCostTest.java
**File**: `application/src/test/java/org/opentripplanner/street/model/edge/StreetEdgePropulsionCostTest.java`

**Add import:**
```java
import org.opentripplanner.street.search.request.RentalRequest;
```

**Update line 314 (the expected distance calculation):**

Change from:
```java
double expectedEffectiveDistance = flatDistance + (slopedDistance - flatDistance) * 0.3;
```

To:
```java
double expectedEffectiveDistance = flatDistance + (slopedDistance - flatDistance) * RentalRequest.DEFAULT_ELECTRIC_ASSIST_SLOPE_SENSITIVITY;
```

---

## Phase 2.3: Optional - Add Custom Sensitivity Test

**Only do this if Phase 1 is complete and you want comprehensive test coverage.**

### Changes Required:

#### 1. StreetEdgePropulsionCostTest.java
**File**: `application/src/test/java/org/opentripplanner/street/model/edge/StreetEdgePropulsionCostTest.java`

**Add new test after `electricAssistBikeHasReducedSlopeEffect()`:**

```java
/**
 * Tests that custom slope sensitivity values are respected.
 * Uses 0.5 sensitivity instead of default 0.3.
 */
@Test
void customElectricAssistSlopeSensitivityIsRespected() {
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

  // Add elevation profile (10% slope up and down)
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
  double customSensitivity = 0.5;
  double expectedEffectiveDistance = flatDistance + (slopedDistance - flatDistance) * customSensitivity;

  // Electric-assist bike with custom 0.5 sensitivity
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

  // Should use custom 50% slope sensitivity
  double expectedWeight = expectedEffectiveDistance / SPEED;
  assertEquals(expectedWeight, result.getWeight() - state.getWeight(), DELTA);
}
```

---

## Expected Final Test File Structure

After refactoring, the test file will contain:

```java
class StreetEdgePropulsionCostTest {

  // Constants
  private static final double DELTA = 0.00001;
  private static final double SPEED = 6.0;
  private static final double LENGTH = 650.0;

  // Fixtures
  private final StreetVertex v0 = intersectionVertex(0.0, 0.0);
  private final StreetVertex v1 = intersectionVertex(2.0, 2.0);

  // BEHAVIOR TESTS (4 original + 1 optional)

  @Test
  void rentalScooterCanTraverseEdge() { ... }

  @Test
  void electricScooterUsesConstantSpeedOnHillyTerrain() { ... }

  @Test
  void humanPoweredBikeUsesFullSlopeEffect() { ... }

  @Test
  void electricAssistBikeHasReducedSlopeEffect() { ... }

  @Test  // Optional, only if Phase 1 complete
  void customElectricAssistSlopeSensitivityIsRespected() { ... }
}
```

---

## Success Criteria

### Automated Verification:
- [x] All remaining tests pass: `mvn test -Dtest=StreetEdgePropulsionCostTest`
- [x] No compilation errors: `mvn package -DskipTests`
- [x] No unused import warnings

### Manual Verification:
- [x] Test count reduced from 10 to 4 (or 5 if custom sensitivity test added)
- [x] All behavior tests still validate correct cost calculation
- [x] Code coverage for propulsion cost logic maintained by behavior tests

---

## Testing Strategy

### Verification That Behavior Tests Cover State Storage:
The behavior tests implicitly verify state storage because:
1. `electricScooterUsesConstantSpeedOnHillyTerrain()` creates an ELECTRIC rental, traverses, and verifies cost uses flat distance - this only works if propulsion type is correctly stored
2. `humanPoweredBikeUsesFullSlopeEffect()` creates a HUMAN rental, traverses, and verifies cost uses sloped distance
3. `electricAssistBikeHasReducedSlopeEffect()` creates an ELECTRIC_ASSIST rental, traverses, and verifies cost uses 30% sensitivity
4. `rentalScooterCanTraverseEdge()` explicitly checks propulsion type is preserved after traversal

If any state storage was broken, these tests would fail.

---

## References

- Handoff document: `thoughts/shared/handoffs/general/2025-11-28_22-04-30_propulsion-test-refactoring-plan.md`
- Test file: `application/src/test/java/org/opentripplanner/street/model/edge/StreetEdgePropulsionCostTest.java`
- Phase 1 plan: `thoughts/shared/plans/2025-11-28-propulsion-slope-sensitivity-configuration.md`
