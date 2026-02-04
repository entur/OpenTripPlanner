# Transport Mode Filter Bug Fix Implementation Plan

## Overview

Fix a bug where the `transportMode` filter is not properly applied when filtering trips on patterns with multiple modes (`containsMultipleModes=true`), and only a main mode (without submodes) is specified in the query.

**Root Cause**: The `hasSubModeFilters` flag in `DefaultTransitDataProviderFilter` only returns `true` when a **submode** is specified, not when a **main mode** filter needs trip-level enforcement on multi-mode patterns. This causes trip-level filtering to be skipped for multi-mode patterns when filtering by main mode only.

## Current State Analysis

### The Bug Flow

1. User query: `transportMode: BUS` (without `transportSubModes`)
2. `SelectRequestMapper` creates `MainAndSubMode(BUS)` with null submode
3. `FilterFactory.of()` creates `AllowMainModeFilter(BUS)`
4. **`AllowMainModeFilter.isSubMode()` returns `false`** (uses default implementation)
5. `TransitFilterRequest.isSubModePredicate()` returns `false`
6. `DefaultTransitDataProviderFilter`: `hasSubModeFilters = false`
7. For patterns with `containsMultipleModes == true`:
   - `SelectRequest.matchesPattern()` returns `true` (defers to TripTimes)
   - `createTripFilter()` calculates: `applyTripTimesFilters = false && true = false`
   - `tripTimesPredicate()` returns `true` without checking mode filter
8. **Result**: All trips in multi-mode patterns pass through, regardless of their actual mode

### Key Files

| File | Lines | Role |
|------|-------|------|
| `AllowMainModeFilter.java` | N/A | Filter class - no `isSubMode()` override (returns `false`) |
| `AllowTransitModeFilter.java` | 22-24 | Interface - default `isSubMode()` returns `false` |
| `DefaultTransitDataProviderFilter.java` | 41, 52, 76 | Uses `hasSubModeFilters` to decide trip-level filtering |
| `TransitFilterRequest.java` | 43-62 | `isSubModePredicate()` checks if any filter needs trip-level filtering |

## Desired End State

When a user specifies `transportMode: BUS` (main mode only, no submode):
- Trips with `mode=BUS` should be included
- Trips with other modes (e.g., `COACH`, `RAIL`) should be **excluded**
- This must work correctly even when the `TripPattern` has `containsMultipleModes=true`

### Verification

1. Unit tests pass demonstrating the bug is fixed
2. Existing tests continue to pass (no regressions)
3. The fix handles both main mode filtering and submode filtering on multi-mode patterns

## What We're NOT Doing

- Changing the NeTEx import logic for how `containsMultipleModes` is set
- Modifying the pattern-level filtering behavior
- Changing the API request mapping
- Refactoring the filter architecture beyond the minimal fix

## Implementation Approach

The fix requires ensuring that when a mode filter exists and a pattern has multiple modes, trip-level filtering is always applied - not just when submodes are specified.

**Two-pronged approach:**
1. **Phase 1**: Write failing tests that demonstrate the bug
2. **Phase 2**: Fix the bug by renaming and adjusting the flag semantics

## Phase 1: Add Failing Tests

### Overview
Add unit tests that specifically test filtering by main mode only on patterns with `containsMultipleModes=true`.

### Changes Required:

#### 1. DefaultTransitDataProviderFilterTest.java
**File**: `application/src/test/java/org/opentripplanner/routing/algorithm/raptoradapter/transit/request/DefaultTransitDataProviderFilterTest.java`

**Add new test method** after `transitModeFilteringTest()` (around line 381):

```java
/**
 * Test filtering by main mode only (no submode) on a pattern with multiple modes.
 * This tests the scenario where a TripPattern contains trips with different modes
 * (e.g., BUS and COACH), and we want to filter by just BUS.
 */
@Test
void filterByMainModeOnMultiModePattern() {
  // Create a pattern with containsMultipleModes=true
  // The pattern itself is marked as BUS, but contains multiple modes
  var busTrip = createPatternAndTimesWithMultipleModes(
    TimetableRepositoryForTest.id("T1"),
    TransitMode.BUS
  );

  var coachTrip = createPatternAndTimesWithMultipleModes(
    TimetableRepositoryForTest.id("T2"),
    TransitMode.COACH
  );

  // Filter for BUS only (no submode specified)
  var filter = DefaultTransitDataProviderFilter.of()
    .withFilters(filterForMode(TransitMode.BUS))
    .build();

  // BUS trip should pass the filter
  assertTrue(validate(filter, busTrip), "BUS trip should be included when filtering for BUS");

  // COACH trip should NOT pass the filter - this is the bug!
  // Currently this incorrectly returns true because trip-level filtering is skipped
  assertFalse(validate(filter, coachTrip), "COACH trip should be excluded when filtering for BUS");
}

/**
 * Test filtering by main mode on a pattern with multiple modes, verifying that
 * multiple different modes can be correctly filtered.
 */
@Test
void filterByDifferentMainModesOnMultiModePattern() {
  var busTrip = createPatternAndTimesWithMultipleModes(
    TimetableRepositoryForTest.id("T1"),
    TransitMode.BUS
  );

  var railTrip = createPatternAndTimesWithMultipleModes(
    TimetableRepositoryForTest.id("T2"),
    TransitMode.RAIL
  );

  // Filter for RAIL only
  var filter = DefaultTransitDataProviderFilter.of()
    .withFilters(filterForMode(TransitMode.RAIL))
    .build();

  assertFalse(validate(filter, busTrip), "BUS trip should be excluded when filtering for RAIL");
  assertTrue(validate(filter, railTrip), "RAIL trip should be included when filtering for RAIL");
}
```

**Add new helper method** after `createPatternAndTimesWithSubmode()` (around line 976):

```java
/**
 * Creates a PatternAndTimes where the pattern has containsMultipleModes=true,
 * simulating a NeTEx JourneyPattern with trips of different modes.
 * The trip's mode is set to the specified mode.
 */
private PatternAndTimes createPatternAndTimesWithMultipleModes(
  FeedScopedId tripId,
  TransitMode tripMode
) {
  Trip trip = Trip.of(tripId)
    .withRoute(ROUTE)
    .withMode(tripMode)
    .withBikesAllowed(BikeAccess.NOT_ALLOWED)
    .withCarsAllowed(CarAccess.NOT_ALLOWED)
    .withWheelchairBoarding(Accessibility.NOT_POSSIBLE)
    .build();

  StopTime stopTime = new StopTime();
  stopTime.setStop(STOP_FOR_TEST);
  stopTime.setArrivalTime(60);
  stopTime.setDepartureTime(60);
  stopTime.setStopSequence(0);

  StopPattern stopPattern = new StopPattern(List.of(stopTime));

  // Pattern is marked as BUS (first trip's mode) but contains multiple modes
  var tripPattern = TripPattern.of(TimetableRepositoryForTest.id("P1"))
    .withRoute(ROUTE)
    .withStopPattern(stopPattern)
    .withMode(TransitMode.BUS)  // Pattern mode is BUS (first trip in the pattern)
    .withContainsMultipleModes(true)  // But pattern contains trips with different modes
    .build();

  TripTimes tripTimes = TripTimesFactory.tripTimes(
    trip,
    List.of(new StopTime()),
    new Deduplicator()
  );

  return new PatternAndTimes(tripPattern, tripTimes);
}
```

### Success Criteria:

#### Automated Verification:
- [ ] Tests compile: `mvn test-compile -pl application`
- [ ] Running the new tests demonstrates the bug: `mvn test -pl application -Dtest=DefaultTransitDataProviderFilterTest#filterByMainModeOnMultiModePattern` should **FAIL** (COACH trip passes when it shouldn't)
- [ ] Running `mvn test -pl application -Dtest=DefaultTransitDataProviderFilterTest#filterByDifferentMainModesOnMultiModePattern` should **FAIL**

---

## Phase 2: Fix the Bug

### Overview
Change the semantics of `hasSubModeFilters` to correctly indicate when trip-level mode filtering is needed on multi-mode patterns.

### Option A: Rename and Expand isSubMode() Semantics (Recommended)

The cleanest fix is to rename the method and adjust its semantics to indicate "requires trip-level mode filtering" rather than "is a submode filter".

#### Changes Required:

#### 1. AllowTransitModeFilter.java
**File**: `application/src/main/java/org/opentripplanner/model/modes/AllowTransitModeFilter.java`

**Change**: Rename the method and update the default to `true` for mode filters:

```java
/**
 * Used to filter out modes for routing requests.
 */
public interface AllowTransitModeFilter extends Serializable {
  static AllowTransitModeFilter of(Collection<MainAndSubMode> modes) {
    return FilterFactory.create(modes);
  }

  /**
   * Check if this filter allows the provided TransitMode
   */
  boolean match(TransitMode transitMode, SubMode netexSubMode);

  /**
   * Returns true if this filter requires trip-level filtering for patterns
   * that contain multiple modes. This is true for:
   * - Filters that check submodes (need to verify each trip's submode)
   * - Filters that check main modes (need to verify each trip's mode on multi-mode patterns)
   *
   * Returns false only for AllowAllModesFilter which allows everything.
   */
  default boolean requiresTripLevelFiltering() {
    return true;
  }
}
```

#### 2. AllowAllModesFilter.java
**File**: `application/src/main/java/org/opentripplanner/model/modes/AllowAllModesFilter.java`

**Change**: Override to return `false` (doesn't need trip-level filtering):

```java
@Override
public boolean requiresTripLevelFiltering() {
  return false;
}
```

#### 3. AllowMainModeFilter.java
**File**: `application/src/main/java/org/opentripplanner/model/modes/AllowMainModeFilter.java`

**No change needed** - will use the new default of `true`.

#### 4. AllowMainModesFilter.java
**File**: `application/src/main/java/org/opentripplanner/model/modes/AllowMainModesFilter.java`

**No change needed** - will use the new default of `true`.

#### 5. AllowMainAndSubModeFilter.java
**File**: `application/src/main/java/org/opentripplanner/model/modes/AllowMainAndSubModeFilter.java`

**Change**: Rename the method:

```java
@Override
public boolean requiresTripLevelFiltering() {
  return true;
}
```

#### 6. AllowMainAndSubModesFilter.java
**File**: `application/src/main/java/org/opentripplanner/model/modes/AllowMainAndSubModesFilter.java`

**Change**: Rename the method:

```java
@Override
public boolean requiresTripLevelFiltering() {
  return true;
}
```

#### 7. FilterCollection.java
**File**: `application/src/main/java/org/opentripplanner/model/modes/FilterCollection.java`

**Change**: Rename the method:

```java
@Override
public boolean requiresTripLevelFiltering() {
  for (var filter : filters) {
    if (filter.requiresTripLevelFiltering()) {
      return true;
    }
  }
  return false;
}
```

#### 8. TransitFilter.java
**File**: `application/src/main/java/org/opentripplanner/routing/api/request/request/filter/TransitFilter.java`

**Change**: Rename the method in the interface:

```java
/**
 * Returns true if this filter requires trip-level filtering for patterns
 * that contain multiple modes.
 */
boolean requiresTripLevelFiltering();
```

#### 9. TransitFilterRequest.java
**File**: `application/src/main/java/org/opentripplanner/routing/api/request/request/filter/TransitFilterRequest.java`

**Change**: Rename the method:

```java
@Override
public boolean requiresTripLevelFiltering() {
  for (var selectRequest : select) {
    if (
      selectRequest.transportModeFilter() != null &&
      selectRequest.transportModeFilter().requiresTripLevelFiltering()
    ) {
      return true;
    }
  }

  for (var selectRequest : not) {
    if (
      selectRequest.transportModeFilter() != null &&
      selectRequest.transportModeFilter().requiresTripLevelFiltering()
    ) {
      return true;
    }
  }
  return false;
}
```

#### 10. DefaultTransitDataProviderFilter.java
**File**: `application/src/main/java/org/opentripplanner/routing/algorithm/raptoradapter/transit/request/DefaultTransitDataProviderFilter.java`

**Change**: Rename the field and update the constructor:

```java
// Line 41: Rename field
private final boolean requiresTripLevelModeFiltering;

// Line 52: Update constructor
requiresTripLevelModeFiltering = builder.filters().stream().anyMatch(TransitFilter::requiresTripLevelFiltering);

// Line 76: Update usage in createTripFilter
var applyTripTimesFilters = requiresTripLevelModeFiltering && tripPattern.getContainsMultipleModes();
```

### Success Criteria:

#### Automated Verification:
- [ ] All existing tests pass: `mvn test -pl application -Dtest=DefaultTransitDataProviderFilterTest`
- [ ] New tests from Phase 1 now pass: `mvn test -pl application -Dtest=DefaultTransitDataProviderFilterTest#filterByMainModeOnMultiModePattern`
- [ ] Full test suite passes: `mvn test`
- [ ] Code compiles without warnings: `mvn compile`
- [ ] Prettier check passes: `mvn prettier:check`

#### Manual Verification:
- [ ] Query with `transportMode: BUS` on a dataset with multi-mode patterns returns only BUS trips
- [ ] No performance regression in transit routing

---

## Testing Strategy

### Unit Tests
- New tests in `DefaultTransitDataProviderFilterTest`:
  - `filterByMainModeOnMultiModePattern()` - verifies BUS vs COACH filtering
  - `filterByDifferentMainModesOnMultiModePattern()` - verifies RAIL vs BUS filtering

### Existing Tests to Verify
All existing tests in these files should continue to pass:
- `DefaultTransitDataProviderFilterTest`
- `AllowMainModeFilterTest`
- `AllowMainAndSubModeFilterTest`
- `AllowMainAndSubModesFilterTest`
- `AllowMainModesFilterTest`
- `AllowAllModesFilterTest`

### Edge Cases
1. Pattern with single mode (containsMultipleModes=false) - should work as before
2. Filter with submode specified - should work as before
3. Filter with multiple main modes - should filter correctly
4. Empty filter (no mode specified) - should allow all

## Performance Considerations

The change affects the `requiresTripLevelModeFiltering` flag which controls when `tripTimesPredicate()` performs mode checking. This check is already designed to be performed only when necessary (when `tripPattern.getContainsMultipleModes()` is true).

The fix does not add any new per-trip overhead - it simply ensures that the existing trip-level mode filtering code path is correctly invoked for main mode filters on multi-mode patterns.

## References

- Research document: `thoughts/shared/research/2025-12-08-transport-mode-filter-bug.md`
- Related ignored test: `DefaultTransitDataProviderFilterTest.selectCombinationTest()` (line 383)
- NeTEx import: `TripPatternMapper.java:218-250`
- API mapping: `SelectRequestMapper.java:40-59`
