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

- raptor, `DefaultRangeRaptorWorker.boardAndAlightRoute`: ask `isStopReachedInPreviousRound` before
  `boardingPossibleAt`. Few stops of a pattern are reached, and boarding is possible at almost all
  of them. `BitSet.get` with its helpers is 6.7 %, most of it from the boarding and alighting
  checks.
- raptor, `DefaultRangeRaptorWorker.boardAndAlightRoute`: skip the alighting until the pattern was
  boarded: nothing is on board before that.
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

Nothing yet.
