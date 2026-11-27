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

From the raptor profile of build `step2` (oslo, `profile --quick --levels raptor --top 60`). A
percentage is the share of the ops, unless it says time.

- raptor, `ParetoSet.add` and `qualify`: branch on the two `leftDominanceExist` results instead of
  `ParetoComparator.compare` (2.7 %), `ParetoDominance.of` (1.4 %) and a switch on the enum.
- raptor, `TripScheduleBoardSearch`: the linear scan after the binary search is 3.8 %, and
  `TripPatternForDates.departureTime` 2.8 %. A binary search to the end gives another trip where
  trips overtake each other, so it needs to know that the departures at the stop are sorted.
- raptor, `BitSet.get` with `checkInvariants` and `wordIndex` is still 3.8 %: the boarding and
  alighting checks of `TripPatternForDates` and the reached stops of the standard search.
- raptor, memory (`needs time`): `TripScheduleAlightSearch` (the reverse heuristic search) has 7 %
  of the time and little of the ops: the departure times of large patterns are read from memory.
- raptor, memory: `TripPatternForDates.getTripSchedule` allocates a `TripScheduleWithOffset` for
  every boarding, 17 % of the allocated bytes. A form that shares nothing between threads:
  `TripScheduleBoardSearch` (and `TripScheduleAlightSearch`), which is created for each visit of a
  route, keeps the last schedule it created with its trip index, and returns it again when the next
  search finds the same trip (several arrivals at a stop board the same trip). Try with `--alloc`;
  the results stay identical if no code compares two schedules with `==`.
- raptor, memory (`needs time`): `MultiCriteriaRoutingStrategy.alightOnlyRegularTransferExist` has
  8 % of the time and 1 % of the ops. For every ride at every stop it reads
  `TripPatternForDates.arrivalTimes[stopPos * nTrips + trip]`: the times of a trip are `nTrips`
  ints apart, a new cache line for each stop. No change is designed yet: an array by trip then
  stop makes the alighting sequential and the trip search at a stop strided.
- raptor, memory: the samples of `IntIterators.intIncIterator` (4.4 % of the time) are most likely
  the first reads of a route in `routeTransit`: `TripPatternForDates`, its `RoutingTripPattern`
  (stop indexes, slack index), the two `BitSet`s with their `long[]` and the time arrays are ten
  cache lines apart for each visit of a route. Fewer objects for what a visit reads first.
- raptor, memory: `RaptorRoutingRequestTransitData.routeIndexIterator` allocates a `BitSet` of all
  patterns in every round, and `patternStopIterator` an iterator for every pattern
  (`IntIterators.intIncIterator` has 4.4 % of the time, 1.4 % of the allocated bytes).
- raptor, `MinTravelDurationRoutingStrategy.alightOnlyRegularTransferExist` (4.2 % of the time)
  and `StdTransferEarlyPruning.updateArrival` (`IntIntHashMap.getOrDefault`, 1.3 % of the time):
  the standard (heuristic) searches.

## Being tried

- raptor, memory (`needs time`): `ArrivalParetoSetComparatorFactory.compareC1RelaxedOnC2Dominance`
  has 20 % of the time and 1.5 % of the ops: the stop arrivals of a pareto set are spread over the
  heap. Keep the criteria of the elements in int arrays in the set. How: a set of its own for the
  stop arrivals (`StopArrivalParetoSet` in `rangeraptor.multicriteria.arrivals`, used by
  `McStopArrivals` only) that does what `ParetoSet.add` does, in the same order and with the same
  events for the listener, but compares the new arrival with a copy of the four criteria (arrival
  time, round, c1, c2) of each element in one `int[]`, and with a copy of the criteria of
  `goodElement` in fields. The comparators of `ArrivalParetoSetComparatorFactory` get a form that
  takes the eight ints. The arrived-on-board criterion (egress and via stops only) still asks the
  element. Tried with a full build, `check --quick` and a time benchmark against step6.

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
