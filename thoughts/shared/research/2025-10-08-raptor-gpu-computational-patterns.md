---
date: 2025-10-08T20:26:05+0000
researcher: testower
git_commit: c08d73994a7ea1b1da675880ca636234bda79cea
branch: dev-2.x
repository: OpenTripPlanner
topic: "GPU Utilization Potential in Raptor Algorithm - Computational Patterns Analysis"
tags: [research, codebase, raptor, performance, gpu, matrix-operations, parallelization]
status: complete
last_updated: 2025-10-08
last_updated_by: testower
---

# Research: GPU Utilization Potential in Raptor Algorithm - Computational Patterns Analysis

**Date**: 2025-10-08T20:26:05+0000
**Researcher**: testower
**Git Commit**: c08d73994a7ea1b1da675880ca636234bda79cea
**Branch**: dev-2.x
**Repository**: OpenTripPlanner

## Research Question

Can the Raptor transit routing algorithm in OpenTripPlanner leverage GPU acceleration, given that the computations involve matrix-like calculations?

## Summary

The Raptor algorithm in OpenTripPlanner uses **cache-optimized contiguous integer arrays** and **BitSet operations** that resemble vectorizable computations, but the algorithm structure presents **significant challenges for GPU acceleration**:

1. **Data-dependent control flow**: Boarding and alighting decisions depend on previous round results, creating branch divergence unsuitable for SIMD execution
2. **Sparse irregular access patterns**: Routes serve different stops, patterns have varying lengths, and only a small fraction of stops are touched each round
3. **Limited parallelism per iteration**: Each round processes stops sequentially along routes, with strong data dependencies between rounds
4. **Small working sets in typical queries**: Most queries touch hundreds to thousands of stops, not millions - too small to amortize GPU transfer overhead

However, the implementation does use **CPU-level optimizations** that exploit modern processor architecture:
- Primitive int arrays for cache locality
- BitSet tracking for sparse stop sets
- Sequential memory access patterns
- Optional multi-threading for heuristic searches

The current architecture is optimized for **cache-friendly CPU execution** rather than massively parallel GPU execution.

## Detailed Findings

### 1. Computational Structure of Raptor Algorithm

#### Core Algorithm Pattern

**Location**: `/Users/testower/Developer/github/entur/OpenTripPlanner/raptor/src/main/java/org/opentripplanner/raptor/rangeraptor/RangeRaptor.java:125-151`

The Raptor algorithm operates in nested loops:
- **Outer loop**: Iterates backward over departure time window (search window iteration)
- **Round loop**: For each departure time, runs rounds until convergence
- **Route loop**: Within each round, processes routes serving stops reached in previous round
- **Stop loop**: For each route, traverses stops sequentially

Each round consists of:
1. **Access phase**: Set initial arrivals at stops
2. **Transit phase**: Board trips at reached stops, alight at subsequent stops
3. **Transfer phase**: Walk from transit stops to nearby stops
4. **Convergence check**: Stop if no new stops reached

#### Time-Based State Management

**Location**: `/Users/testower/Developer/github/entur/OpenTripPlanner/raptor/src/main/java/org/opentripplanner/raptor/rangeraptor/standard/besttimes/BestTimes.java:28-213`

State tracked in parallel integer arrays:
```java
private final int[] times;              // Best overall arrival time at each stop
private final int[] transitArrivalTimes; // Best on-board arrival at each stop
private BitSet reachedCurrentRound;      // Stops touched this round
private BitSet reachedLastRound;         // Stops touched last round
```

Updates follow a check-then-update pattern:
```java
public boolean updateBestTransitArrivalTime(int stop, int time) {
  if (isBestTime(stop, time)) {
    times[stop] = time;
    reachedCurrentRound.set(stop);
    return true;
  }
  return false;
}
```

**Characteristics**:
- Direct array indexing by stop ID
- Conditional updates based on time comparison
- BitSet tracking for sparse stop sets
- Sequential array access in most code paths

### 2. Data Structures and Memory Layout

#### Transit Schedule Storage

**Location**: `/Users/testower/Developer/github/entur/OpenTripPlanner/application/src/main/java/org/opentripplanner/routing/algorithm/raptoradapter/transit/request/TripPatternForDates.java:47-112`

Trip schedules stored in flattened 2D arrays:
```java
// Trips stored stop-major: [stop0_trip0, stop0_trip1, stop0_trip2, stop1_trip0, ...]
private final int[] arrivalTimes;
private final int[] departureTimes;

// Access pattern - all trips at a stop are contiguous
IntUnaryOperator getArrivalTimes(int stopPositionInPattern) {
  final int base = stopPositionInPattern * numberOfTripSchedules;
  return (int tripIndex) -> arrivalTimes[base + tripIndex];
}
```

**Memory layout**:
- Arrays sized `nStops * nTrips` per pattern
- Stop-major ordering for sequential access when searching trips at a stop
- Binary search over trip dimension for boarding time

#### Route and Pattern Structure

**Location**: `/Users/testower/Developer/github/entur/OpenTripPlanner/application/src/main/java/org/opentripplanner/transit/model/network/RoutingTripPattern.java:23-87`

Pattern metadata stored in arrays and BitSets:
```java
private final int[] stopIndexes;        // Stop sequence in pattern
private final BitSet boardingPossible;  // Boarding flags per stop position
private final BitSet alightingPossible; // Alighting flags per stop position
```

Access is direct array indexing:
```java
public int stopIndex(int stopPositionInPattern) {
  return stopIndexes[stopPositionInPattern];
}
```

### 3. Computational Operations

#### Pattern 1: Time Arithmetic and Comparisons

**Location**: `/Users/testower/Developer/github/entur/OpenTripPlanner/raptor/src/main/java/org/opentripplanner/raptor/rangeraptor/standard/StdRangeRaptorWorkerState.java:99-120`

Time calculations with direction abstraction:
```java
int arrivalTime = calculator.plusDuration(departureTime, durationInSeconds);
if (exceedsTimeLimit(arrivalTime)) return;

if (newOverallBestTime(stop, arrivalTime)) {
  stopArrivalsState.setAccessTime(arrivalTime, accessPath, bestTime);
}
```

**Operations**:
- Addition: `time + duration`
- Comparison: `time < bestTime`
- Conditional update of state arrays

**Vectorization potential**: These operations could theoretically vectorize, but are gated by control flow.

#### Pattern 2: Trip Schedule Binary Search

**Location**: `/Users/testower/Developer/github/entur/OpenTripPlanner/application/src/main/java/org/opentripplanner/routing/algorithm/raptoradapter/transit/request/TripScheduleBoardSearch.java:164-224`

Finding first valid trip:
```java
// Linear scan for small trip counts
for (int i = tripIndexUpperBound - 1; i >= 0; --i) {
  if (departureTimes.applyAsInt(i) >= earliestBoardTime) {
    candidateTripIndex = i;
  } else {
    break;  // trips are sorted
  }
}

// Binary search for large trip counts, then linear scan
int lower = 0, upper = nTrips;
while (upper - lower > binarySearchThreshold) {
  int m = (lower + upper) / 2;
  if (departureTimes.applyAsInt(m) >= earliestBoardTime) {
    upper = m;
  } else {
    lower = m;
  }
}
```

**Operations**:
- Sequential array scan
- Binary search over sorted times
- Early termination based on data

**Vectorization potential**: Binary search is inherently sequential. Linear scan could use SIMD but with data-dependent exit.

#### Pattern 3: Multi-Criteria Pareto Comparison

**Location**: `/Users/testower/Developer/github/entur/OpenTripPlanner/raptor/src/main/java/org/opentripplanner/raptor/rangeraptor/multicriteria/arrivals/McStopArrival.java:139-162`

Dominance check across criteria:
```java
protected static boolean compareBase(McStopArrival<?> l, McStopArrival<?> r) {
  return (
    l.arrivalTime() < r.arrivalTime() ||
    l.paretoRound() < r.paretoRound() ||
    l.c1() < r.c1()
  );
}
```

Used in pareto set maintenance:
```java
for (int i = 0; i < size; ++i) {
  T it = elements[i];
  boolean leftDominance = leftDominanceExist(newValue, it);
  boolean rightDominance = rightDominanceExist(newValue, it);

  if (leftDominance && rightDominance) {
    mutualDominanceExist = true;
  } else if (leftDominance) {
    removeDominatedElementsFromRestOfSetAndAddNewElement(newValue, i);
    return true;
  } else if (rightDominance) {
    return false;
  }
}
```

**Operations**:
- Multiple scalar comparisons
- Short-circuit boolean logic
- List compaction with two-pointer technique

**Vectorization potential**: Comparisons could vectorize, but control flow and list mutations are inherently sequential.

#### Pattern 4: Route Exploration Loop

**Location**: `/Users/testower/Developer/github/entur/OpenTripPlanner/raptor/src/main/java/org/opentripplanner/raptor/rangeraptor/DefaultRangeRaptorWorker.java:128-184`

Main transit routing loop:
```java
IntIterator stops = state.stopsTouchedPreviousRound();  // Sparse BitSet
IntIterator routeIndexIterator = transitData.routeIndexIterator(stops);

while (routeIndexIterator.hasNext()) {
  var route = transitData.getRouteForIndex(routeIndexIterator.next());
  var pattern = route.pattern();

  IntIterator stop = calculator.patternStopIterator(pattern.numberOfStopsInPattern());

  while (stop.hasNext()) {
    int stopPos = stop.next();
    int stopIndex = pattern.stopIndex(stopPos);

    if (calculator.alightingPossibleAt(pattern, stopPos)) {
      transitWorker.alightOnlyRegularTransferExist(stopIndex, stopPos, alightSlack);
    }

    if (calculator.boardingPossibleAt(pattern, stopPos)) {
      if (state.isStopReachedInPreviousRound(stopIndex)) {
        transitWorker.boardWithRegularTransfer(stopIndex, stopPos, boardSlack);
      }
    }
  }
}
```

**Characteristics**:
- Nested iteration: stops → routes → pattern stops
- Data-dependent: only process routes serving reached stops
- Irregular: patterns have varying numbers of stops
- Conditional: boarding/alighting checks depend on pattern and previous state

**Vectorization potential**: Outer loops are sparse and irregular. Inner loop has potential for SIMD but is short and data-dependent.

#### Pattern 5: Transfer Application

**Location**: `/Users/testower/Developer/github/entur/OpenTripPlanner/raptor/src/main/java/org/opentripplanner/raptor/rangeraptor/multicriteria/McRangeRaptorWorkerState.java:169-182`

Processing transfers from a stop:
```java
private void transferToStop(
  Iterable<? extends McStopArrival<T>> fromArrivals,
  RaptorTransfer transfer
) {
  final int transferTimeInSeconds = transfer.durationInSeconds();

  for (McStopArrival<T> it : fromArrivals) {
    int arrivalTime = it.arrivalTime() + transferTimeInSeconds;

    if (!exceedsTimeLimit(arrivalTime)) {
      arrivalsCache.add(stopArrivalFactory.createTransferStopArrival(it, transfer, arrivalTime));
    }
  }
}
```

**Operations**:
- Iterate over arrivals (typically small count)
- Add constant to each arrival time
- Conditional insertion into cache

**Vectorization potential**: The arithmetic could vectorize, but the small iteration count and cache insertion overhead dominate.

### 4. Current Parallelization Approach

#### Multi-Threading for Heuristics

**Location**: `/Users/testower/Developer/github/entur/OpenTripPlanner/raptor/src/main/java/org/opentripplanner/raptor/service/RangeRaptorDynamicSearch.java:165-205`

Parallel execution of forward and reverse heuristic searches:
```java
if (config.isMultiThreaded() &&
    originalRequest.runInParallel() &&
    s.isEarliestDepartureTimeSet() &&
    s.isLatestArrivalTimeSet() &&
    fwdHeuristics.isEnabled() &&
    revHeuristics.isEnabled()) {

  asyncResult = config.threadPool().submit(fwdHeuristics::run);
  revHeuristics.run();
  asyncResult.get();
}
```

**Characteristics**:
- Task-level parallelism (2 independent searches)
- Thread pool execution
- Synchronization via Future.get()

**Rationale**: Heuristic searches are independent and can run concurrently to establish bounds for the main search.

#### Heuristic Optimization Strategy

**Location**: `/Users/testower/Developer/github/entur/OpenTripPlanner/raptor/src/main/java/org/opentripplanner/raptor/package.md:86-91`

Documentation describes performance characteristics:
- Standard Range Raptor (RR): ~80ms
- Multi-Criteria RR (McRR): ~400ms (5x slower)
- McRR with extra criteria (e.g., walking distance): ~1000ms (12.5x slower)

Strategy: Run fast single-criteria search first to bound multi-criteria search.

#### Search Window Calculation

**Location**: `/Users/testower/Developer/github/entur/OpenTripPlanner/raptor/src/main/java/org/opentripplanner/raptor/rangeraptor/transit/RaptorSearchWindowCalculator.java:75-91`

Heuristics inform search window sizing:
```java
int v = roundStep(
  minSearchWindow.toSeconds() +
  minTransitTimeCoefficient * heuristicMinTransitTime +
  minWaitTimeCoefficient * heuristicMinWaitTime
);
```

**Benefit**: Reduces number of iterations in main search, improving overall performance.

### 5. Cache-Friendly Design

#### Design Philosophy

**Documentation**: `/Users/testower/Developer/github/entur/OpenTripPlanner/raptor/src/main/java/org/opentripplanner/raptor/package.md:34-38`

> "The algorithm also operates largely on contiguous lists of numbers, with adjacency in memory (rather than explicit edge objects) implying reachability of one stop from another. This avoids a lot of pointer-chasing and better exploits processor cache and prefetching. Raptor is part of a family of newer algorithms that account for typical processor architecture rather than just theoretical asymptotic complexity."

#### Primitive Arrays vs Objects

**Location**: `/Users/testower/Developer/github/entur/OpenTripPlanner/raptor/src/main/java/org/opentripplanner/raptor/rangeraptor/DefaultRangeRaptorWorker.java:56-63`

Comment on state implementation:
> "For a long time, we had a state which stored all data as int arrays in addition to the current object-oriented approach. There were no performance differences (=> GC is not the bottleneck), so we dropped the integer array implementation."

**Implication**: The team tested pure int-array state but found modern JVM GC performance sufficient. The current approach balances cache-friendliness with code maintainability.

#### BitSet for Sparse Sets

**Location**: `/Users/testower/Developer/github/entur/OpenTripPlanner/raptor/src/main/java/org/opentripplanner/raptor/rangeraptor/standard/besttimes/BestTimes.java:38-52`

BitSets track which stops were reached:
```java
private BitSet reachedCurrentRound;
private BitSet reachedLastRound;
private final BitSet reachedByTransitCurrentRound;
```

**Benefits**:
- Memory-efficient: 1 bit per stop
- Fast iteration: `nextSetBit()` skips unset bits
- Cache-friendly: sequential memory access
- Pointer swapping avoids copying

#### Flyweight Pattern for Transfers

**Documentation**: `/Users/testower/Developer/github/entur/OpenTripPlanner/raptor/src/main/java/org/opentripplanner/raptor/spi/RaptorTransitDataProvider.java:38-63`

Recommends reusing iterator objects:
> "The iterator element only needs to be valid for the duration of a single iterator step. Hence; It is safe to use a cursor/flyweight pattern to represent both the Transfer and the Iterator<Transfer> - this will most likely be the best performing implementation."

**Benefit**: Avoids allocation overhead when iterating transfers.

### 6. Performance-Critical Sections

#### Identified in Comments

**Short-circuit OR performance note** (`McStopArrival.java:142-143`):
```java
// This is important with respect to performance. Using the short-circuit logical OR(||) is
// faster than bitwise inclusive OR(|) (even between boolean expressions)
```

**Main algorithm loop** (`RangeRaptor.java:93-151`):
- Wrapped in performance timer
- Iterates over departure time window
- For each minute, runs rounds until convergence

**Transit search worker** (`DefaultRangeRaptorWorker.java:128-184`):
- `findTransitForRound()` wrapped in timer
- Most computationally intensive phase
- Processes routes serving reached stops

## Code References

### Core Algorithm
- **Main routing loop**: `raptor/src/main/java/org/opentripplanner/raptor/rangeraptor/RangeRaptor.java:93-151`
- **Transit search**: `raptor/src/main/java/org/opentripplanner/raptor/rangeraptor/DefaultRangeRaptorWorker.java:128-184`
- **State management**: `raptor/src/main/java/org/opentripplanner/raptor/rangeraptor/standard/besttimes/BestTimes.java:28-213`

### Data Structures
- **Trip schedules**: `application/src/main/java/org/opentripplanner/routing/algorithm/raptoradapter/transit/request/TripPatternForDates.java:47-112`
- **Pattern metadata**: `application/src/main/java/org/opentripplanner/transit/model/network/RoutingTripPattern.java:23-87`
- **Pareto sets**: `raptor/src/main/java/org/opentripplanner/raptor/util/paretoset/ParetoSet.java:80-124`

### Parallelization
- **Heuristic parallelism**: `raptor/src/main/java/org/opentripplanner/raptor/service/RangeRaptorDynamicSearch.java:165-205`
- **Thread pool config**: `raptor/src/main/java/org/opentripplanner/raptor/api/request/RaptorEnvironment.java:33-40`
- **Concurrent router**: `raptor/src/main/java/org/opentripplanner/raptor/rangeraptor/ConcurrentCompositeRaptorRouter.java:54-79`

### Optimizations
- **Search window calculation**: `raptor/src/main/java/org/opentripplanner/raptor/rangeraptor/transit/RaptorSearchWindowCalculator.java:75-91`
- **Heuristics adapter**: `raptor/src/main/java/org/opentripplanner/raptor/rangeraptor/standard/heuristics/HeuristicsAdapter.java:23-217`
- **Binary search**: `application/src/main/java/org/opentripplanner/routing/algorithm/raptoradapter/transit/request/TripScheduleBoardSearch.java:164-224`

## Architecture Documentation

### Current Parallelization Model

**Task-Level Parallelism**:
- Two independent heuristic searches (forward/reverse) can run in parallel
- Optional thread pool injected via `RaptorEnvironment`
- Default is single-threaded execution

**No Data Parallelism**:
- Individual searches run on single thread
- State updates are sequential
- No vectorization or SIMD usage detected

### Memory Access Patterns

**Sequential Access** (cache-friendly):
- Trip time arrays accessed stop-by-stop
- BitSet iteration over reached stops
- Pattern traversal in stop-position order

**Random Access** (less cache-friendly):
- Stop index lookup in patterns: `pattern.stopIndex(stopPos)`
- Best time lookup by stop: `times[stop]`
- Transfer lookup: `transferIndex.getForwardTransfers(stopIndex)`

**Sparsity**:
- Most stops not reached in typical query
- BitSet iteration skips unreached stops
- Routes filtered to those serving reached stops

### Computational Bottlenecks

Based on documentation and code structure:

1. **Route exploration**: Nested loops over routes and stops
2. **Trip schedule search**: Binary search + linear scan per boarding attempt
3. **Pareto set maintenance**: Linear scan for dominance checks (multi-criteria)
4. **Transfer enumeration**: Iterating transfers from each reached stop

## Analysis: GPU Suitability

### Challenges for GPU Acceleration

#### 1. Control Flow Divergence

**Problem**: Boarding and alighting decisions are data-dependent
```java
if (calculator.alightingPossibleAt(pattern, stopPos)) {
  if (onTripIndex != UNBOUNDED_TRIP_INDEX) {
    transitWorker.alightOnlyRegularTransferExist(stopIndex, stopPos, alightSlack);
  }
}

if (calculator.boardingPossibleAt(pattern, stopPos)) {
  if (state.isStopReachedInPreviousRound(stopIndex)) {
    transitWorker.boardWithRegularTransfer(stopIndex, stopPos, boardSlack);
  }
}
```

**Impact**: Branch divergence would cause thread serialization in GPU warps.

#### 2. Irregular Data Structures

**Problem**: Patterns have varying numbers of stops, routes serve different stop sets
- Pattern lengths range from 2 stops to 100+ stops
- Number of trips per pattern varies widely
- Transfer counts per stop are highly irregular

**Impact**: Load imbalancing across GPU threads, poor warp utilization.

#### 3. Sequential Dependencies

**Problem**: Round N depends on results from Round N-1
- Cannot start Round 2 until Round 1 completes
- Stop arrivals in current round depend on previous round
- Limited parallelism within a round

**Impact**: GPU would be underutilized waiting for synchronization points.

#### 4. Small Working Sets

**Problem**: Typical queries touch hundreds to thousands of stops, not millions
- Norway dataset (SpeedTest): ~10,000 stops
- Query might reach 500-2000 stops total
- Each round touches 50-500 stops

**Impact**: Insufficient parallelism to saturate GPU, transfer overhead dominates.

#### 5. Sparse Access Patterns

**Problem**: Only a fraction of stops reached each round
- BitSet tracking maintains sparsity
- Route iteration filters to relevant routes
- Most array elements never accessed

**Impact**: GPU memory bandwidth wasted on zero-masking or divergent access.

### Potential GPU-Friendly Operations

Despite challenges, some operations could theoretically benefit:

#### 1. Transfer Enumeration

For each reached stop, compute arrival times at all transfer destinations:
```
for stop in reachedStops:
  for transfer in transfers[stop]:
    arrivalTime = times[stop] + transfer.duration
    if arrivalTime < times[transfer.toStop]:
      times[transfer.toStop] = arrivalTime
```

**GPU approach**: Launch one thread per (stop, transfer) pair, atomic min on destination.

**Challenge**: Transfer counts are irregular (0-50 per stop), causing divergence.

#### 2. Trip Time Search (Bulk)

If processing many patterns in parallel:
```
for each pattern:
  for each stop in pattern:
    binarySearch(tripTimes[pattern][stop], earliestBoardTime)
```

**GPU approach**: Launch threads for all (pattern, stop) pairs.

**Challenge**: Binary search is sequential; irregular pattern lengths cause imbalance.

#### 3. Pareto Comparison (Bulk)

When comparing many arrivals against a pareto set:
```
for arrival in newArrivals:
  for existing in paretoSet:
    checkDominance(arrival, existing)
```

**GPU approach**: Launch threads for all comparisons, reduce results.

**Challenge**: Pareto sets are small (typically 5-50 elements), insufficient parallelism.

### Matrix Operations: Not Present

The research question mentions "matrix calculations," but **Raptor does not perform dense matrix operations**:

- No matrix multiplication
- No matrix-vector products
- No linear algebra operations

The "contiguous lists of numbers" are **jagged arrays** (arrays of varying-length arrays), not dense matrices. This is fundamentally different from graph algorithms that use adjacency matrices or neural network computations.

### Comparison to Successful GPU Graph Algorithms

Algorithms that GPU-accelerate well typically have:
- ✅ **Regular structure**: Fixed-size grids or dense matrices
- ✅ **Massive parallelism**: Millions of independent operations
- ✅ **Simple control flow**: Few branches, no data-dependent exits
- ✅ **Dense access patterns**: Most data elements processed

Raptor has:
- ❌ **Irregular structure**: Varying pattern lengths, sparse stop sets
- ❌ **Limited parallelism**: Thousands of operations per round
- ❌ **Complex control flow**: Many conditionals, early exits
- ❌ **Sparse access patterns**: Most stops/routes not touched

### Current CPU Optimizations Are Well-Suited

The existing design exploits **CPU strengths**:
- Branch prediction for conditionals
- Cache prefetching for sequential access
- Low-latency random access for stop lookups
- Efficient sparse set iteration with BitSets

Modern CPUs excel at these patterns, making the current approach appropriate.

## Historical Context (from thoughts/)

No prior research or discussions about GPU utilization for Raptor were found in the thoughts directory. This appears to be a novel research direction for this codebase.

## Related Research

None found in `thoughts/shared/research/`.

## Open Questions

1. **Hybrid CPU-GPU approach**: Could transfer enumeration be offloaded to GPU while keeping route exploration on CPU?

2. **Batch routing**: If routing many queries simultaneously (e.g., for analysis), could query-level parallelism benefit from GPU?

3. **Alternative algorithms**: Are there GPU-friendly transit routing algorithms that could complement or replace Raptor for specific use cases?

4. **Data structure redesign**: Would restructuring transit data into dense matrices enable GPU acceleration, and would performance gains offset the loss of sparsity exploitation?

5. **SIMD on CPU**: Could AVX-512 or similar SIMD instructions accelerate bulk operations (e.g., time comparisons across multiple stops) without GPU complexity?

6. **Profile-guided optimization**: What percentage of runtime is spent in potentially vectorizable operations vs. inherently sequential logic?

## Conclusion

The Raptor algorithm in OpenTripPlanner is **unlikely to benefit significantly from GPU acceleration** in its current form due to:
- Irregular, sparse data structures
- Data-dependent control flow
- Limited parallelism per round
- Small working sets in typical queries
- Strong inter-round dependencies

The existing design is optimized for **cache-friendly CPU execution** using:
- Contiguous integer arrays
- BitSet sparse set tracking
- Sequential memory access patterns
- Optional CPU multi-threading for heuristics

**No matrix operations exist** in the traditional sense (dense linear algebra). The "matrix-like" aspects are jagged arrays optimized for sequential scanning, not bulk computation.

Future work could explore:
- SIMD vectorization on CPU for specific loops
- Query batching for analysis workloads
- Hybrid approaches for specific sub-problems (e.g., transfer enumeration)

However, the current architecture is well-suited to the problem structure and modern CPU capabilities.
