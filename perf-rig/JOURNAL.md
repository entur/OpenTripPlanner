# Journal of the performance experiments

Every idea that was tried, accepted or not, and the ideas not tried yet, so no session tries the
same thing twice. How it is used: "A series of experiments" in the README.

Baseline: build `base` is `perf-rig` 479537253a (on dev-2.x 9dcae85fe9), datasets `oslo` and
`norway` as in the README.

An entry, numbered in the order the ideas were tried, on any branch:

```
### 7. accepted: the idea in a few words
- where: raptor, `ParetoSet.add`
- result: ops raptor 0.9712 against step5 (0.9431 against base), results identical
- commit: 1a2b3c4d5e on perf/raptor-1, build step7
- note: what the next session should know, if anything
```

A rejected idea has `why:` instead of `commit:`. A time check is an entry too (`### 8. time check`),
with the verdict of the benchmark for each level.

The idea a session works on is under "Being tried" from before it changes the code until its entry
is written: a session that was interrupted leaves changes behind, and the next one must know whose
they are.

## Ideas not tried yet

From the raptor profiles of build `step2` and of build `step8` (oslo, `profile --quick --levels
raptor --top 60`, the last one is `profile/20261127-200513-wt`). A percentage is the share of the
ops, unless it says time; the shares are of step8 where they were measured again. The order is
what each idea promises in time, not in ops: since entry 8 the ops are known to be a poor guide
where code is shared between uses (calls that are not inlined) and where branches are hard to
predict.

- raptor, `HeuristicsProvider` and `DestinationArrivalPaths.qualify`, the destination pruning of
  every candidate: 12 % of the time (`HeuristicsProvider.get` 3.0 %, `ParetoSet.leftDominanceExist`
  4.1 %, `ParetoSet.qualify` 2.0 %, `ParetoComparator.compare` 1.9 %, the comparator 1.1 %) and
  13 % of the ops. Two changes, each an idea of its own:
  - `HeuristicsProvider.get` reads `stops[stop]` and then the `HeuristicAtStop` object: two
    reads from memory that depend on each other, for every candidate. Keep the three values
    (min travel duration, min number of transfers, min cost) of a stop in one `int[]`
    (`needs time`).
  - The destination set is a generic `ParetoSet`: `qualify` calls the comparator through
    `ParetoComparator.compare`, which every kind of set shares, so it is not inlined (as the
    stop arrivals before entry 8). A check of its own for the destination arrivals, with the
    criteria of the probe as values. Decide it with `--counters` (instructions), not the ops.
- raptor, `TripScheduleBoardSearch`: the linear scan after the binary search is 4.2 % of the ops
  and 8.9 % of the time, `TripPatternForDates.departureTime` 3.1 %, the binary search 2.0 % of the
  time. Where the departures at every stop are in the order of the trips (to be computed when
  `TripPatternForDates` fills its arrays, as a flag of `TripSearchTimetable`), the first trip at
  or after a time is found by a binary search to the end with the same result: the linear scan
  finds the lowest index of the run of trips that depart late enough, which is the first such
  trip when the departures are sorted. Only for the unbounded search: with a trip index bound
  (the standard search, already on a trip) the scan is one or two steps. Same for
  `TripScheduleAlightSearch` with the arrivals. Caution: the ops will say 6 % less, but a binary
  search has a branch that is hard to predict in every step and the scan has one; the threshold
  of 50 was tuned on time. Try a binary search without a branch (`base = time < t ? base + half :
  base`), and decide on the cycles and the branch mispredictions of `--counters`.
- raptor, the order of the two checks of a candidate (after "create a stop arrival only when it
  is accepted"): the heuristic check runs for all 62 M candidates and rejects 37 %, the set of
  the stop then rejects 84 % of the rest with a far cheaper check. The set first would save most
  of the 12 % of the destination pruning. Not identical by construction: a rejection updates
  `goodElement` in the set it happens in, and a set with a listener (via stops) gets an event
  for a rejected arrival, so the sets with a listener must keep the order, and the rest needs
  the argument that `goodElement` never changes a result.
- raptor, `ParetoSet.add` and `qualify`: branch on the two `leftDominanceExist` results instead of
  `ParetoComparator.compare` (1.0 %), `ParetoDominance.of` and a switch on the enum. The stop
  arrivals no longer use it (entry 8); left are the pattern rides and the destination arrivals.
- raptor, `BitSet.get` with `checkInvariants` and `wordIndex` is 4.2 %: the boarding and
  alighting checks of `TripPatternForDates` and the reached stops of the standard search.
- raptor, "is the stop reached in the previous round" for every stop of every route visited:
  `McStopArrivals.hasArrivalsAfterMarker` 1.0 %, `StopArrivalParetoSet.hasElementsAfterMarker`
  0.5 %, `McRangeRaptorWorkerState.isStopReachedInPreviousRound` 0.4 %; it reads the set object
  of the stop from memory. A bit set of the stops with arrivals after the marker answers it
  without the set (`touchedStops` is such a set, but it is cleared at another moment).
- raptor, memory (`needs time`): `TripScheduleAlightSearch` (the reverse heuristic search) has
  8.4 % of the time and 0.35 % of the ops: the arrival times of every pattern of the network are
  read from memory that is not in the cache. No change is designed yet.
- raptor, memory: `TripPatternForDates.getTripSchedule` allocates a `TripScheduleWithOffset` for
  every boarding, 15 % of the allocated bytes. A form that shares nothing between threads:
  `TripScheduleBoardSearch` (and `TripScheduleAlightSearch`), which is created for each visit of a
  route, keeps the last schedule it created with its trip index, and returns it again when the next
  search finds the same trip (several arrivals at a stop board the same trip). Try with `--alloc`;
  the results stay identical if no code compares two schedules with `==`.
- raptor, memory: `TransitGroupPriorityRideFactory.createPatternRide` is 14 % of the allocated
  bytes: a ride is created for every boarding and then offered to the pareto set of the rides.
  Create it when the set accepts it, as for the stop arrivals.
- raptor, memory (`needs time`): `MultiCriteriaRoutingStrategy.alightOnlyRegularTransferExist` has
  10 % of the time and 1.2 % of the ops. For every ride at every stop it reads
  `TripPatternForDates.arrivalTimes[stopPos * nTrips + trip]`: the times of a trip are `nTrips`
  ints apart, a new cache line for each stop. No change is designed yet: an array by trip then
  stop makes the alighting sequential and the trip search at a stop strided.
- raptor, memory: the samples of `IntIterators.intIncIterator` (5.0 % of the time) are most likely
  the first reads of a route in `routeTransit`: `TripPatternForDates`, its `RoutingTripPattern`
  (stop indexes, slack index), the two `BitSet`s with their `long[]` and the time arrays are ten
  cache lines apart for each visit of a route. Fewer objects for what a visit reads first.
- raptor, memory: `RaptorRoutingRequestTransitData.routeIndexIterator` allocates a `BitSet` of all
  patterns in every round (1.6 % of the time), and `patternStopIterator` an iterator for every
  pattern (1.6 % of the allocated bytes).
- raptor, `MinTravelDurationRoutingStrategy.alightOnlyRegularTransferExist` (4.8 % of the time)
  and `StdTransferEarlyPruning.updateArrival` (`IntIntHashMap.getOrDefault`, 2.2 % of the time):
  the standard (heuristic) searches. The map has the egress stops only; a `BitSet` of the egress
  stops, or an `int[]` of all stops, answers "not an egress stop" without hashing.

## Being tried

- raptor, memory: create a stop arrival only when it is accepted. `transitToStop` and
  `transferToStop` of `McRangeRaptorWorkerState` create an arrival for every candidate (31 % and
  21 % of the allocated bytes) and keep it in a list until the commit. Measured with the ops of
  the profile of step8: 62 M arrivals are created (34 M transit, 29 M transfer), the heuristic
  check rejects 23 M, the set of the stop gets 39 M and accepts 6.3 M: nine of ten are created
  for nothing. Keep a candidate as values (ride or from-arrival and transfer, stop, arrival
  time, c1) and let the heuristic check and `StopArrivalParetoSet.add` work on the values; the
  object is created when the set accepts it, or when a listener or the debugger needs a rejected
  one. The same checks in the same order with the same values, so the results are identical by
  construction. Tried with `try --alloc` against step8. Draft:
  `../perf-rig-data/drafts/create-stop-arrival-when-accepted.md`.
  State 2026-11-27 20:40: the code is in the working tree (new `StopArrivalCandidates`, changes in
  `McRangeRaptorWorkerState`, `StopArrivalParetoSet`, `McStopArrivals`, `HeuristicsProvider`, the
  two stop arrival factories; tests `StopArrivalCandidatesTest`, `StopArrivalParetoSetTest`).
  `try --alloc`: results identical, `B allocates less (43.0%)` (5.229 GB to 2.982 GB), but the ops
  are 1.0256: storing seven values for a candidate is more bytecodes than a constructor, and the
  ops count `Arrays.fill` of the clear element by element. Not accepted on that; it needs
  `B faster` of a time benchmark with `--counters` against step8 (after a full build and
  `check --quick`), which is the next command.

## Tried

### 1. accepted: ask if the stop is reached before asking if boarding is possible
- where: raptor, `DefaultRangeRaptorWorker.boardAndAlightRoute`
- result: ops raptor 0.9721 against base, results identical
- commit: cf2efa9c3a on perf/raptor-1, build step1
- note: `BitSet.get` with its helpers went from 6.7 % to 4.6 % of the ops of base; the rest is
  mostly the alighting check. `mutate` of the file: 5 places, 4 found. Not found: `&&` to `||` in the
  choice between `alightConstrainedTransferExist` and `alightOnlyRegularTransferExist`, which do
  the same in the multi-criteria search.

### 2. accepted: no alighting before the first attempt to board the pattern
- where: raptor, `DefaultRangeRaptorWorker.boardAndAlightRoute`
- result: ops raptor 0.9792 against step1 (0.9519 against base, the product of the two steps),
  results identical
- commit: f04139771a on perf/raptor-1, build step2
- note: `BitSet.get` with its helpers is 3.6 % of the ops of base now.

### 3. time check: stopped early, not conclusive
- where: raptor, step2 (f04139771a) against base, `bench --levels raptor --forks 5`
- result: raptor: no significant difference, time B/A 0.989 (99 % interval 0.608 to 1.611),
  allocated bytes 0.997, results identical, from 2+2 of the 5+5 forks
- note: stopped after 38 min: a fork took 8.5 min, so the 10 forks take 85 min, not 20. The laptop
  was in use: other processes used 1.9 to 2.8 cores during each fork, and the benchmark warns that
  the timings are not reliable. A time check on a quiet machine is still owed for steps 1 and 2.

### 4. time check: fewer instructions confirmed, time not conclusive
- where: raptor, step2 (f04139771a) against base, `bench --quick --levels raptor --counters`, 3+3
  forks
- result: time B/A 0.996 (99 % interval 0.930 to 1.066): no significant difference. Of the CPU:
  instructions 0.9709 (0.9654 to 0.9764), cycles 0.9987 (0.909 to 1.097), cache misses 1.034
  (0.879 to 1.216), branch mispredictions 1.006 (0.989 to 1.024). Results identical.
- note: the laptop was in use, and the benchmark warns that the timings are not reliable. The two
  steps remove 2.9 % of the instructions the CPU executes, against 4.8 % of the ops, and the
  cycles for an instruction went from 0.339 to 0.349: what was removed was cheap. Whether any time
  is gained is open: a time check on a quiet machine, with more forks, is still owed for steps 1
  and 2.

### 5. time check: laptop with the other programs closed, still no time gain shown
- where: raptor, step2 (f04139771a) against base, `bench --quick --levels raptor --forks 4
  --counters`
- result: time B/A 0.979 (99 % interval 0.947 to 1.011): no significant difference. Of the CPU:
  instructions 0.9751 (0.9646 to 0.9858), cycles 0.9866 (0.9549 to 1.0192), cache misses 1.001
  (0.939 to 1.068), branch mispredictions 1.003 (0.992 to 1.014). Allocated bytes 1.000. Results
  identical.
- note: other processes still used 0.5 to 1.0 cores during three of the four forks of base (the
  benchmark warns), and those forks are up to 4 % off in cycles. The four forks of step2 were not
  disturbed and agree to 0.3 % in time and cycles. The one fork of base that was not disturbed is
  as fast as the forks of step2: steps 1 and 2 gain a percent or two of time at most, and it is
  not shown.

### 6. accepted: an arrival that arrives before every destination arrival qualifies without the loop
- where: raptor, `DestinationArrivalPaths.qualify` (the destination pruning of McRaptor)
- result: ops raptor 0.8799 against step2 (geomean 0.9566; 0.8376 against base, the product of the
  steps), results identical; `accept`: 550 results identical, also repeated and on 8 threads, and
  all 9671 OTP tests pass
- commit: a148179598 on perf/raptor-1, build step6
- note: the set keeps the best arrival time of every arrival it accepted. When the arrival time is
  a criterion of the destination comparator (timetable, or arrival time forward, or departure
  time in reverse), a probe with a better arrival time than that is dominated by no element and
  by no stale `goodElement`, so the loop over the set returns true anyway. The work removed is
  the loop of `ParetoSet.qualify` with the comparator, `ParetoDominance.of` and the probe set-up.
  `DestinationArrivalPathsTest` got the new constructor argument only. For the case work apart
  from the loop: `mutate` found 8 of 9 bugs in `DestinationArrivalPaths` and
  `DestinationArrivalConfig` (the one not found only decides whether a cost-mismatch warning is
  logged), and 4 of 10 in `DestinationArrivalParetoSetComparators`: not found were `||` to `&&`
  in the comparators of lines 88, 94, 95, 103 and 106 (reverse and departure-time configurations)
  and `<` to `<=` in `lowC1RelaxedIfC2IsOptimal`. No case runs
  `DestinationArrivalConfig.createDestArrivalPathsStdSearch` (the standard search with paths).

### 7. time check: steps 1, 2 and 6 on a quiet laptop: fewer cycles, the time gain is not shown
- where: raptor, step6 (a148179598) against base, `bench --quick --levels raptor --forks 5
  --counters`, in the idle mode, started as administrator
- result: time B/A 0.997 (99 % interval 0.961 to 1.033): no significant difference. Of the CPU:
  instructions 0.9255 (0.9160 to 0.9352), cycles 0.9667 (0.9511 to 0.9826), cache misses 1.000
  (0.966 to 1.036), branch mispredictions 0.9947 (0.9849 to 1.0046). Allocated bytes 1.000.
  Results identical.
- note: no warning: other processes used 0.14 to 0.23 cores during a fork, once 0.42. The three
  steps have 16 % fewer ops, 7.4 % fewer instructions and 3.3 % fewer cycles (0.322 to 0.338
  cycles for an instruction). The verdict is of the geometric mean of the items, where a short
  search counts as much as a long one, and the gain of step 6 is in the long searches: the total
  time of a fork is 0.963 (0.922 to 1.005, the same interval over the forks, computed by hand),
  and of the third of the items that take longest (47 to 621 ms, 85 % of the time) 0.951. The
  forks of a build differed by up to 2.4 % from their mean in time. Steps 1, 2 and 6 have their
  time check now: nothing is slower, and the cycles confirm a gain of 3 %.

### 8. accepted: the pareto set of the stop arrivals keeps the criteria of its elements in an int array
- where: raptor, `McStopArrivals` with the new `StopArrivalParetoSet`, and
  `ArrivalParetoSetComparatorFactory`
- result: time raptor `B faster (12.6%)` against step6: B/A 0.874 (99 % interval 0.833 to 0.917),
  B/A of the total time 0.803 (0.762 to 0.847, computed by hand). Of the CPU: instructions 0.8087
  (0.8027 to 0.8148), cycles 0.7909 (0.7390 to 0.8464), cache misses 0.8554 (0.8004 to 0.9141),
  branch mispredictions 0.9945 (0.9863 to 1.0027). Allocated bytes 1.011. Ops raptor 1.0313
  against step6 (0.8638 against base, the product of the steps): more ops, see the note. Results
  identical; `accept`: 550 results identical, also repeated and on 8 threads, and all 9677 OTP
  tests pass
- commit: b27fe977ee on perf/raptor-1, build step8
- note: tried as a `needs time` idea in the idle mode: `bench --quick --levels raptor --forks 5
  --counters --baseline step6`, no warning (other processes used 0.13 to 0.41 cores during a
  fork). The set does what `ParetoSet.add` does, in the same order and with the same events, but
  compares a new arrival with a copy of the arrival time, round, c1 and c2 of each element in one
  `int[]`, and with a copy of the criteria of `goodElement` in fields; the arrived-on-board
  criterion of the egress and via stops still asks the element. `StopArrivalParetoSetTest` adds
  random arrivals to the new set and to a `ParetoSet` with the comparator of the factory, and
  compares the elements, the marker and the events after each.
  The ops count 3 % more work, and the processor executes 19 % fewer instructions: the gain is
  not only the memory (14 % fewer cache misses). `ParetoSet.add` calls its comparator from one
  place for every kind of set (stop arrivals, pattern rides, destination arrivals, paths), so the
  JIT compiler sees many comparators there and inlines none; the set of the stop arrivals has
  call sites of its own now, with one or two comparators. The ops do not show a call that is not
  inlined. What follows for the next ideas: the instructions of `--counters` show a difference of
  1 % (the time one of 4 %), and hot code that is shared between uses with different lambdas or
  implementations is worth a look even where the ops are few.
  The initial capacity of the set is 8 elements (16 in `ParetoSet`): the criteria take 16 bytes
  for an element, and the allocated bytes are 1.1 % more.
  The report of the benchmark first said UNEVEN: the rig compared the forks of both builds with
  each other, so a gain above 15 % looked like a disturbed fork. Fixed in the rig (ff788f6095 on
  this branch, ed2a433518 on perf-rig): a fork is compared with the forks of its own build.
