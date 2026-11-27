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

## Ideas not tried yet

From the raptor profile at the start of `perf/raptor-1` (oslo, the code of `base`). A percentage
is the share of the ops, unless it says time.

- raptor, `ParetoSet.add` and `qualify`: branch on the two `leftDominanceExist` results instead of
  `ParetoComparator.compare` (2.6 %), `ParetoDominance.of` (1.3 %) and a switch on the enum.
- raptor, `TripScheduleBoardSearch`: the linear scan after the binary search is 3.6 %, and
  `TripPatternForDates.departureTime` 2.7 %. A binary search to the end gives another trip where
  trips overtake each other, so it needs to know that the departures at the stop are sorted.
- raptor, destination pruning (`HeuristicsProvider.rejectDestinationArrivalBasedOnHeuristic`,
  `ParetoSet.qualify` and the destination comparator): about 20 %. A probe that arrives before
  every destination arrival qualifies without the loop over them.
- raptor, memory: `ArrivalParetoSetComparatorFactory.compareC1RelaxedOnC2Dominance` has 18 % of the
  time and 1.4 % of the ops: the stop arrivals of a pareto set are spread over the heap. Keep the
  criteria of the elements in int arrays in the set.
- raptor, memory: `TripPatternForDates.getTripSchedule` allocates a `TripScheduleWithOffset` for
  every boarding, 16 % of the allocated bytes.
- raptor, memory: `RaptorRoutingRequestTransitData.routeIndexIterator` allocates a `BitSet` of all
  patterns in every round, and `patternStopIterator` an iterator for every pattern (5 % of the
  time).

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
