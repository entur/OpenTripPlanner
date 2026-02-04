---
date: 2025-12-08T12:00:00+01:00
researcher: Claude Code
git_commit: 915b033e7d76ef9ec02bed9715a9bf29c9088052
branch: fix/transport-mode-filter-bug
repository: OpenTripPlanner
topic: "transportMode filter not applied for multi-mode NeTEx patterns"
tags: [research, codebase, filtering, netex, transmodel-api, transit-modes]
status: complete
last_updated: 2025-12-08
last_updated_by: Claude Code
---

# Research: transportMode Filter Bug in Multi-Mode NeTEx Patterns

**Date**: 2025-12-08T12:00:00+01:00
**Researcher**: Claude Code
**Git Commit**: 915b033e7d76ef9ec02bed9715a9bf29c9088052
**Branch**: fix/transport-mode-filter-bug
**Repository**: OpenTripPlanner

## Research Question

Why is the `transportMode` filter not applied when a service journey in NeTEx has multiple submodes for its patterns, and there is no `transportSubMode` filter defined in the query?

**Expected behavior:** When the Transmodel trip query has `transportMode` set to `BUS` with `transportSubModes` unset, only trip alternatives with `BUS` as transportMode should be returned.

**Observed behavior:** Trip alternatives with transportModes other than `BUS` are returned.

## Summary

The bug occurs because the `hasSubModeFilters` flag incorrectly determines whether TripTimes-level filtering should be applied for patterns with multiple modes. When only `transportMode` is specified (without `transportSubModes`), the system creates an `AllowMainModeFilter` which returns `false` for `isSubMode()`, causing trip-level mode filtering to be skipped for multi-mode patterns.

The core issue is a semantic mismatch: the variable `hasSubModeFilters` controls whether mode filtering happens at the trip level for multi-mode patterns, but it only returns `true` when a **submode** is specified, not when a **main mode** filter needs trip-level enforcement.

## Detailed Findings

### 1. TripPattern Construction (NeTEx Import)

**File:** `application/src/main/java/org/opentripplanner/netex/mapping/TripPatternMapper.java:218-250`

When creating TripPatterns from NeTEx data, the mapper collects all modes and submodes from the trips in a JourneyPattern:

```java
var tripPatternModes = new HashSet<TransitMode>();
var tripPatternSubmodes = new HashSet<SubMode>();
for (Trip trip : trips) {
  tripPatternModes.add(trip.getMode());
  tripPatternSubmodes.add(trip.getNetexSubMode());
}

boolean hasMultipleModes = tripPatternModes.size() > 1;
boolean hasMultipleSubmodes = tripPatternSubmodes.size() > 1;

var tripPattern = TripPattern.of(idFactory.createId(journeyPattern.getId()))
  .withRoute(lookupRoute(journeyPattern))
  .withStopPattern(stopPattern)
  .withMode(trips.get(0).getMode())                           // Only first trip's mode
  .withNetexSubmode(trips.get(0).getNetexSubMode())           // Only first trip's submode
  .withContainsMultipleModes(hasMultipleModes || hasMultipleSubmodes)  // Key flag
  // ...
  .build();
```

**Key observations:**
- The pattern only stores the **first** trip's mode/submode
- The `containsMultipleModes` flag is set if trips have different modes OR different submodes
- Issues are logged when multiple modes/submodes are detected

### 2. API Request Mapping (Transmodel)

**File:** `application/src/main/java/org/opentripplanner/apis/transmodel/mapping/SelectRequestMapper.java:40-59`

When mapping the GraphQL `transportModes` input:

```java
if (input.containsKey("transportModes")) {
  var tModes = new ArrayList<MainAndSubMode>();

  var transportModes = (List<Map<String, ?>>) input.get("transportModes");
  for (Map<String, ?> modeWithSubModes : transportModes) {
    var mainMode = (TransitMode) modeWithSubModes.get("transportMode");
    if (modeWithSubModes.containsKey("transportSubModes")) {
      var transportSubModes = (List<TransmodelTransportSubmode>) modeWithSubModes.get(
        "transportSubModes"
      );

      for (var subMode : transportSubModes) {
        tModes.add(new MainAndSubMode(mainMode, SubMode.of(subMode.getValue())));
      }
    } else {
      tModes.add(new MainAndSubMode(mainMode));  // No submode - null
    }
  }
  selectRequestBuilder.withTransportModes(tModes);
}
```

**Key observation:** When `transportSubModes` is NOT present, `MainAndSubMode(mainMode)` is created with a **null** submode.

### 3. Filter Creation

**File:** `application/src/main/java/org/opentripplanner/model/modes/FilterFactory.java:25-31`

The filter factory creates different filter types based on whether a submode is specified:

```java
static AllowTransitModeFilter of(MainAndSubMode mode) {
  if (mode.subMode() == null) {
    return new AllowMainModeFilter(mode.mainMode());   // When no submode
  } else {
    return new AllowMainAndSubModeFilter(mode);        // When submode specified
  }
}
```

### 4. The isSubMode() Behavior - Root Cause

The `isSubMode()` method determines whether a filter requires trip-level filtering:

| Filter Class | `isSubMode()` return value | File Location |
|--------------|---------------------------|---------------|
| `AllowMainModeFilter` | `false` (default) | `AllowMainModeFilter.java` - no override |
| `AllowMainAndSubModeFilter` | `true` | `AllowMainAndSubModeFilter.java:26-28` |
| `AllowMainAndSubModesFilter` | `true` | `AllowMainAndSubModesFilter.java:31-33` |
| `FilterCollection` | `true` if any sub-filter returns `true` | `FilterCollection.java:38-40` |

**File:** `application/src/main/java/org/opentripplanner/model/modes/AllowMainModeFilter.java`

```java
class AllowMainModeFilter implements AllowTransitModeFilter {
  private final TransitMode mainMode;

  @Override
  public boolean match(TransitMode transitMode, SubMode ignore) {
    return mainMode == transitMode;
  }

  // NOTE: Does NOT override isSubMode(), so returns false (default)
}
```

**File:** `application/src/main/java/org/opentripplanner/model/modes/AllowMainAndSubModeFilter.java:26-28`

```java
@Override
public boolean isSubMode() {
  return true;
}
```

### 5. Pattern-Level Filtering

**File:** `application/src/main/java/org/opentripplanner/routing/api/request/request/filter/SelectRequest.java:104-136`

```java
private boolean matchesPattern(TripPattern tripPattern, boolean maybeValue) {
  // ... agency and route checks ...

  if (this.transportModeFilter != null) {
    // If the pattern contains multiple modes, we will do the filtering in
    // SelectRequest.matches(TripTimes)
    if (tripPattern.getContainsMultipleModes()) {
      return maybeValue;  // Returns true for select, deferring to TripTimes filtering
    }
    if (!this.transportModeFilter.match(tripPattern.getMode(), tripPattern.getNetexSubmode())) {
      return false;
    }
  }

  return true;
}
```

**Key insight:** When a pattern has `containsMultipleModes == true`, pattern-level filtering is **skipped** and deferred to TripTimes-level filtering.

### 6. TransitFilterRequest.isSubModePredicate()

**File:** `application/src/main/java/org/opentripplanner/routing/api/request/request/filter/TransitFilterRequest.java:43-62`

```java
@Override
public boolean isSubModePredicate() {
  for (var selectRequest : select) {
    if (
      selectRequest.transportModeFilter() != null &&
      selectRequest.transportModeFilter().isSubMode()  // Calls AllowMainModeFilter.isSubMode()
    ) {
      return true;
    }
  }

  for (var selectRequest : not) {
    if (
      selectRequest.transportModeFilter() != null &&
      selectRequest.transportModeFilter().isSubMode()
    ) {
      return true;
    }
  }
  return false;
}
```

### 7. Trip-Level Filtering Decision

**File:** `application/src/main/java/org/opentripplanner/routing/algorithm/raptoradapter/transit/request/DefaultTransitDataProviderFilter.java`

**Line 52 - Flag initialization:**
```java
hasSubModeFilters = builder.filters().stream().anyMatch(TransitFilter::isSubModePredicate);
```

**Lines 73-81 - createTripFilter:**
```java
@Override
@Nullable
public Predicate<TripTimes> createTripFilter(TripPattern tripPattern) {
  for (TransitFilter filter : filters) {
    if (filter.matchTripPattern(tripPattern)) {
      var applyTripTimesFilters = hasSubModeFilters && tripPattern.getContainsMultipleModes();
      return tripTimes -> tripTimesPredicate(tripTimes, applyTripTimesFilters);
    }
  }
  return null;
}
```

**Lines 83-130 - tripTimesPredicate:**
```java
private boolean tripTimesPredicate(TripTimes tripTimes, boolean applyTripTimesFilters) {
  final Trip trip = tripTimes.getTrip();

  // ... other checks (bikes, cars, wheelchair, cancellations, banned trips) ...

  // Trip has to match with at least one predicate in order to be included in search. We only have
  // to this if we have mode specific filters, and not all trips on the pattern have the same
  // mode, since that's the only thing that is trip specific
  if (applyTripTimesFilters) {
    for (TransitFilter f : filters) {
      if (f.matchTripTimes(tripTimes)) {
        return true;
      }
    }
    return false;
  }

  return true;  // When applyTripTimesFilters is false, ALL trips pass through
}
```

## The Bug Mechanism - Step by Step

1. **User query:** `transportMode: BUS` (without `transportSubModes`)

2. **SelectRequestMapper** creates `MainAndSubMode(BUS)` with null submode

3. **FilterFactory.of()** creates `AllowMainModeFilter(BUS)`

4. **AllowMainModeFilter.isSubMode()** returns `false` (uses default implementation)

5. **TransitFilterRequest.isSubModePredicate()** returns `false`

6. **DefaultTransitDataProviderFilter constructor:** `hasSubModeFilters = false`

7. **For patterns with `containsMultipleModes == true`:**
   - `SelectRequest.matchesPattern()` returns `true` (defers to TripTimes)
   - `createTripFilter()` calculates: `applyTripTimesFilters = false && true = false`
   - `tripTimesPredicate()` returns `true` without checking mode filter

8. **Result:** All trips in multi-mode patterns pass through, regardless of their actual mode

## Code References

| File | Lines | Description |
|------|-------|-------------|
| `application/src/main/java/org/opentripplanner/netex/mapping/TripPatternMapper.java` | 218-250 | Sets `containsMultipleModes` flag during NeTEx import |
| `application/src/main/java/org/opentripplanner/apis/transmodel/mapping/SelectRequestMapper.java` | 40-59 | Maps GraphQL input to `MainAndSubMode` |
| `application/src/main/java/org/opentripplanner/model/modes/FilterFactory.java` | 25-31, 40-103 | Creates appropriate filter based on submode presence |
| `application/src/main/java/org/opentripplanner/model/modes/AllowMainModeFilter.java` | 7-45 | Main mode filter - no `isSubMode()` override |
| `application/src/main/java/org/opentripplanner/model/modes/AllowMainAndSubModeFilter.java` | 26-28 | Submode filter - `isSubMode()` returns true |
| `application/src/main/java/org/opentripplanner/model/modes/AllowMainAndSubModesFilter.java` | 31-33 | Multi-submode filter - `isSubMode()` returns true |
| `application/src/main/java/org/opentripplanner/model/modes/FilterCollection.java` | 38-40 | Collection filter - delegates to sub-filters |
| `application/src/main/java/org/opentripplanner/routing/api/request/request/filter/SelectRequest.java` | 104-136 | Pattern matching with multi-mode deferral |
| `application/src/main/java/org/opentripplanner/routing/api/request/request/filter/TransitFilterRequest.java` | 43-62 | `isSubModePredicate()` implementation |
| `application/src/main/java/org/opentripplanner/routing/algorithm/raptoradapter/transit/request/DefaultTransitDataProviderFilter.java` | 52, 73-81, 117-127 | Controls when mode filtering is applied |

## Architecture Documentation

### Filtering Architecture Overview

The transit filtering system has a two-level architecture:

1. **Pattern-level filtering** (`SelectRequest.matchesPattern()`)
   - Fast filtering based on pattern metadata
   - Can definitively exclude patterns that don't match
   - For multi-mode patterns, defers to trip-level filtering

2. **Trip-level filtering** (`tripTimesPredicate()`)
   - More expensive, per-trip evaluation
   - Only invoked when necessary
   - Controlled by `applyTripTimesFilters` flag

### Filter Type Hierarchy

```
AllowTransitModeFilter (interface)
├── AllowAllModesFilter          - matches everything
├── AllowMainModeFilter          - matches by main mode only, isSubMode()=false
├── AllowMainModesFilter         - matches multiple main modes, isSubMode()=false
├── AllowMainAndSubModeFilter    - matches mode+submode, isSubMode()=true
├── AllowMainAndSubModesFilter   - matches mode+multiple submodes, isSubMode()=true
└── FilterCollection             - combines filters, isSubMode()=any child true
```

### The Design Assumption

The current design assumes:
- If only main modes are specified, pattern-level filtering is sufficient
- Trip-level mode filtering is only needed when submodes are involved

This assumption breaks down when:
- A pattern contains trips with different main modes (e.g., BUS and COACH)
- The pattern is marked as `containsMultipleModes = true`
- Pattern-level filtering defers to trip-level
- But trip-level filtering is skipped because `hasSubModeFilters = false`

## Open Questions

1. **Naming:** Should `hasSubModeFilters` be renamed to better reflect its purpose (e.g., `requiresTripLevelModeFiltering`)?

2. **Condition fix:** Should the condition `hasSubModeFilters && tripPattern.getContainsMultipleModes()` be changed to check for any transport mode filter, not just submode filters?

3. **Alternative approach:** Should `AllowMainModeFilter.isSubMode()` return `true` to indicate it needs trip-level filtering for multi-mode patterns?

4. **Test coverage:** Are there existing tests for the scenario of filtering by main mode on patterns with multiple modes?
