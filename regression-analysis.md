# Regression Analysis: Empty Trip Results with Non-Existing Place IDs

## Issue Description

GraphQL trip queries that specify both a `place` field (stop/station ID) and `coordinates` now return empty results when the place ID doesn't exist in the graph, whereas previously they would return valid trip results using the provided coordinates as a fallback.

### Example Query
```graphql
{
  trip(
    from: {
      place: "NSR:StopPlace:58195",
      coordinates: {latitude: 59.945270000000001, longitude: 10.778637}
    }
    to: {
      place: "934778",
      coordinates: {latitude: 59.94072499185954, longitude: 10.808358406497828}
    }
    numTripPatterns: 3
    dateTime: "2025-11-13T08:37:51.778+01:00"
    walkSpeed: 1.3
    arriveBy: false
  ) {
    tripPatterns {
      expectedStartTime
      duration
      walkDistance
      legs {
        mode
        distance
      }
    }
  }
}
```

### Current Behavior
Returns empty results:
```json
{
  "data": {
    "trip": {
      "tripPatterns": []
    }
  }
}
```

### Expected Behavior
Should return trip results using the provided coordinates when the place ID doesn't exist in the graph.

## Root Cause Analysis

### Commit That Introduced the Regression

**PR #6972**: "Add validation for visit via locations with coordinates when attempting to link them to graph"
- Merge commit: `e36bf0efcf63c71b43ba5cdb5b31b02b23470b39`
- Merged: November 11, 2025
- Branch: `temporary-vertex-refactor`

This was a major refactoring that:
- Moved vertex linking logic from `TemporaryVerticesContainer` to new `LinkingContextFactory`
- Introduced `VertexCreationService` for vertex creation
- Added validation that throws `RoutingValidationException` when vertices cannot be linked to the graph

### Technical Details

The regression occurs in `LinkingContextFactory.getStreetVerticesForLocation()` method:

#### New Code (Problematic)
```java
private Set<Vertex> getStreetVerticesForLocation(
    TemporaryVerticesContainer container,
    GenericLocation location,
    EnumSet<StreetMode> streetModes,
    LocationType type
) {
    if (!location.isSpecified()) {
        return Set.of();
    }

    // ... mode setup ...

    var results = new HashSet<Vertex>();
    if (location.stopId != null) {
        if (!modes.stream().allMatch(TraverseMode::isInCar)) {
            results.addAll(getStreetVerticesForStop(location));  // Returns empty set if not found
        }
        // ... car handling ...
    } else if (location.getCoordinate() != null) {  // ← NEVER REACHED when stopId != null
        // Connect a temporary vertex from coordinate to graph
        results.add(
            vertexCreationService.createVertexFromCoordinate(...)
        );
    }

    return results;  // Returns empty set when stop not found
}
```

#### Old Code (Working)
```java
private Set<Vertex> getStreetVerticesForLocation(
    GenericLocation location,
    StreetMode streetMode,
    boolean endVertex,
    Set<DisposableEdgeCollection> tempEdges
) {
    if (!location.isSpecified()) {
        return null;
    }

    TraverseMode mode = getTraverseModeForLinker(streetMode, endVertex);

    if (mode.isInCar()) {
        // Handle car mode...
    } else {
        if (location.stopId != null) {
            // Try to find stop vertex
            var stopVertex = graph.findStopVertex(location.stopId);
            if (stopVertex.isPresent()) {
                return Set.of(stopVertex.get());
            }
            // Try other stop-related lookups...
        }
        // NOT in an else block - falls through to coordinate check
    }

    // Check if coordinate is provided and connect it to graph
    if (location.getCoordinate() != null) {  // ← FALLBACK: Reached even when stopId != null
        return Set.of(
            createVertexFromCoordinate(...)
        );
    }

    return null;
}
```

### The Critical Difference

**New implementation**: Uses mutually exclusive `if/else if` structure
- When `location.stopId != null`, takes the first branch
- If stop ID is not found in graph, `getStreetVerticesForStop()` returns empty set
- The `else if (location.getCoordinate() != null)` branch is **never evaluated**
- Returns empty `results` set

**Old implementation**: Non-mutually-exclusive checks
- Checks for stop ID within a conditional block
- If stop not found, **falls through** to coordinate check
- The coordinate check is independent and always evaluated when coordinates exist
- Returns vertex created from coordinates as fallback

### Validation Chain

When empty vertex set is returned, the following happens:

1. `LinkingContextFactory.checkIfVerticesFound()` validates vertices were found
2. `isDisconnected()` returns `true` when vertices set is empty:
   ```java
   private static boolean isDisconnected(Set<Vertex> vertices, LocationType type) {
       if (vertices.isEmpty()) {
           return true;  // ← Triggers for non-existent place IDs
       }
       // ...
   }
   ```
3. Throws `RoutingValidationException` with routing error
4. `TransmodelGraphQLPlanner.plan()` catches exception and returns empty trip plan
5. GraphQL response contains empty `tripPatterns` array

## Impact

This regression affects any GraphQL query that:
1. Specifies both a `place` field and `coordinates` in from/to locations
2. References a place ID that doesn't exist in the loaded transit data
3. Expects the coordinates to be used as a fallback

### Affected Use Cases
- Queries using outdated or incorrect stop IDs with coordinate fallback
- Systems that provide both identifiers and coordinates for robustness
- Cross-region queries where stop IDs may not always be available

## Additional Context

### Related Changes

The refactor also introduced another fix in PR #6961 ("Fix NPE in TransModel trip plan query", commit `b45d54d986`), which modified how empty trip plans are created when validation fails. This change ensures the system doesn't crash with NPE, but it doesn't address the coordinate fallback regression.

### Files Affected

Primary file with the issue:
- `application/src/main/java/org/opentripplanner/routing/linking/LinkingContextFactory.java`

Related validation and error handling:
- `application/src/main/java/org/opentripplanner/routing/error/RoutingValidationException.java`
- `application/src/main/java/org/opentripplanner/apis/transmodel/TransmodelGraphQLPlanner.java`
- `application/src/main/java/org/opentripplanner/routing/algorithm/mapping/TripPlanMapper.java`

## Recommendation

The fix should modify `LinkingContextFactory.getStreetVerticesForLocation()` to restore the fallback behavior when a place ID is not found but coordinates are available. The logic should:

1. Attempt to find vertices using the place ID (if provided)
2. If no vertices found AND coordinates are available, create vertex from coordinates
3. Only return empty set if both place ID lookup fails AND no coordinates are available

This would restore the previous behavior while maintaining the validation improvements from the refactor.
