# TransModel API Scooter Preferences Implementation Plan

## Overview

Add native scooter preference parameters to the TransModel GraphQL API while maintaining backward compatibility with existing clients that use bicycle field names for scooter routing. This completes the final 1/3 of issue #6572 (the other 2/3 were fixed by PR #6599).

## Current State Analysis

The TransModel API currently reuses bicycle field names (`bikeSpeed`, `bicycleOptimisationMethod`) for scooter preferences due to historical backward compatibility requirements. This creates confusion and inconsistency with the GTFS GraphQL API, which has separate, properly-named `ScooterPreferencesInput` and `BicyclePreferencesInput` types.

### Key Findings:
- **TransModel API limitation**: ScooterPreferencesMapper.java:15-16 explicitly maps bicycle fields to scooter preferences
- **GTFS API reference**: Already has proper separation with distinct `ScooterPreferencesInput` type
- **Internal model**: ScooterPreferences.java:28-32 has separate fields (speed, reluctance, rental, optimizeType, optimizeTriangle)
- **PR #6599 fix**: Fixed the A* heuristic to use correct vehicle speeds (EuclideanRemainingWeightHeuristic.java:62-74)

## Desired End State

After implementation:
1. TransModel API has native scooter-specific fields: `scooterSpeed`, `scooterReluctance`, `scooterOptimisationMethod`
2. Existing clients using `bikeSpeed` for scooter routing continue to work unchanged (backward compatibility)
3. New clients can use explicit scooter fields for clarity
4. Implementation follows the same pattern as GTFS API
5. Comprehensive test coverage ensures fallback logic works correctly

### Verification:
- GraphQL schema includes new scooter fields
- ScooterPreferencesMapper implements fallback hierarchy: scooter-specific → bicycle fallback → default
- All tests pass: `mvn test -Dtest=ScooterPreferencesMapperTest`
- Manual testing via GraphiQL confirms both old and new field names work
- Documentation updated to guide migration

## What We're NOT Doing

- NOT making breaking changes to existing API
- NOT removing bicycle field fallback (maintained for backward compatibility)
- NOT changing the internal domain model (ScooterPreferences class)
- NOT modifying GTFS API (already has proper separation)
- NOT adding new triangle factor fields (existing triangleFactors work for both)
- NOT deprecating bicycle fields yet (that's a future Phase 3, not part of this implementation)

## Implementation Approach

Add scooter-specific fields alongside existing bicycle fields in the schema, then implement a preference hierarchy in the mapper: try scooter-specific fields first, fall back to bicycle fields if not set, then use defaults. This maintains 100% backward compatibility while enabling clearer semantics for new clients.

## Phase 1: Extend GraphQL Schema

### Overview
Add new optional scooter preference fields to the TransModel GraphQL schema alongside existing bicycle fields.

### Changes Required:

#### 1. TransModel GraphQL Schema
**File**: `application/src/main/resources/org/opentripplanner/apis/transmodel/schema.graphql`
**Changes**: Add three new optional arguments to the `trip` query

Find the section with bicycle parameters (around line 832-834) and add scooter parameters nearby:

```graphql
# Existing bicycle parameters (keep unchanged for backward compatibility)
bicycleOptimisationMethod: BicycleOptimisationMethod = safe,
bikeSpeed: Float = 5.0,

# NEW: Add scooter-specific parameters
"The set of characteristics that the user wants to optimise for during scooter searches -- defaults to safe"
scooterOptimisationMethod: BicycleOptimisationMethod = safe,
"The maximum scooter speed along streets, in meters per second"
scooterSpeed: Float = 5.0,
"A measure of how bad scooter travel is compared to being in transit for equal periods of time"
scooterReluctance: Float = 2.0,
```

**Notes**:
- Reuse the existing `BicycleOptimisationMethod` enum (values: flat, greenways, quick, safe, triangle) since scooter optimization uses the same options
- Keep default values consistent with ScooterPreferences.DEFAULT (speed=5.0, reluctance=2.0)
- Add these after the bicycle parameters for logical grouping

### Success Criteria:

#### Automated Verification:
- [ ] Build succeeds: `mvn package -DskipTests`
- [ ] No GraphQL schema validation errors

#### Manual Verification:
- [ ] Start OTP locally: `java -jar otp-shaded/target/otp-shaded-*.jar --load .`
- [ ] Open GraphiQL at `http://localhost:8080/otp/transmodel/v3/graphiql`
- [ ] Verify new fields appear in autocomplete for trip query arguments
- [ ] Confirm field descriptions are visible in documentation panel

---

## Phase 2: Update ScooterPreferencesMapper

### Overview
Implement the preference hierarchy in ScooterPreferencesMapper to try scooter-specific fields first, then fall back to bicycle fields, then to defaults.

### Changes Required:

#### 1. ScooterPreferencesMapper Implementation
**File**: `application/src/main/java/org/opentripplanner/apis/transmodel/mapping/preferences/ScooterPreferencesMapper.java`
**Changes**: Replace lines 15-21 with fallback logic

Replace the existing simple mapping with a preference hierarchy:

```java
public static void mapScooterPreferences(
  ScooterPreferences.Builder scooter,
  DataFetcherDecorator callWith
) {
  // Speed: Try scooter-specific field first, fall back to bicycle field
  // We need to check if scooter field was explicitly set to avoid always overriding
  var originalSpeed = scooter.speed();
  callWith.argument("scooterSpeed", scooter::withSpeed);
  if (scooter.speed() == originalSpeed) {
    // scooterSpeed was not provided, try bikeSpeed for backward compatibility
    callWith.argument("bikeSpeed", scooter::withSpeed);
  }

  // Optimization method: Try scooter-specific field first, fall back to bicycle field
  var originalOptimizeType = scooter.optimizeType();
  callWith.argument("scooterOptimisationMethod", scooter::withOptimizeType);
  if (scooter.optimizeType() == originalOptimizeType) {
    // scooterOptimisationMethod was not provided, try bicycleOptimisationMethod for backward compatibility
    callWith.argument("bicycleOptimisationMethod", scooter::withOptimizeType);
  }

  // Reluctance: Try scooter-specific field first, fall back to walkReluctance
  var originalReluctance = scooter.reluctance();
  callWith.argument("scooterReluctance", scooter::withReluctance);
  if (scooter.reluctance() == originalReluctance) {
    // scooterReluctance was not provided, try walkReluctance for backward compatibility
    callWith.argument("walkReluctance", (Double r) -> scooter.withReluctance(r));
  }

  // Triangle factors work for both bicycle and scooter (no change needed)
  if (scooter.optimizeType() == VehicleRoutingOptimizeType.TRIANGLE) {
    scooter.withOptimizeTriangle(triangle -> {
      callWith.argument("triangleFactors.time", triangle::withTime);
      callWith.argument("triangleFactors.slope", triangle::withSlope);
      callWith.argument("triangleFactors.safety", triangle::withSafety);
    });
  }

  // Shared rental preferences mapper (no change needed)
  scooter.withRental(rental -> mapRentalPreferences(rental, callWith));
}
```

**Implementation Notes**:
- Store original values before attempting to set from GraphQL arguments
- Check if value changed after attempting to set from scooter-specific field
- Only try fallback field if scooter-specific field didn't change the value
- This approach respects explicit null/zero values while providing fallback for missing fields

### Success Criteria:

#### Automated Verification:
- [ ] Build succeeds: `mvn package -DskipTests`
- [ ] Code compiles without errors

#### Manual Verification:
- [ ] Code review confirms fallback logic is correct
- [ ] No regressions in mapper behavior (will be verified by tests in Phase 3)

---

## Phase 3: Add Comprehensive Test Coverage

### Overview
Extend existing test suite to verify the fallback hierarchy works correctly in all scenarios.

### Changes Required:

#### 1. ScooterPreferencesMapperTest
**File**: `application/src/test/java/org/opentripplanner/apis/transmodel/mapping/preferences/ScooterPreferencesMapperTest.java`
**Changes**: Add new test cases for fallback logic

Add these new test methods after the existing tests (after line 69):

```java
// ===== NEW TEST CASES FOR SCOOTER-SPECIFIC FIELDS =====

static List<Arguments> mapScooterSpecificFieldsTestCases() {
  return List.of(
    // Scooter-specific fields take precedence
    Arguments.of("scooterSpeed", 10.0, "ScooterPreferences{speed: 10.0}"),
    Arguments.of("scooterReluctance", 10.0, "ScooterPreferences{reluctance: 10.0}"),
    Arguments.of(
      "scooterOptimisationMethod",
      VehicleRoutingOptimizeType.TRIANGLE,
      "ScooterPreferences{optimizeType: TRIANGLE}"
    )
  );
}

@ParameterizedTest
@MethodSource("mapScooterSpecificFieldsTestCases")
void testMapScooterSpecificFields(String field, Object value, String expected) {
  var preferences = ScooterPreferences.of();
  mapScooterPreferences(preferences, TestDataFetcherDecorator.of(field, value));
  assertEquals(expected, preferences.build().toString());
}

@Test
void testScooterSpeedTakesPrecedenceOverBikeSpeed() {
  var preferences = ScooterPreferences.of();
  mapScooterPreferences(
    preferences,
    TestDataFetcherDecorator.of(
      java.util.Map.of(
        "scooterSpeed", 10.0,
        "bikeSpeed", 5.0  // Should be ignored
      )
    )
  );
  assertEquals(10.0, preferences.build().speed());
}

@Test
void testBikeSpeedUsedAsFallbackWhenScooterSpeedNotProvided() {
  var preferences = ScooterPreferences.of();
  mapScooterPreferences(
    preferences,
    TestDataFetcherDecorator.of("bikeSpeed", 7.0)
  );
  assertEquals(7.0, preferences.build().speed());
}

@Test
void testScooterOptimisationMethodTakesPrecedenceOverBicycleOptimisationMethod() {
  var preferences = ScooterPreferences.of();
  mapScooterPreferences(
    preferences,
    TestDataFetcherDecorator.of(
      java.util.Map.of(
        "scooterOptimisationMethod", VehicleRoutingOptimizeType.FLAT_STREETS,
        "bicycleOptimisationMethod", VehicleRoutingOptimizeType.TRIANGLE
      )
    )
  );
  assertEquals(VehicleRoutingOptimizeType.FLAT_STREETS, preferences.build().optimizeType());
}

@Test
void testBicycleOptimisationMethodUsedAsFallback() {
  var preferences = ScooterPreferences.of();
  mapScooterPreferences(
    preferences,
    TestDataFetcherDecorator.of("bicycleOptimisationMethod", VehicleRoutingOptimizeType.SAFEST_STREETS)
  );
  assertEquals(VehicleRoutingOptimizeType.SAFEST_STREETS, preferences.build().optimizeType());
}

@Test
void testScooterReluctanceTakesPrecedenceOverWalkReluctance() {
  var preferences = ScooterPreferences.of();
  mapScooterPreferences(
    preferences,
    TestDataFetcherDecorator.of(
      java.util.Map.of(
        "scooterReluctance", 5.0,
        "walkReluctance", 3.0
      )
    )
  );
  assertEquals(5.0, preferences.build().reluctance());
}

@Test
void testWalkReluctanceUsedAsFallbackForScooterReluctance() {
  var preferences = ScooterPreferences.of();
  mapScooterPreferences(
    preferences,
    TestDataFetcherDecorator.of("walkReluctance", 4.0)
  );
  assertEquals(4.0, preferences.build().reluctance());
}

@Test
void testDefaultUsedWhenNeitherScooterNorBicycleFieldsProvided() {
  var preferences = ScooterPreferences.of();
  mapScooterPreferences(
    preferences,
    TestDataFetcherDecorator.of(java.util.Map.of())
  );
  var result = preferences.build();
  assertEquals(ScooterPreferences.DEFAULT.speed(), result.speed());
  assertEquals(ScooterPreferences.DEFAULT.reluctance(), result.reluctance());
  assertEquals(ScooterPreferences.DEFAULT.optimizeType(), result.optimizeType());
}

@Test
void testAllScooterFieldsTogetherIgnoreBicycleFields() {
  var preferences = ScooterPreferences.of();
  mapScooterPreferences(
    preferences,
    TestDataFetcherDecorator.of(
      java.util.Map.of(
        "scooterSpeed", 8.0,
        "scooterReluctance", 3.5,
        "scooterOptimisationMethod", VehicleRoutingOptimizeType.SAFEST_STREETS,
        // These should all be ignored:
        "bikeSpeed", 6.0,
        "walkReluctance", 2.0,
        "bicycleOptimisationMethod", VehicleRoutingOptimizeType.TRIANGLE
      )
    )
  );
  var result = preferences.build();
  assertEquals(8.0, result.speed());
  assertEquals(3.5, result.reluctance());
  assertEquals(VehicleRoutingOptimizeType.SAFEST_STREETS, result.optimizeType());
}
```

**Test Coverage**:
- Scooter-specific fields work independently
- Scooter-specific fields take precedence over bicycle fields
- Bicycle fields work as fallback when scooter fields not provided
- Defaults apply when neither field is provided
- All scooter fields together ignore bicycle fields
- Mixed scenarios (some scooter, some bicycle fields)

### Success Criteria:

#### Automated Verification:
- [ ] All new tests pass: `mvn test -Dtest=ScooterPreferencesMapperTest`
- [ ] Existing tests still pass (no regressions)
- [ ] Full test suite passes: `mvn test`
- [ ] Code coverage for ScooterPreferencesMapper is >90%

#### Manual Verification:
- [ ] Review test output to confirm all scenarios are covered
- [ ] Verify test names clearly describe what they test

---

## Phase 4: Update Documentation

### Overview
Document the new scooter preference fields and provide migration guidance for API clients.

### Changes Required:

#### 1. TransModel API Documentation
**File**: `doc/user/apis/TransmodelApi.md`
**Changes**: Add a new section about scooter preferences

Add this new section after the existing content (around line 82):

```markdown
## Scooter Routing Preferences

As of OTP v2.7.0, the TransModel API supports explicit scooter routing preferences.

### Scooter-Specific Fields

For scooter routing (when using `modes: [{mode: SCOOTER}]`), you can now use scooter-specific parameters:

- **`scooterSpeed`** (Float): Maximum scooter speed in m/s. Defaults to 5.0 m/s (~11 mph).
- **`scooterReluctance`** (Float): A measure of how bad scooter travel is compared to being in transit for equal periods of time. Higher values make routing prefer other modes. Defaults to 2.0.
- **`scooterOptimisationMethod`** (BicycleOptimisationMethod): Routing optimization strategy. Options:
  - `safe` (default): Prefer safer streets
  - `flat`: Prefer flat terrain
  - `quick`: Prefer shorter duration
  - `greenways`: Prefer greenways
  - `triangle`: Use triangle factors for custom optimization

### Triangle Optimization

When using `scooterOptimisationMethod: triangle`, you can fine-tune the routing with `triangleFactors`:

```graphql
{
  trip(
    from: {...}
    to: {...}
    modes: [{mode: SCOOTER}]
    scooterOptimisationMethod: triangle
    triangleFactors: {
      time: 0.4
      slope: 0.3
      safety: 0.3
    }
  ) {
    ...
  }
}
```

### Backward Compatibility

For backward compatibility, the API still supports using bicycle field names for scooter routing:

- `bikeSpeed` → applies to scooter speed if no `scooterSpeed` is specified
- `bicycleOptimisationMethod` → applies to scooter optimization if no `scooterOptimisationMethod` is specified
- `walkReluctance` → applies to scooter reluctance if no `scooterReluctance` is specified

**Example - Old style (still works):**
```graphql
{
  trip(
    from: {...}
    to: {...}
    modes: [{mode: SCOOTER}]
    bikeSpeed: 5.0  # Applied to scooter
  ) {
    ...
  }
}
```

**Example - New style (recommended):**
```graphql
{
  trip(
    from: {...}
    to: {...}
    modes: [{mode: SCOOTER}]
    scooterSpeed: 5.0  # Explicit scooter parameter
  ) {
    ...
  }
}
```

### Migration Recommendation

**For new integrations**: Use the scooter-specific fields (`scooterSpeed`, `scooterReluctance`, `scooterOptimisationMethod`) for clearer semantics and to avoid confusion.

**For existing integrations**: No changes required. Your existing queries using `bikeSpeed` for scooter mode will continue to work unchanged. You may migrate to scooter-specific fields at your convenience.

### Related Issues

This enhancement resolves the TransModel API portion of [issue #6572](https://github.com/opentripplanner/OpenTripPlanner/issues/6572).
```

### Success Criteria:

#### Automated Verification:
- [ ] Documentation builds successfully: `mkdocs build` (from doc/user directory)
- [ ] No broken links: Check MkDocs output for warnings

#### Manual Verification:
- [ ] Documentation reads clearly and is technically accurate
- [ ] GraphQL examples are valid and tested in GraphiQL
- [ ] Migration guidance is actionable
- [ ] Cross-reference to issue #6572 is correct

---

## Testing Strategy

### Unit Tests

**File**: `ScooterPreferencesMapperTest.java`

Test matrix covering all fallback scenarios:

| scooterSpeed | bikeSpeed | Expected Result | Test Case |
|--------------|-----------|-----------------|-----------|
| 10.0 | 5.0 | 10.0 (scooter wins) | testScooterSpeedTakesPrecedenceOverBikeSpeed |
| null | 7.0 | 7.0 (bike fallback) | testBikeSpeedUsedAsFallbackWhenScooterSpeedNotProvided |
| null | null | 5.0 (default) | testDefaultUsedWhenNeitherScooterNorBicycleFieldsProvided |
| 8.0 | 6.0 | 8.0 (scooter wins) | testAllScooterFieldsTogetherIgnoreBicycleFields |

Similar matrices for `reluctance` and `optimizeType`.

### Integration Tests

**Manual Testing via GraphiQL** (`http://localhost:8080/otp/transmodel/v3/graphiql`):

1. **Scooter mode with scooter-specific fields**:
   ```graphql
   {
     trip(
       from: {coordinates: {latitude: 59.9139, longitude: 10.7522}}
       to: {coordinates: {latitude: 59.9496, longitude: 10.7564}}
       modes: [{mode: SCOOTER}]
       scooterSpeed: 6.0
       scooterReluctance: 2.5
       scooterOptimisationMethod: safe
     ) {
       tripPatterns {
         legs {
           mode
           distance
           duration
         }
       }
     }
   }
   ```
   **Expected**: Uses scooter speed of 6.0 m/s

2. **Scooter mode with bicycle field fallback** (backward compatibility):
   ```graphql
   {
     trip(
       from: {coordinates: {latitude: 59.9139, longitude: 10.7522}}
       to: {coordinates: {latitude: 59.9496, longitude: 10.7564}}
       modes: [{mode: SCOOTER}]
       bikeSpeed: 7.0
     ) {
       tripPatterns {
         legs {
           mode
           distance
           duration
         }
       }
     }
   }
   ```
   **Expected**: Uses bike speed (7.0 m/s) as fallback for scooter

3. **Scooter mode with defaults** (no speed specified):
   ```graphql
   {
     trip(
       from: {coordinates: {latitude: 59.9139, longitude: 10.7522}}
       to: {coordinates: {latitude: 59.9496, longitude: 10.7564}}
       modes: [{mode: SCOOTER}]
     ) {
       tripPatterns {
         legs {
           mode
           distance
           duration
         }
       }
     }
   }
   ```
   **Expected**: Uses default scooter speed (5.0 m/s)

4. **Both bicycle and scooter modes with distinct speeds**:
   ```graphql
   {
     trip(
       from: {coordinates: {latitude: 59.9139, longitude: 10.7522}}
       to: {coordinates: {latitude: 59.9496, longitude: 10.7564}}
       modes: [{mode: BICYCLE}, {mode: SCOOTER}]
       bikeSpeed: 7.0
       scooterSpeed: 5.0
     ) {
       tripPatterns {
         legs {
           mode
           distance
           duration
         }
       }
     }
   }
   ```
   **Expected**: Bicycle legs use 7.0 m/s, scooter legs use 5.0 m/s

5. **Triangle optimization with scooter**:
   ```graphql
   {
     trip(
       from: {coordinates: {latitude: 59.9139, longitude: 10.7522}}
       to: {coordinates: {latitude: 59.9496, longitude: 10.7564}}
       modes: [{mode: SCOOTER}]
       scooterOptimisationMethod: triangle
       triangleFactors: {time: 0.5, slope: 0.3, safety: 0.2}
     ) {
       tripPatterns {
         legs {
           mode
           distance
           duration
         }
       }
     }
   }
   ```
   **Expected**: Scooter routing uses triangle optimization

### Regression Tests

Verify PR #6599 fix still works correctly:

1. **Scooter rental mode uses correct speed in heuristic**:
   - Run integration test or manual test with scooter rental
   - Verify routing produces sensible paths
   - Confirm no fallback to bicycle speed in heuristic

2. **Different scooter speeds produce different routes**:
   - Test with `scooterSpeed: 3.0` vs `scooterSpeed: 8.0`
   - Should see different route choices or durations

3. **Access/egress routing calculates correctly**:
   - Test trips with scooter access to transit
   - Verify timing calculations are accurate

## Performance Considerations

No performance impact expected:
- Schema changes add minimal parsing overhead (3 new optional fields)
- Mapper changes add small conditional checks (negligible)
- No changes to routing algorithms or graph structure
- Fallback logic executes only once per request during preference mapping

## Migration Notes

### For API Clients

**No breaking changes** - existing clients work unchanged.

**Migration timeline**:
- **Phase 1 (Current)**: Clients use `bikeSpeed` for scooter mode (fallback behavior)
- **Phase 2 (After deployment)**: Clients can optionally migrate to `scooterSpeed` for clarity
- **Phase 3 (Future, not part of this implementation)**: Consider deprecating bicycle field fallback after sufficient adoption period

### For OTP Operators

No configuration changes required. Deploy the updated version and both old and new API clients will work correctly.

## References

- Original issue: [#6572 "Scooter and Bike Rental Speed Configuration"](https://github.com/opentripplanner/OpenTripPlanner/issues/6572)
- Related PR: [#6599 "Don't use bicycle as street routing mode when car or scooter rental is requested"](https://github.com/opentripplanner/OpenTripPlanner/pull/6599)
- Research document: `RESEARCH_6572_TRANSMODEL_SCOOTER_PREFERENCES.md`
- GTFS API reference implementation: `application/src/main/java/org/opentripplanner/apis/gtfs/mapping/routerequest/ScooterPreferencesMapper.java:10-28`
- TransModel API schema: `application/src/main/resources/org/opentripplanner/apis/transmodel/schema.graphql:832-834`
- Internal preferences model: `application/src/main/java/org/opentripplanner/routing/api/request/preference/ScooterPreferences.java:28-32`
