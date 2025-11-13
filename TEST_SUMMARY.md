# Regression Test Summary

## Test Added
**Class**: `LinkingContextFactoryTest`  
**Method**: `nonExistingPlaceIdWithCoordinatesShouldFallbackToCoordinates()`  
**File**: `application/src/test/java/org/opentripplanner/routing/linking/LinkingContextFactoryTest.java`

## Test Status
**Current Status**: ✘ FAILING (as expected - demonstrates the regression)

## Test Results

```
[INFO] ├─ org.opentripplanner.routing.linking.LinkingContextFactoryTest - 0.372 s
[INFO] │  ├─ ✔ locationsShouldBeRoutableWithTheGivenModes - 0.051 s
[INFO] │  ├─ ✔ stationId - 0.006 s
[INFO] │  ├─ ✔ walkingBetterThanTransitException - 0.004 s
[INFO] │  ├─ ✔ stopId - 0.001 s
[INFO] │  ├─ ✔ centroid - 0.001 s
[INFO] │  ├─ ✔ locationNotFoundException - 0.008 s
[INFO] │  ├─ ✔ locationOutsideBoundsException - 0.002 s
[INFO] │  ├─ ✘ nonExistingPlaceIdWithCoordinatesShouldFallbackToCoordinates - 0.004 s
[INFO] │  └─ ✔ coordinates - 0.002 s
```

## Failure Details

```
org.opentripplanner.routing.error.RoutingValidationException: 
RoutingError{code: LOCATION_NOT_FOUND, inputField: FROM_PLACE}
RoutingError{code: LOCATION_NOT_FOUND, inputField: TO_PLACE}
	at org.opentripplanner.routing.linking.LinkingContextFactory.checkIfVerticesFound(LinkingContextFactory.java:402)
	at org.opentripplanner.routing.linking.LinkingContextFactory.create(LinkingContextFactory.java:89)
```

## What the Test Does

The test creates a routing request with:
1. **From location**: Non-existing stop ID (`F:NonExistingStop`) + valid coordinates
2. **To location**: Non-existing stop ID (`F:AnotherNonExisting`) + valid coordinates

Expected behavior: Should use coordinates as fallback and successfully create vertices  
Actual behavior: Throws `RoutingValidationException` with `LOCATION_NOT_FOUND` errors

## Why This Test Matters

This test directly demonstrates the regression reported in the issue:
- Previously, GraphQL queries with both place ID and coordinates would work even if the place ID didn't exist
- After PR #6972 (temporary-vertex-refactor), the same queries now fail with empty results
- This test will pass once the fallback behavior is restored

## Running the Test

```bash
# Run just this test
mvn test -pl application -Dtest=LinkingContextFactoryTest#nonExistingPlaceIdWithCoordinatesShouldFallbackToCoordinates

# Run all LinkingContextFactoryTest tests
mvn test -pl application -Dtest=LinkingContextFactoryTest
```

## Fix Validation

When the fix is implemented, this test should:
1. Stop throwing `RoutingValidationException`
2. Successfully create a `LinkingContext`
3. Return vertices created from the provided coordinates
4. Pass all assertions

The test can be used as a regression guard to ensure this behavior is maintained in the future.
