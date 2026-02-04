# Research: Option B - Propulsion-Aware Cost Calculation for Rental Vehicles

**Date**: 2025-11-27T10:33:37+0000
**Researcher**: testower
**Git Commit**: bf2b9ac8524b4384785836f43669e6ee3aad0ee9
**Branch**: dev-2.x
**Repository**: OpenTripPlanner

## Research Question

What would Option B (Propulsion-Aware Cost Calculation) entail in detail? This approach keeps existing traverse modes but modifies cost calculations based on propulsion type. Specifically:
1. How would e-scooters (constant speed) be handled?
2. How would e-bikes (variable speed with reduced slope impact) be handled?

## Summary

Option B modifies cost calculations at runtime based on propulsion type, without adding new traverse modes. The key changes are:
1. Thread `PropulsionType` through the routing State (similar to how `RentalFormFactor` is already tracked)
2. Modify `bicycleOrScooterTraversalCost()` to adjust effective distances based on propulsion
3. E-scooters use flat distance (constant speed model)
4. E-bikes use interpolated distance (reduced slope sensitivity model)

This approach requires fewer changes than Option A (new traverse modes) and is more targeted than Option C (TraverseMode as record).

**Estimated scope**: 6-8 files modified, ~80 lines of core changes
**Risk level**: Medium - changes are targeted to rental vehicle routing, don't affect owned bike/car routing

## Current Architecture: Where Slope Affects Routing

### 1. Build-Time: Effective Distances Pre-Computed

**Location**: `ElevationUtils.getSlopeCosts()` at `ElevationUtils.java:95-162`

Two key factors are computed for each street edge:

| Factor | Formula | Purpose |
|--------|---------|---------|
| `slopeSpeedFactor` | `sum(run / slopeSpeedCoef) / flatLength` | Time/speed penalty |
| `slopeWorkFactor` | `sum(energy) / flatLength` where `energy = hypotenuse * (1 + 4000 * slope³)` | Energy/work penalty |

These are stored as:
- `effectiveBikeDistance = distanceMeters * slopeSpeedFactor`
- `effectiveBikeDistanceForWorkCost = distanceMeters * slopeWorkFactor`

### 2. Runtime: Cost Calculation Uses Effective Distances

**Location**: `StreetEdge.bicycleOrScooterTraversalCost()` at `StreetEdge.java:1104-1141`

```java
private TraversalCosts bicycleOrScooterTraversalCost(
  StreetSearchRequest req,
  TraverseMode mode,
  double speed
) {
  double time = getEffectiveBikeDistance() / speed;  // Line 1109 - ALWAYS uses slope-adjusted distance
  double weight;
  // ... switch on optimizeType ...
  // FLAT_STREETS uses getEffectiveBikeDistanceForWorkCost()
  // SHORTEST_DURATION uses getEffectiveBikeDistance()
  // TRIANGLE uses both
}
```

**Critical observation**: The `time` calculation at line 1109 ALWAYS uses `getEffectiveBikeDistance()`, which includes slope penalties. This affects travel time estimates for ALL bicycle/scooter routes.

## The PropulsionType Gap

### Where PropulsionType Exists

`RentalVehicleType.java:101-102`:
```java
public PropulsionType propulsionType() {
  return propulsionType;
}
```

Available values (`RentalVehicleType.java:211-220`):
```java
public enum PropulsionType {
  HUMAN,
  ELECTRIC_ASSIST,  // E-bikes (pedal-assist)
  ELECTRIC,         // Fully electric (e-scooters)
  COMBUSTION,
  COMBUSTION_DIESEL,
  HYBRID,
  PLUG_IN_HYBRID,
  HYDROGEN_FUEL_CELL,
}
```

### Where PropulsionType is Lost

`VehicleRentalUpdater.java:192-199` - When creating rental edges, only FormFactor is used:
```java
for (RentalFormFactor formFactor : formFactors) {
  tempEdges.addEdge(
    VehicleRentalEdge.createVehicleRentalEdge(vehicleRentalVertex, formFactor)
  );
}
// PropulsionType is NOT passed to the edge
```

`StateEditor.beginVehicleRentingAtStation()` at `StateEditor.java:282-303`:
```java
public void beginVehicleRentingAtStation(
  RentalFormFactor formFactor,
  String network,
  boolean mayKeep,
  boolean reverse
) {
  // ...
  child.stateData.rentalVehicleFormFactor = formFactor;
  // NO PropulsionType stored!
}
```

`StateData.java:49` - Only FormFactor is tracked:
```java
public RentalFormFactor rentalVehicleFormFactor;
// NO PropulsionType field exists
```

## Implementation: Threading PropulsionType

### Step 1: Add PropulsionType to StateData

**File**: `StateData.java`

Add new field at line 50:
```java
public RentalFormFactor rentalVehicleFormFactor;
public PropulsionType rentalVehiclePropulsionType;  // NEW
```

### Step 2: Update StateEditor Methods

**File**: `StateEditor.java`

Modify `beginVehicleRentingAtStation()` (line 282):
```java
public void beginVehicleRentingAtStation(
  RentalFormFactor formFactor,
  PropulsionType propulsionType,  // NEW PARAMETER
  String network,
  boolean mayKeep,
  boolean reverse
) {
  // ...
  if (!reverse) {
    child.stateData.rentalVehicleFormFactor = formFactor;
    child.stateData.rentalVehiclePropulsionType = propulsionType;  // NEW
  } else {
    child.stateData.rentalVehicleFormFactor = null;
    child.stateData.rentalVehiclePropulsionType = null;  // NEW
  }
}
```

Similarly update:
- `beginFloatingVehicleRenting()` (line 262)
- `dropOffRentedVehicleAtStation()` (line 305)
- `dropOffFloatingVehicle()` (line 337)

### Step 3: Pass PropulsionType from VehicleRentalEdge

**File**: `VehicleRentalEdge.java`

The edge currently stores only `RentalFormFactor`. For floating vehicles, PropulsionType is accessible:
```java
// In traverse() method:
VehicleRentalVehicle vehicle = (VehicleRentalVehicle) station;
PropulsionType propulsion = vehicle.vehicleType().propulsionType();
```

For station-based rentals, need to determine which vehicle type is being rented. Options:
1. Use the "most common" propulsion type at the station
2. Add PropulsionType to the edge (per vehicle type available)
3. Default to HUMAN if mixed types available

### Step 4: Add State Accessor

**File**: `State.java`

Add method to access propulsion type:
```java
public PropulsionType getRentalVehiclePropulsionType() {
  return stateData.rentalVehiclePropulsionType;
}
```

### Step 5: Modify Cost Calculation

**File**: `StreetEdge.java`

Modify `bicycleOrScooterTraversalCost()`:

```java
private TraversalCosts bicycleOrScooterTraversalCost(
  StreetSearchRequest req,
  TraverseMode mode,
  double speed,
  PropulsionType propulsion  // NEW PARAMETER (or get from State)
) {
  // Calculate effective distances based on propulsion type
  double effectiveTimeDistance = calculateEffectiveTimeDistance(propulsion);
  double effectiveWorkDistance = calculateEffectiveWorkDistance(propulsion);

  double time = effectiveTimeDistance / speed;  // Was: getEffectiveBikeDistance() / speed
  double weight;

  switch (optimizeType) {
    case FLAT_STREETS -> weight = effectiveWorkDistance / speed;
    case SHORTEST_DURATION -> weight = effectiveTimeDistance / speed;
    case TRIANGLE -> {
      double quick = effectiveTimeDistance;
      double slope = effectiveWorkDistance;
      // ...
    }
    // ...
  }
}

private double calculateEffectiveTimeDistance(PropulsionType propulsion) {
  if (propulsion == null) {
    return getEffectiveBikeDistance();  // Default: full slope effect
  }

  return switch (propulsion) {
    case ELECTRIC -> getDistanceMeters();  // Constant speed (e-scooters)
    case ELECTRIC_ASSIST -> interpolateDistance(0.3);  // Reduced slope effect (e-bikes)
    default -> getEffectiveBikeDistance();  // Full slope effect
  };
}

private double interpolateDistance(double slopeSensitivity) {
  // Interpolate between flat distance and slope-adjusted distance
  double flatDistance = getDistanceMeters();
  double slopedDistance = getEffectiveBikeDistance();
  return flatDistance + (slopedDistance - flatDistance) * slopeSensitivity;
}
```

## E-Scooter Model: Constant Speed

### Physics Rationale

Electric scooters (kick scooters with motors) typically:
- Have speed-limited motors (20-25 km/h in most jurisdictions)
- Maintain constant speed on moderate grades (up to ~10%)
- May slow slightly on steep uphills (>15%) due to motor/battery limits
- Are typically speed-limited on downhills (motor cuts out at max speed)

### Implementation Approach

For `PropulsionType.ELECTRIC` (fully electric, no human power):

```java
case ELECTRIC -> {
  // Constant speed model - ignore slope for time calculation
  effectiveTimeDistance = getDistanceMeters();

  // Work cost could be zero (motor-powered) or slight penalty for battery drain on uphills
  effectiveWorkDistance = getDistanceMeters();
  // OR: slight uphill penalty for battery drain
  // effectiveWorkDistance = getDistanceMeters() * (1 + getMaxSlope() * 0.1);
}
```

**Key decisions**:
1. **Time**: Use flat distance (constant speed)
2. **Work/Energy**: Use flat distance (motor does work) or slight uphill penalty for battery considerations
3. **Safety**: Keep existing safety distance (steep downhills are dangerous on scooters)

## E-Bike Model: Variable Speed with Reduced Slope Impact

### Physics Rationale

Electric-assist bicycles (pedelecs) typically:
- Provide motor assistance up to a speed limit (25 km/h in EU, 32 km/h in US)
- Motor assistance compensates for 60-80% of uphill effort
- Still require some human effort (pedaling triggers assist)
- Downhill: behave like regular bikes (motor cuts out above speed limit)

### Implementation Approach

For `PropulsionType.ELECTRIC_ASSIST`:

```java
case ELECTRIC_ASSIST -> {
  // Reduced slope sensitivity - motor compensates for ~70% of uphill penalty
  double slopeSensitivity = 0.3;  // 30% of human-powered effect

  double flatDist = getDistanceMeters();
  double slopedDist = getEffectiveBikeDistance();

  // Uphill: reduced penalty
  // Downhill: same as regular bike (can go faster than motor limit)
  if (slopedDist > flatDist) {
    // Uphill segment - reduce penalty
    effectiveTimeDistance = flatDist + (slopedDist - flatDist) * slopeSensitivity;
  } else {
    // Downhill segment - keep full benefit (can coast faster than motor limit)
    effectiveTimeDistance = slopedDist;
  }

  // Work cost: significantly reduced (motor does most of the work)
  effectiveWorkDistance = flatDist +
    (getEffectiveBikeDistanceForWorkCost() - flatDist) * slopeSensitivity;
}
```

**Slope sensitivity factor considerations**:
- `0.0` = fully electric (no slope impact) - too aggressive for e-bikes
- `0.3` = motor compensates 70% of effort - reasonable for modern e-bikes
- `0.5` = motor compensates 50% of effort - conservative estimate
- `1.0` = no motor assist - same as regular bike

This could be made configurable in `BikePreferences` or `ScooterPreferences`.

### Asymmetric Model (Advanced)

A more accurate model would be asymmetric:
- Uphill: 20-40% of human-powered penalty (motor helps significantly)
- Downhill: 80-100% of human-powered benefit (motor doesn't help, can coast fast)

```java
case ELECTRIC_ASSIST -> {
  double flatDist = getDistanceMeters();
  double slopedDist = getEffectiveBikeDistance();
  double delta = slopedDist - flatDist;

  if (delta > 0) {
    // Uphill: motor helps a lot
    effectiveTimeDistance = flatDist + delta * 0.25;  // 25% of uphill penalty
  } else {
    // Downhill: can coast fast like regular bike
    effectiveTimeDistance = flatDist + delta * 0.9;  // 90% of downhill benefit
  }
}
```

## Code Changes Summary

| File | Change | Lines Affected |
|------|--------|----------------|
| `StateData.java` | Add `rentalVehiclePropulsionType` field | +1 line |
| `State.java` | Add `getRentalVehiclePropulsionType()` accessor | +3 lines |
| `StateEditor.java` | Add PropulsionType to 4 rental methods | ~20 lines modified |
| `VehicleRentalEdge.java` | Pass PropulsionType to StateEditor | ~10 lines modified |
| `StreetEdge.java` | Add propulsion-aware distance calculation | ~40 lines added |
| Tests | Update tests for new parameter | Variable |

## Comparison: Option B vs Other Options

| Aspect | Option A (New Enum Values) | Option B (This Approach) | Option C (Record) |
|--------|---------------------------|--------------------------|-------------------|
| Files changed | 14+ | 6-8 | 30-50 |
| Lines of code | ~200 | ~80 | ~500+ |
| API changes | Add EBIKE, ESCOOTER enums | None | Major restructuring |
| Graph changes | None | None | Serialization changes |
| Risk level | Medium-High | Medium | High |
| Flexibility | Fixed propulsion types | Runtime calculation | Full flexibility |
| Performance | Same as current | Slight overhead | Slight overhead |

## Open Questions for Implementation

1. **Station-based rentals**: How to determine PropulsionType when a station has multiple vehicle types?
   - Option: Use the most common type
   - Option: Create separate edges per propulsion type
   - Option: Default to HUMAN (conservative)

2. **Configurable sensitivity**: Should the slope sensitivity factor be configurable per deployment?
   - Could add `ebikeSlopeSensitivity` to preferences
   - Default: 0.3 (70% motor compensation)

3. **Downhill behavior for e-bikes**: Should we apply asymmetric model?
   - Uphill: reduced penalty (motor helps)
   - Downhill: full benefit (coasting, motor off)

4. **Safety considerations**: Should steep downhills still be penalized for e-scooters?
   - High-speed e-scooter descents can be dangerous
   - Keep safety distance calculation for all modes?

5. **Battery range**: Should `maxRangeMeters` be enforced as a routing constraint?
   - Could add range check to state validation
   - Separate from slope handling

## Recommended Implementation Approach

Start with a simple implementation:

1. **Add PropulsionType to State** (Steps 1-4 above)
2. **Implement constant speed for e-scooters** (`ELECTRIC` → flat distance)
3. **Implement reduced slope for e-bikes** (`ELECTRIC_ASSIST` → 30% sensitivity)
4. **Keep safety calculations unchanged** (steep slopes penalized for safety)
5. **Add configuration option** for slope sensitivity factor
6. **Add unit tests** for propulsion-aware cost calculations

This provides immediate improvement for e-scooter and e-bike routing with minimal code changes and risk.

## Code References

### Core Files to Modify
- `StateData.java:49-50` - Add propulsion type field
- `State.java` - Add accessor method
- `StateEditor.java:262-337` - Update 4 rental methods
- `VehicleRentalEdge.java:56-146` - Pass propulsion to StateEditor
- `StreetEdge.java:1104-1141` - Propulsion-aware cost calculation

### Supporting Files (Read-Only Context)
- `ElevationUtils.java:95-162` - Slope cost calculation (build-time)
- `RentalVehicleType.java:101-102, 211-220` - PropulsionType enum and accessor
- `VehicleRentalUpdater.java:192-199` - Where propulsion info is currently lost

## Related Research

This document is extracted from the comprehensive research in:
- `thoughts/shared/research/RENTAL_VEHICLE_ROUTING_RESEARCH.md` - Full research including Options A, B, and C

See that document for:
- Option A: New Traverse Modes (EBIKE, ESCOOTER)
- Option C: TraverseMode as Record
- TraverseMode deep dive
- Historical context on GBFS vehicle types
