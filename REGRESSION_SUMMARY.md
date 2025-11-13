# Regression Analysis and Test - Summary

## Overview

This document summarizes the investigation and testing of a regression in OpenTripPlanner where GraphQL trip queries that specify both a `place` field and `coordinates` return empty results when the place ID doesn't exist in the graph.

## Files Created

1. **regression-analysis.md** - Detailed analysis of the regression
2. **regression-test-documentation.md** - Documentation of the test case
3. **TEST_SUMMARY.md** - Quick reference for the test

## The Regression

### What Changed
After PR #6972 (temporary-vertex-refactor, merged November 11, 2025), queries with both a non-existing place ID and valid coordinates fail instead of using the coordinates as a fallback.

### Root Cause
In `LinkingContextFactory.getStreetVerticesForLocation()`, the refactored code uses mutually exclusive `if/else if`:

```java
if (location.stopId != null) {
    results.addAll(getStreetVerticesForStop(location));  // Returns empty if not found
} else if (location.getCoordinate() != null) {  // NEVER REACHED when stopId != null
    results.add(vertexCreationService.createVertexFromCoordinate(...));
}
return results;  // Empty set when stop not found → validation fails
```

The old code allowed falling through to the coordinate check even when a stopId was present.

## The Test

### Location
`application/src/test/java/org/opentripplanner/routing/linking/LinkingContextFactoryTest.java`

### Test Method
```java
@Test
void nonExistingPlaceIdWithCoordinatesShouldFallbackToCoordinates()
```

### What It Tests
Creates a `LinkingContextRequest` with locations that have:
- Non-existing place IDs (`F:NonExistingStop` and `F:AnotherNonExisting`)
- Valid coordinates that can be linked to the graph

### Expected vs Actual Behavior

**Expected (when bug is fixed):**
- Attempt to find stop using place ID
- When not found, fall back to creating vertex from coordinates
- Return successful `LinkingContext` with vertices

**Actual (current - demonstrates regression):**
- Attempt to find stop using place ID
- When not found, return empty vertex set
- Throw `RoutingValidationException` with `LOCATION_NOT_FOUND` errors

### Test Output

```
✘ nonExistingPlaceIdWithCoordinatesShouldFallbackToCoordinates - 0.004 s

org.opentripplanner.routing.error.RoutingValidationException: 
RoutingError{code: LOCATION_NOT_FOUND, inputField: FROM_PLACE}
RoutingError{code: LOCATION_NOT_FOUND, inputField: TO_PLACE}
```

## Running the Test

```bash
# From project root
mvn test -pl application -Dtest=LinkingContextFactoryTest#nonExistingPlaceIdWithCoordinatesShouldFallbackToCoordinates
```

## Impact

This regression affects:
- GraphQL queries with both place ID and coordinates (common for robustness)
- Systems using outdated or incorrect stop IDs with coordinate fallback
- Cross-region queries where stop IDs may not always be available

## Next Steps

To fix this regression:

1. Modify `LinkingContextFactory.getStreetVerticesForLocation()` to restore fallback behavior
2. When place ID lookup returns empty AND coordinates are available, create vertex from coordinates
3. Verify the test passes after the fix
4. Run full test suite to ensure no other regressions

The test serves as both:
- **Demonstration** of the current bug
- **Regression guard** to prevent this issue from reoccurring

## Files Modified

- `application/src/test/java/org/opentripplanner/routing/linking/LinkingContextFactoryTest.java` (1 test method added)

## Test Integration

The new test integrates cleanly with existing tests:
- Uses same test infrastructure and setup
- Follows same patterns as other tests in the class
- All 8 existing tests still pass
- New test is the only failing test (as expected)
