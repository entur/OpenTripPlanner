# Test Update Handoff Document

## Overview

This document describes the changes needed to update tests for the revised bike and scooter preferences implementation in the TransModel GraphQL API.

## Summary of Implementation Changes

### New Priority Logic

The implementation now uses a **"defaults with override"** approach:

1. **New wrapper fields** (`bikePreferences`, `scooterPreferences`) have default values from server config
2. **Deprecated fields** (`bikeSpeed`, `bicycleOptimisationMethod`, `triangleFactors`, `walkReluctance`) take precedence when explicitly provided
3. Deprecated fields no longer have `defaultValue()` set in GraphQL schema - they're only applied when user explicitly provides them

### Key Files Changed

- `BikePreferencesInputType.java` - NEW: Factory method creates input type with defaults from `BikePreferences`
- `ScooterPreferencesInputType.java` - NEW: Factory method creates input type with defaults from `ScooterPreferences`
- `TripQuery.java` - Updated to use new input types and removed `defaultValue()` from deprecated fields
- `BikePreferencesMapper.java` - Reversed priority: applies wrapper defaults first, then overrides with deprecated fields
- `ScooterPreferencesMapper.java` - Same pattern: applies wrapper defaults first, then overrides with deprecated bike fields
- `VehiclePreferencesInputType.java` - DELETED (replaced by separate bike/scooter types)

### Mapper Logic

**BikePreferencesMapper** (lines 13-48):
```java
// First, apply values from bikePreferences (which have defaults from server config)
callWith.argument("bikePreferences.speed", bike::withSpeed);
callWith.argument("bikePreferences.reluctance", ...);
callWith.argument("bikePreferences.optimizationMethod", bike::withOptimizeType);

// Then, override with deprecated fields if explicitly provided (they take precedence)
callWith.argument("bikeSpeed", bike::withSpeed);
callWith.argument("bicycleOptimisationMethod", bike::withOptimizeType);
callWith.argument("walkReluctance", ...);

// Triangle factors: wrapper first, then deprecated override
if (bike.optimizeType() == TRIANGLE) {
  // First apply from bikePreferences
  callWith.argument("bikePreferences.triangleFactors.*", ...);
  // Then override with deprecated top-level if provided
  callWith.argument("triangleFactors.*", ...);
}
```

**ScooterPreferencesMapper** (lines 11-42):
```java
// Apply values from scooterPreferences (which have defaults from server config)
callWith.argument("scooterPreferences.speed", scooter::withSpeed);
callWith.argument("scooterPreferences.reluctance", scooter::withReluctance);
callWith.argument("scooterPreferences.optimizationMethod", scooter::withOptimizeType);

// Then, override with deprecated fields if explicitly provided (they take precedence)
callWith.argument("bikeSpeed", scooter::withSpeed);
callWith.argument("bicycleOptimisationMethod", scooter::withOptimizeType);
callWith.argument("walkReluctance", scooter::withReluctance);

// Triangle factors: wrapper first, then deprecated override
if (scooter.optimizeType() == TRIANGLE) {
  // First apply from scooterPreferences
  callWith.argument("scooterPreferences.triangleFactors.*", ...);
  // Then override with deprecated top-level if provided
  callWith.argument("triangleFactors.*", ...);
}
```

## Test Files to Update

### 1. BikePreferencesMapperTest.java

**Location**: `application/src/test/java/org/opentripplanner/apis/transmodel/mapping/preferences/BikePreferencesMapperTest.java`

**Current tests to review/update**:

| Test | Current Behavior | New Expected Behavior |
|------|------------------|----------------------|
| `testBikeSpeed_WrapperTakesPrecedenceOverDeprecated` | Wrapper wins over deprecated | **REVERSE**: Deprecated wins over wrapper |
| `testBikeSpeed_NewNestedFieldWorks` | Tests wrapper alone | Still valid, but now wrapper provides defaults |
| `testBikeSpeed_DefaultUsedWhenNeitherProvided` | Uses `BikePreferences.DEFAULT` | Now uses wrapper defaults (same value if config not set) |
| `testBikeReluctance_WalkReluctanceAppliedCorrectly` | walkReluctance applied | Still valid - deprecated field overrides wrapper |
| `testBikeReluctance_NewNestedFieldSecondPriority` | Tests wrapper reluctance | Still valid as base case |
| `testBikeOptimization_WrapperTakesPrecedenceOverDeprecated` | Wrapper wins | **REVERSE**: Deprecated wins |
| Triangle factor tests | Various priorities | Update to reflect deprecated > wrapper |

**New test scenarios needed**:

```java
// Scenario 1: Only wrapper provided - uses wrapper values
@Test
void testBikeSpeed_WrapperValuesUsedWhenNoDeprecatedProvided() {
  var preferences = BikePreferences.of();
  var callWith = TestDataFetcherDecorator.of(
    "bikePreferences", Map.of("speed", 7.0)
  );
  mapBikePreferences(preferences, callWith);
  assertEquals(7.0, preferences.build().speed());
}

// Scenario 2: Both provided - deprecated wins
@Test
void testBikeSpeed_DeprecatedTakesPrecedenceOverWrapper() {
  var preferences = BikePreferences.of();
  var callWith = TestDataFetcherDecorator.of(
    Map.of(
      "bikeSpeed", 10.0,  // Deprecated - should win
      "bikePreferences", Map.of("speed", 7.0)  // Wrapper - should be overridden
    )
  );
  mapBikePreferences(preferences, callWith);
  assertEquals(10.0, preferences.build().speed());  // Deprecated wins
}

// Scenario 3: Only deprecated provided - deprecated used
@Test
void testBikeSpeed_DeprecatedOnlyWorks() {
  var preferences = BikePreferences.of();
  var callWith = TestDataFetcherDecorator.of(Map.of("bikeSpeed", 12.0));
  mapBikePreferences(preferences, callWith);
  assertEquals(12.0, preferences.build().speed());
}

// Scenario 4: Neither provided - builder retains original (config) value
@Test
void testBikeSpeed_ConfigValuePreservedWhenNothingProvided() {
  // Start with a builder that has config values
  var preferences = BikePreferences.of();
  preferences.withSpeed(8.5);  // Simulating config value
  var callWith = TestDataFetcherDecorator.of(Map.of());
  mapBikePreferences(preferences, callWith);
  assertEquals(8.5, preferences.build().speed());  // Config preserved
}

// Similar tests for reluctance, optimization method, and triangle factors
```

**Key principle**: The test decorator needs to NOT include arguments that aren't explicitly provided. The current `TestDataFetcherDecorator` should already do this correctly.

### 2. ScooterPreferencesMapperTest.java

**Location**: `application/src/test/java/org/opentripplanner/apis/transmodel/mapping/preferences/ScooterPreferencesMapperTest.java`

**Test scenarios needed**:

```java
// Scenario 1: Only scooterPreferences provided - uses scooter values
@Test
void testScooterSpeed_WrapperValuesUsed() {
  var preferences = ScooterPreferences.of();
  var callWith = TestDataFetcherDecorator.of(
    "scooterPreferences", Map.of("speed", 6.0)
  );
  mapScooterPreferences(preferences, callWith);
  assertEquals(6.0, preferences.build().speed());
}

// Scenario 2: Both scooterPreferences and deprecated bike fields - deprecated wins
@Test
void testScooterSpeed_DeprecatedBikeFieldTakesPrecedence() {
  var preferences = ScooterPreferences.of();
  var callWith = TestDataFetcherDecorator.of(
    Map.of(
      "bikeSpeed", 10.0,  // Deprecated bike field - should override scooter
      "scooterPreferences", Map.of("speed", 6.0)
    )
  );
  mapScooterPreferences(preferences, callWith);
  assertEquals(10.0, preferences.build().speed());  // Deprecated bike field wins
}

// Scenario 3: Only deprecated bike field - applied to scooter
@Test
void testScooterSpeed_DeprecatedBikeFieldApplied() {
  var preferences = ScooterPreferences.of();
  var callWith = TestDataFetcherDecorator.of(Map.of("bikeSpeed", 9.0));
  mapScooterPreferences(preferences, callWith);
  assertEquals(9.0, preferences.build().speed());
}

// Scenario 4: walkReluctance overrides scooterPreferences.reluctance
@Test
void testScooterReluctance_WalkReluctanceTakesPrecedence() {
  var preferences = ScooterPreferences.of();
  var callWith = TestDataFetcherDecorator.of(
    Map.of(
      "walkReluctance", 4.0,  // Deprecated - should win
      "scooterPreferences", Map.of("reluctance", 2.5)
    )
  );
  mapScooterPreferences(preferences, callWith);
  assertEquals(4.0, preferences.build().reluctance());
}

// Scenario 5: triangleFactors overrides scooterPreferences.triangleFactors
@Test
void testScooterTriangle_DeprecatedTakesPrecedence() {
  var preferences = ScooterPreferences.of();
  preferences.withOptimizeType(VehicleRoutingOptimizeType.TRIANGLE);
  var callWith = TestDataFetcherDecorator.of(
    Map.of(
      "triangleFactors", Map.of("time", 0.6, "slope", 0.2, "safety", 0.2),
      "scooterPreferences", Map.of(
        "triangleFactors", Map.of("time", 0.3, "slope", 0.4, "safety", 0.3)
      )
    )
  );
  mapScooterPreferences(preferences, callWith);
  var result = preferences.build();
  assertEquals(0.6, result.optimizeTriangle().time());  // Deprecated wins
}

// Scenario 6: Config values preserved when nothing provided
@Test
void testScooterSpeed_ConfigValuePreserved() {
  var preferences = ScooterPreferences.of();
  preferences.withSpeed(7.5);  // Simulating config value
  var callWith = TestDataFetcherDecorator.of(Map.of());
  mapScooterPreferences(preferences, callWith);
  assertEquals(7.5, preferences.build().speed());  // Config preserved
}
```

### 3. Remove/Update Tests Referencing Removed Fields

The following flat scooter fields were removed from TripQuery and should NOT be tested:
- `scooterSpeed`
- `scooterReluctance`
- `scooterOptimisationMethod`
- `bikeReluctance`

Any existing tests referencing these fields should be removed.

### 4. TestDataFetcherDecorator Verification

**Location**: `application/src/test/java/org/opentripplanner/apis/transmodel/_support/TestDataFetcherDecorator.java`

Verify that `TestDataFetcherDecorator`:
1. Only reports `hasArgument()` = true for arguments explicitly added
2. Does NOT simulate GraphQL default value behavior (that's the point - we're testing the mapper logic)

## Test Matrix

### BikePreferencesMapper Test Matrix

| Scenario | bikePreferences | bikeSpeed | walkReluctance | bicycleOptimisationMethod | triangleFactors | Expected Winner |
|----------|-----------------|-----------|----------------|---------------------------|-----------------|-----------------|
| Wrapper only | speed: 7.0 | - | - | - | - | 7.0 (wrapper) |
| Deprecated only | - | 10.0 | - | - | - | 10.0 (deprecated) |
| Both provided | speed: 7.0 | 10.0 | - | - | - | 10.0 (deprecated wins) |
| Neither | - | - | - | - | - | config/builder value |
| Reluctance wrapper | reluctance: 2.5 | - | - | - | - | 2.5 (wrapper) |
| Reluctance deprecated | reluctance: 2.5 | - | 4.0 | - | - | 4.0 (deprecated wins) |
| Triangle wrapper | triangleFactors: {...} | - | - | triangle | - | wrapper values |
| Triangle both | triangleFactors: {...} | - | - | triangle | {...} | deprecated wins |

### ScooterPreferencesMapper Test Matrix

| Scenario | scooterPreferences | bikeSpeed | walkReluctance | bicycleOptimisationMethod | triangleFactors | Expected Winner |
|----------|-------------------|-----------|----------------|---------------------------|-----------------|-----------------|
| Wrapper only | speed: 6.0 | - | - | - | - | 6.0 (wrapper) |
| Deprecated bike only | - | 10.0 | - | - | - | 10.0 (deprecated) |
| Both provided | speed: 6.0 | 10.0 | - | - | - | 10.0 (deprecated wins) |
| Neither | - | - | - | - | - | config/builder value |
| Reluctance wrapper | reluctance: 2.5 | - | - | - | - | 2.5 (wrapper) |
| Reluctance deprecated | reluctance: 2.5 | - | 4.0 | - | - | 4.0 (deprecated wins) |

## Integration Test Considerations

When testing via GraphiQL or integration tests:

1. **Default values are now on the input type fields**, not the top-level arguments
2. **Deprecated fields without explicit values** will NOT be present in the environment
3. **The bikePreferences/scooterPreferences types** will always have their defaults applied (from server config)

### Example GraphQL Queries for Testing

```graphql
# Test 1: Uses scooter config defaults (no explicit values)
{
  trip(from: {...}, to: {...}, modes: [{mode: SCOOTER}]) {
    tripPatterns { ... }
  }
}
# Expected: Uses routingDefaults.scooter.speed from router-config.json

# Test 2: Explicit scooterPreferences
{
  trip(
    from: {...}, to: {...},
    modes: [{mode: SCOOTER}]
    scooterPreferences: { speed: 6.0 }
  ) {
    tripPatterns { ... }
  }
}
# Expected: Uses 6.0 for speed, config defaults for other fields

# Test 3: Deprecated field overrides
{
  trip(
    from: {...}, to: {...},
    modes: [{mode: BICYCLE}]
    bikeSpeed: 12.0
    bikePreferences: { speed: 8.0 }
  ) {
    tripPatterns { ... }
  }
}
# Expected: Uses 12.0 (deprecated wins)

# Test 4: Scooter with deprecated bike fallback
{
  trip(
    from: {...}, to: {...},
    modes: [{mode: SCOOTER}]
    bikeSpeed: 10.0
  ) {
    tripPatterns { ... }
  }
}
# Expected: Scooter uses 10.0 from deprecated bikeSpeed
```

## Files Summary

| File | Action | Notes |
|------|--------|-------|
| `BikePreferencesMapperTest.java` | UPDATE | Reverse priority expectations, add new scenarios |
| `ScooterPreferencesMapperTest.java` | UPDATE | Add deprecated bike field override tests |
| `TestDataFetcherDecorator.java` | VERIFY | Ensure it doesn't simulate GraphQL defaults |
| Any tests referencing removed fields | REMOVE | `scooterSpeed`, `scooterReluctance`, `scooterOptimisationMethod`, `bikeReluctance` |

## Key Behavioral Changes to Test

1. **Wrapper fields now have defaults** - GraphQL will always include them with config values
2. **Deprecated fields override wrapper fields** - When explicitly provided by user
3. **Deprecated fields without defaultValue()** - Only present when user provides them
4. **Scooter uses its own config** - No longer falls back to bike config when wrapper is used
5. **Scooter still accepts deprecated bike fields** - For backward compatibility with existing clients
