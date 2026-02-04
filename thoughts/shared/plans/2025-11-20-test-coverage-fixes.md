# Test Coverage Fixes for BikePreferencesMapper and ScooterPreferencesMapper

## Summary

After reviewing the actual implementation and existing tests, we need to fix:
1. **Incorrect test names** that say "LowerPrecedence" when they should say the wrapper wins
2. **Incorrect test expectations** that contradict the actual object-level priority behavior
3. **Missing test coverage** for key scenarios
4. **Incorrect comments** that contradict actual behavior

## Implementation Behavior (Confirmed)

Both mappers use **object-level priority** (if/else approach):

### BikePreferencesMapper
- **IF** `bikePreferences` wrapper exists → use ALL nested fields, ignore ALL deprecated fields
- **ELSE** → use ALL deprecated fields (`bikeSpeed`, `walkReluctance`, `bicycleOptimisationMethod`, `triangleFactors`)

### ScooterPreferencesMapper
- **IF** `scooterPreferences` wrapper exists → use ALL nested fields, ignore ALL deprecated bike fields
- **ELSE** → fall back to ALL deprecated bike fields (`bikeSpeed`, `walkReluctance`, `bicycleOptimisationMethod`, `triangleFactors`)

## BikePreferencesMapperTest Issues

### 1. Incorrect Test Names

| Line | Current Name | Issue | Correct Name |
|------|--------------|-------|--------------|
| 16 | `testBikeSpeed_DeprecatedFieldTakesLowerPrecedence` | Says "LowerPrecedence" but expects NEW field to win (7.0) | `testBikeSpeed_WrapperTakesPrecedenceOverDeprecated` |
| 73 | `testBikeOptimization_DeprecatedFieldTakesLowerPrecedence` | Says "LowerPrecedence" but expects NEW field to win (TRIANGLE) | `testBikeOptimization_WrapperTakesPrecedenceOverDeprecated` |
| 107 | `testTriangleFactors_DeprecatedTopLevelTakesLowerPrecedence` | Says "LowerPrecedence" but expects NEW field to win (0.3, 0.4, 0.3) | `testTriangleFactors_WrapperTakesPrecedenceOverDeprecated` |

### 2. Incorrect Test Expectations

| Line | Test Name | Issue | Fix |
|------|-----------|-------|-----|
| 16-28 | `testBikeSpeed_DeprecatedFieldTakesLowerPrecedence` | Expects 7.0 (NEW wins), but comment says deprecated should be ignored | ✅ Expectation is CORRECT - wrapper wins |
| 73-85 | `testBikeOptimization_DeprecatedFieldTakesLowerPrecedence` | Expects TRIANGLE (NEW wins), comment says deprecated should be ignored | ✅ Expectation is CORRECT - wrapper wins |
| 107-123 | `testTriangleFactors_DeprecatedTopLevelTakesLowerPrecedence` | Expects NEW values (0.3, 0.4, 0.3), comment says deprecated should be ignored | ✅ Expectation is CORRECT - wrapper wins |

### 3. Incorrect Comments in Tests

| Line | Test Name | Incorrect Comment | Should Be |
|------|-----------|-------------------|-----------|
| 170-171 | `testBike_MixedDeprecatedAndNew` | "Deprecated, should win for speed" | "Deprecated - ignored because wrapper exists" |
| 185 | `testBike_MixedDeprecatedAndNew` | "assertEquals(7.0, result.speed()); // New field wins" | ✅ CORRECT |
| 200-213 | `testBike_AllNewIgnoreDeprecatedFields` | Test name says "AllNewIgnoreDeprecatedFields" | Should be "WrapperIgnoresAllDeprecatedFields" |
| 218-222 | `testBike_AllNewIgnoreDeprecatedFields` | Expectations are CORRECT but confusing values (0.33 not 0.3?) | Need to investigate rounding |

### 4. Missing Test Coverage for BikePreferencesMapper

#### Missing: Deprecated fields work when wrapper NOT provided
- ✅ `testBikeSpeed_NewNestedFieldWorks` (line 31) - has wrapper
- ❌ **MISSING**: `testBikeSpeed_DeprecatedFieldWorks()` - NO wrapper, uses deprecated `bikeSpeed`

- ❌ **MISSING**: `testBikeOptimization_DeprecatedFieldWorks()` - NO wrapper, uses deprecated `bicycleOptimisationMethod`

- ✅ `testBikeReluctance_WalkReluctanceAppliedCorrectly` (line 47) - NO wrapper, uses deprecated `walkReluctance` ✅

- ❌ **MISSING**: `testTriangleFactors_DeprecatedTopLevelWorks()` - NO wrapper, uses deprecated `triangleFactors`

#### Missing: Empty wrapper object behavior
- ❌ **MISSING**: `testBike_EmptyWrapperUsesDefaults()` - wrapper exists but empty `bikePreferences: {}`

## ScooterPreferencesMapperTest Issues

### 1. Incorrect Test Names

| Line | Current Name | Issue | Correct Name |
|------|--------------|-------|--------------|
| 66 | `testTriangleFactors_DeprecatedTopLevelTakesLowerPrecedence` | Says "LowerPrecedence" but expects NEW field to win (0.3, 0.4, 0.3) | `testTriangleFactors_WrapperTakesPrecedenceOverDeprecated` |

### 2. Missing Test Coverage for ScooterPreferencesMapper

#### Missing: Fallback to deprecated bike fields when wrapper NOT provided
The implementation (lines 26-39) falls back to deprecated **bike** fields when `scooterPreferences` is not provided:

- ❌ **MISSING**: `testScooterSpeed_FallsBackToBikeSpeed()` - NO wrapper, uses deprecated `bikeSpeed`
- ❌ **MISSING**: `testScooterReluctance_FallsBackToWalkReluctance()` - NO wrapper, uses deprecated `walkReluctance`
- ❌ **MISSING**: `testScooterOptimization_FallsBackToBicycleOptimisationMethod()` - NO wrapper, uses deprecated `bicycleOptimisationMethod`
- ❌ **MISSING**: `testTriangleFactors_FallsBackToTopLevel()` - NO wrapper, uses deprecated `triangleFactors`

#### Missing: Wrapper precedence over deprecated bike fields
- ❌ **MISSING**: `testScooterSpeed_WrapperTakesPrecedenceOverBikeSpeed()` - wrapper exists + `bikeSpeed` exists → wrapper wins
- ❌ **MISSING**: `testScooterReluctance_WrapperTakesPrecedenceOverWalkReluctance()` - wrapper exists + `walkReluctance` exists → wrapper wins
- ❌ **MISSING**: `testScooterOptimization_WrapperTakesPrecedenceOverBicycleOptimisationMethod()` - wrapper exists + `bicycleOptimisationMethod` exists → wrapper wins

#### Missing: Empty wrapper object behavior
- ❌ **MISSING**: `testScooter_EmptyWrapperUsesDefaults()` - wrapper exists but empty `scooterPreferences: {}`

### 3. Overlapping Tests (Acceptable)

The following overlaps are acceptable for comprehensive coverage:
- `testScooterSpeed_NewNestedFieldWorks` + partial overlap with `testScooter_AllNewFieldsTogether` ✅
- `testTriangleFactors_NewNestedFieldWorks` + partial overlap with `testScooter_AllNewFieldsTogether` ✅

## Detailed Test Fixes

### BikePreferencesMapperTest - Tests to Fix

#### 1. Rename and fix comments (lines 16-28)
```java
@Test
void testBikeSpeed_WrapperTakesPrecedenceOverDeprecated() {  // RENAMED
  var preferences = BikePreferences.of();
  var callWith = TestDataFetcherDecorator.of(
    Map.of(
      "bikeSpeed", 10.0,  // Deprecated - ignored because wrapper exists
      "bikePreferences", Map.of("speed", 7.0)  // Wrapper wins
    )
  );
  mapBikePreferences(preferences, callWith);
  assertEquals(7.0, preferences.build().speed());  // Wrapper value
}
```

#### 2. Rename and fix comments (lines 73-85)
```java
@Test
void testBikeOptimization_WrapperTakesPrecedenceOverDeprecated() {  // RENAMED
  var preferences = BikePreferences.of();
  var callWith = TestDataFetcherDecorator.of(
    Map.of(
      "bicycleOptimisationMethod", VehicleRoutingOptimizeType.FLAT_STREETS,  // Ignored
      "bikePreferences", Map.of("optimizationMethod", VehicleRoutingOptimizeType.TRIANGLE)  // Wrapper wins
    )
  );
  mapBikePreferences(preferences, callWith);
  assertEquals(VehicleRoutingOptimizeType.TRIANGLE, preferences.build().optimizeType());
}
```

#### 3. Rename and fix comments (lines 107-123)
```java
@Test
void testTriangleFactors_WrapperTakesPrecedenceOverDeprecated() {  // RENAMED
  var preferences = BikePreferences.of();
  preferences.withOptimizeType(VehicleRoutingOptimizeType.TRIANGLE);
  var callWith = TestDataFetcherDecorator.of(
    Map.of(
      "triangleFactors", Map.of("time", 0.5, "slope", 0.3, "safety", 0.2),  // Ignored
      "bikePreferences", Map.of(
        "triangleFactors", Map.of("time", 0.3, "slope", 0.4, "safety", 0.3)  // Wrapper wins
      )
    )
  );
  mapBikePreferences(preferences, callWith);
  var result = preferences.build();
  assertEquals(0.3, result.optimizeTriangle().time());
  assertEquals(0.4, result.optimizeTriangle().slope());
  assertEquals(0.3, result.optimizeTriangle().safety());
}
```

#### 4. Fix comments (lines 166-188)
```java
@Test
void testBike_MixedDeprecatedAndNew() {
  var preferences = BikePreferences.of();
  var callWith = TestDataFetcherDecorator.of(
    Map.of(
      "bikeSpeed", 10.0,  // Deprecated - ignored because wrapper exists
      "bikePreferences", Map.of(
        "speed", 7.0,  // Wrapper wins
        "reluctance", 3.5,  // Wrapper wins
        "optimizationMethod", VehicleRoutingOptimizeType.TRIANGLE  // Wrapper wins
      )
    )
  );
  mapBikePreferences(preferences, callWith);
  var result = preferences.build();
  assertEquals(7.0, result.speed());  // Wrapper wins
  assertEquals(3.5, result.reluctance());  // Wrapper wins
  assertEquals(VehicleRoutingOptimizeType.TRIANGLE, result.optimizeType());  // Wrapper wins
}
```

#### 5. Rename and fix (lines 191-223)
```java
@Test
void testBike_WrapperIgnoresAllDeprecatedFields() {  // RENAMED
  var preferences = BikePreferences.of();
  preferences.withOptimizeType(VehicleRoutingOptimizeType.TRIANGLE);
  var callWith = TestDataFetcherDecorator.of(
    Map.of(
      // All deprecated fields - should be ignored
      "bikeSpeed", 11.0,
      "bicycleOptimisationMethod", VehicleRoutingOptimizeType.SAFEST_STREETS,
      "triangleFactors", Map.of("time", 0.6, "slope", 0.2, "safety", 0.1),
      // Wrapper takes precedence
      "bikePreferences", Map.of(
        "speed", 7.0,
        "reluctance", 3.0,
        "optimizationMethod", VehicleRoutingOptimizeType.TRIANGLE,
        "triangleFactors", Map.of("time", 0.3, "slope", 0.4, "safety", 0.2)
      )
    )
  );
  mapBikePreferences(preferences, callWith);
  var result = preferences.build();
  assertEquals(7.0, result.speed());  // Wrapper value
  assertEquals(VehicleRoutingOptimizeType.TRIANGLE, result.optimizeType());  // Wrapper value
  // Note: Values may be slightly different due to rounding in Units class
  assertEquals(0.3, result.optimizeTriangle().time(), 0.05);
  assertEquals(0.4, result.optimizeTriangle().slope(), 0.05);
  assertEquals(0.2, result.optimizeTriangle().safety(), 0.05);
}
```

### BikePreferencesMapperTest - Tests to Add

#### 6. Add missing deprecated field tests
```java
@Test
void testBikeSpeed_DeprecatedFieldWorks() {
  var preferences = BikePreferences.of();
  var callWith = TestDataFetcherDecorator.of(Map.of("bikeSpeed", 9.0));
  mapBikePreferences(preferences, callWith);
  assertEquals(9.0, preferences.build().speed());
}

@Test
void testBikeOptimization_DeprecatedFieldWorks() {
  var preferences = BikePreferences.of();
  var callWith = TestDataFetcherDecorator.of(
    Map.of("bicycleOptimisationMethod", VehicleRoutingOptimizeType.FLAT_STREETS)
  );
  mapBikePreferences(preferences, callWith);
  assertEquals(VehicleRoutingOptimizeType.FLAT_STREETS, preferences.build().optimizeType());
}

@Test
void testTriangleFactors_DeprecatedTopLevelWorks() {
  var preferences = BikePreferences.of();
  preferences.withOptimizeType(VehicleRoutingOptimizeType.TRIANGLE);
  var callWith = TestDataFetcherDecorator.of(
    Map.of("triangleFactors", Map.of("time", 0.6, "slope", 0.2, "safety", 0.2))
  );
  mapBikePreferences(preferences, callWith);
  var result = preferences.build();
  assertEquals(0.6, result.optimizeTriangle().time());
  assertEquals(0.2, result.optimizeTriangle().slope());
  assertEquals(0.2, result.optimizeTriangle().safety());
}

@Test
void testBike_EmptyWrapperUsesDefaults() {
  var preferences = BikePreferences.of();
  var callWith = TestDataFetcherDecorator.of(Map.of("bikePreferences", Map.of()));
  mapBikePreferences(preferences, callWith);
  var result = preferences.build();
  assertEquals(BikePreferences.DEFAULT.speed(), result.speed());
  assertEquals(BikePreferences.DEFAULT.reluctance(), result.reluctance());
  assertEquals(BikePreferences.DEFAULT.optimizeType(), result.optimizeType());
}
```

### ScooterPreferencesMapperTest - Tests to Fix

#### 1. Rename (line 66)
```java
@Test
void testTriangleFactors_WrapperTakesPrecedenceOverDeprecated() {  // RENAMED
  var preferences = ScooterPreferences.of();
  preferences.withOptimizeType(VehicleRoutingOptimizeType.TRIANGLE);
  var callWith = TestDataFetcherDecorator.of(
    Map.of(
      "triangleFactors", Map.of("time", 0.5, "slope", 0.3, "safety", 0.2),  // Ignored
      "scooterPreferences", Map.of(
        "triangleFactors", Map.of("time", 0.3, "slope", 0.4, "safety", 0.3)  // Wrapper wins
      )
    )
  );
  mapScooterPreferences(preferences, callWith);
  var result = preferences.build();
  assertEquals(0.3, result.optimizeTriangle().time());
  assertEquals(0.4, result.optimizeTriangle().slope());
  assertEquals(0.3, result.optimizeTriangle().safety());
}
```

### ScooterPreferencesMapperTest - Tests to Add

#### 2. Add fallback to bike deprecated fields tests
```java
@Test
void testScooterSpeed_FallsBackToBikeSpeed() {
  var preferences = ScooterPreferences.of();
  var callWith = TestDataFetcherDecorator.of(Map.of("bikeSpeed", 8.0));
  mapScooterPreferences(preferences, callWith);
  assertEquals(8.0, preferences.build().speed());
}

@Test
void testScooterReluctance_FallsBackToWalkReluctance() {
  var preferences = ScooterPreferences.of();
  var callWith = TestDataFetcherDecorator.of(Map.of("walkReluctance", 3.5));
  mapScooterPreferences(preferences, callWith);
  assertEquals(3.5, preferences.build().reluctance());
}

@Test
void testScooterOptimization_FallsBackToBicycleOptimisationMethod() {
  var preferences = ScooterPreferences.of();
  var callWith = TestDataFetcherDecorator.of(
    Map.of("bicycleOptimisationMethod", VehicleRoutingOptimizeType.FLAT_STREETS)
  );
  mapScooterPreferences(preferences, callWith);
  assertEquals(VehicleRoutingOptimizeType.FLAT_STREETS, preferences.build().optimizeType());
}

@Test
void testTriangleFactors_FallsBackToTopLevel() {
  var preferences = ScooterPreferences.of();
  preferences.withOptimizeType(VehicleRoutingOptimizeType.TRIANGLE);
  var callWith = TestDataFetcherDecorator.of(
    Map.of("triangleFactors", Map.of("time", 0.5, "slope", 0.3, "safety", 0.2))
  );
  mapScooterPreferences(preferences, callWith);
  var result = preferences.build();
  assertEquals(0.5, result.optimizeTriangle().time());
  assertEquals(0.3, result.optimizeTriangle().slope());
  assertEquals(0.2, result.optimizeTriangle().safety());
}
```

#### 3. Add wrapper precedence over bike fields tests
```java
@Test
void testScooterSpeed_WrapperTakesPrecedenceOverBikeSpeed() {
  var preferences = ScooterPreferences.of();
  var callWith = TestDataFetcherDecorator.of(
    Map.of(
      "bikeSpeed", 10.0,  // Ignored
      "scooterPreferences", Map.of("speed", 6.0)  // Wrapper wins
    )
  );
  mapScooterPreferences(preferences, callWith);
  assertEquals(6.0, preferences.build().speed());
}

@Test
void testScooterReluctance_WrapperTakesPrecedenceOverWalkReluctance() {
  var preferences = ScooterPreferences.of();
  var callWith = TestDataFetcherDecorator.of(
    Map.of(
      "walkReluctance", 4.0,  // Ignored
      "scooterPreferences", Map.of("reluctance", 2.5)  // Wrapper wins
    )
  );
  mapScooterPreferences(preferences, callWith);
  assertEquals(2.5, preferences.build().reluctance());
}

@Test
void testScooterOptimization_WrapperTakesPrecedenceOverBicycleOptimisationMethod() {
  var preferences = ScooterPreferences.of();
  var callWith = TestDataFetcherDecorator.of(
    Map.of(
      "bicycleOptimisationMethod", VehicleRoutingOptimizeType.FLAT_STREETS,  // Ignored
      "scooterPreferences", Map.of("optimizationMethod", VehicleRoutingOptimizeType.TRIANGLE)  // Wrapper wins
    )
  );
  mapScooterPreferences(preferences, callWith);
  assertEquals(VehicleRoutingOptimizeType.TRIANGLE, preferences.build().optimizeType());
}

@Test
void testScooter_EmptyWrapperUsesDefaults() {
  var preferences = ScooterPreferences.of();
  var callWith = TestDataFetcherDecorator.of(Map.of("scooterPreferences", Map.of()));
  mapScooterPreferences(preferences, callWith);
  var result = preferences.build();
  assertEquals(ScooterPreferences.DEFAULT.speed(), result.speed());
  assertEquals(ScooterPreferences.DEFAULT.reluctance(), result.reluctance());
  assertEquals(ScooterPreferences.DEFAULT.optimizeType(), result.optimizeType());
}
```

## Summary of Changes

### BikePreferencesMapperTest
- **Rename 3 tests**: Add "Wrapper" instead of "LowerPrecedence"
- **Fix comments in 2 tests**: Correct misleading comments about priority
- **Add 4 new tests**: Deprecated field behavior + empty wrapper

### ScooterPreferencesMapperTest
- **Rename 1 test**: Add "Wrapper" instead of "LowerPrecedence"
- **Add 8 new tests**: Fallback to bike fields + wrapper precedence + empty wrapper

## Implementation Plan

1. ✅ Update plan document to reflect actual implementation
2. ⏳ Fix BikePreferencesMapperTest (rename + comments + add tests)
3. ⏳ Fix ScooterPreferencesMapperTest (rename + add tests)
4. ⏳ Run tests: `mvn test -Dtest=BikePreferencesMapperTest,ScooterPreferencesMapperTest`
5. ⏳ Verify all tests pass and coverage is comprehensive
