# Regression Test Documentation

## Test: `nonExistingPlaceIdWithCoordinatesShouldFallbackToCoordinates`

### Location
`application/src/test/java/org/opentripplanner/routing/linking/LinkingContextFactoryTest.java`

### Purpose
This test demonstrates and verifies the regression where queries with both a non-existing place ID and valid coordinates fail instead of falling back to using the coordinates.

### Test Setup
The test creates a `LinkingContextRequest` with:
- **From location**: A `GenericLocation` with a non-existing stop ID (`F:NonExistingStop`) and valid coordinates matching `stopA`
- **To location**: A `GenericLocation` with another non-existing stop ID (`F:AnotherNonExisting`) and valid coordinates matching `stopD`
- **Direct mode**: `WALK`

### Current Behavior (Failing Test)
The test currently **fails** with the following exception:

```
org.opentripplanner.routing.error.RoutingValidationException: 
RoutingError{code: LOCATION_NOT_FOUND, inputField: FROM_PLACE}
RoutingError{code: LOCATION_NOT_FOUND, inputField: TO_PLACE}
```

This demonstrates the regression: even though both locations have valid coordinates that can be linked to the graph, the system throws a validation exception because the place IDs don't exist.

### Expected Behavior (When Bug is Fixed)
When the bug is fixed, the test should **pass** by:
1. Attempting to find vertices using the place IDs
2. When the place IDs are not found, falling back to creating temporary vertices from the provided coordinates
3. Successfully returning a `LinkingContext` with vertices for both locations
4. The test assertions verify that both `fromVertices` and `toVertices` contain exactly one vertex each

### How to Run the Test

From the project root:
```bash
mvn test -pl application -Dtest=LinkingContextFactoryTest#nonExistingPlaceIdWithCoordinatesShouldFallbackToCoordinates
```

Or run all tests in the class:
```bash
mvn test -pl application -Dtest=LinkingContextFactoryTest
```

### Technical Details

The test uses the existing test infrastructure:
- **Graph**: Built with a simple street network and several stops (stopA, stopB, stopC, stopD)
- **VertexCreationService**: Configured with `VertexLinkerTestFactory` for vertex creation
- **LinkingContextFactory**: The system under test, which should handle the fallback logic

### Root Cause Verified by Test

The test fails because `LinkingContextFactory.getStreetVerticesForLocation()` uses an `if/else if` structure:

```java
if (location.stopId != null) {
    // Try to find stop - returns empty set if not found
    results.addAll(getStreetVerticesForStop(location));
} else if (location.getCoordinate() != null) {  // Never reached when stopId != null
    // Create vertex from coordinates
    results.add(vertexCreationService.createVertexFromCoordinate(...));
}
```

When `stopId != null` but the stop doesn't exist:
1. First branch is taken
2. `getStreetVerticesForStop()` returns empty set
3. Second branch (coordinate fallback) is never evaluated
4. Empty set triggers validation failure

### Success Criteria

The test will pass when the code is modified to:
1. Attempt to find vertices using the place ID
2. Check if the result is empty AND coordinates are available
3. If so, fall back to creating a vertex from coordinates
4. Return the created vertex instead of an empty set

This restores the previous behavior where coordinates serve as a fallback when place IDs are not found in the graph.
