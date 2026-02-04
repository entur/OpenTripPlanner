---
date: 2025-11-28T22:04:30+0100
researcher: Claude
git_commit: 728b777c8110c676c46154f10dcbf44019b21e9f
branch: feature/propulsion-type-aware-bike-and-scooter-routing
repository: OpenTripPlanner
topic: "Propulsion-Aware Routing Test Refactoring and Slope Sensitivity Configuration"
tags: [implementation, refactoring, tests, vehicle-rental, propulsion]
status: planned
last_updated: 2025-11-28
last_updated_by: Claude
type: implementation_strategy
---

# Handoff: Propulsion Test Refactoring & Slope Sensitivity Configuration

## Task(s)

1. **Test Refactoring** (planned) - Refactor `StreetEdgePropulsionCostTest.java` to reduce testing of implementation details
2. **Slope Sensitivity Configuration** (planned) - Make the electric-assist slope sensitivity factor (currently hardcoded as 0.3) configurable via `VehicleRentalPreferences`

## Critical References

- `application/src/test/java/org/opentripplanner/street/model/edge/StreetEdgePropulsionCostTest.java` - The test file to refactor
- `application/src/main/java/org/opentripplanner/routing/api/request/preference/VehicleRentalPreferences.java` - Where slope sensitivity config should be added
- `application/src/main/java/org/opentripplanner/routing/api/request/preference/BikePreferences.java` - Reference for OTP preferences pattern

## Recent changes

None in this session - this was analysis/planning only.

## Learnings

### Test Analysis Results

The test file has two categories of tests:

**Behavior tests (keep these):**
- `electricScooterUsesConstantSpeedOnHillyTerrain()` - Tests routing cost outcome
- `humanPoweredBikeUsesFullSlopeEffect()` - Tests routing cost outcome
- `electricAssistBikeHasReducedSlopeEffect()` - Tests routing cost outcome
- `rentalScooterCanTraverseEdge()` - Integration-level behavior

**Implementation detail tests (consider removing/consolidating):**
- `electricScooterPropulsionTypeIsStoredInState()` - Tests internal state storage
- `electricAssistBikePropulsionTypeIsStoredInState()` - Tests internal state storage
- `humanPoweredBikePropulsionTypeIsStoredInState()` - Tests internal state storage
- `allPropulsionTypesCanBeStoredAndRetrieved()` - Tests internal state storage
- `ownedBikeHasNullPropulsionType()` - Tests internal state
- `propulsionTypeClearedAfterDropOff()` - Tests internal state management

The state storage tests are redundant - if the cost tests pass, the state must be correctly stored.

### OTP Preferences Pattern

OTP uses immutable preference classes with builders for per-request configuration. The pattern in `VehicleRentalPreferences`:
- Private constructor with defaults
- Private constructor from Builder
- Immutable fields with getters
- Nested Builder class with `with*()` methods
- `equals/hashCode/toString` including all fields
- `DEFAULT` static instance with default values exposed as constants

## Artifacts

None created yet - this is a planning handoff.

## Action Items & Next Steps

### Phase 1: Add Slope Sensitivity to VehicleRentalPreferences

1. Add to `VehicleRentalPreferences.java`:
   - Add constant: `public static final double DEFAULT_ELECTRIC_ASSIST_SLOPE_SENSITIVITY = 0.3;`
   - Add field: `private final double electricAssistSlopeSensitivity;`
   - Initialize in default constructor to `DEFAULT_ELECTRIC_ASSIST_SLOPE_SENSITIVITY`
   - Add getter: `public double electricAssistSlopeSensitivity()`
   - Add to Builder: field + `withElectricAssistSlopeSensitivity(double)` method
   - Update `equals()`, `hashCode()`, `toString()`

2. Wire the preference through to `StreetEdge.traverse()`:
   - Find where cost calculation happens (look for the 0.3 hardcoded value)
   - Pass preferences through the traversal context
   - Use `preferences.bike().rental().electricAssistSlopeSensitivity()` instead of hardcoded value

3. Update configuration parsing if needed (for router-config.json support)

### Phase 2: Refactor Tests

1. In `StreetEdgePropulsionCostTest.java`:
   - Remove or consolidate the 6 state-storage tests into a single test or remove entirely
   - Update `electricAssistBikeHasReducedSlopeEffect()` to use `VehicleRentalPreferences.DEFAULT_ELECTRIC_ASSIST_SLOPE_SENSITIVITY` instead of hardcoded 0.3
   - Consider testing `propulsionTypeClearedAfterDropOff()` by verifying subsequent routing uses correct (non-rental) costs, rather than checking null state

2. Optionally add a test that verifies custom slope sensitivity values work correctly

### Phase 3: Build & Test

```bash
mvn test -Dtest=StreetEdgePropulsionCostTest
mvn test -Dtest=VehicleRentalPreferencesTest
mvn package -DskipTests  # Verify compilation
```

## Other Notes

### Key Files to Examine

- `StreetEdge.java` - Contains the cost calculation logic where 0.3 is likely hardcoded
- Look for where `rentalVehiclePropulsionType()` is used in cost calculations
- `RouteRequest` and how preferences flow to street traversal

### Design Decisions Made

- Slope sensitivity belongs in `VehicleRentalPreferences` (not `StreetEdgeBuilder`) because:
  - It's a rental vehicle behavior parameter
  - Follows existing OTP patterns for per-request configuration
  - Semantically correct location
  - Enables both per-request override and system-wide defaults
