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

State of the series `perf/raptor-1` on 2026-11-28: the steps 1, 2, 6, 8, 9, 10, 11, 13, 16, 17,
18 and 20 are accepted; step13 against base is 25.8 % faster for a search and 36 % in total
time (entry 15), and 16 to 20 add fewer cache misses. The last build is `step20`.

The numbers below are from the raptor profile of build `step13` (oslo, `profile --quick --levels
raptor --top 60`, `profile/20261128-024922-wt`); the steps 16 to 18 came after it, so profile
again before trusting a share. A percentage is the share of the time samples, unless it says ops.
In that profile: the pareto sets of the stop arrivals 17 % (`compareC1Relaxed` 10.0 %,
`StopArrivalParetoSet.add` 4.8 %), the alighting of the two standard (heuristic) searches 12.7 %,
the destination pruning 11.5 % (`ParetoSet.qualify` 6.9 %, `HeuristicsProvider.get` 3.3 %, now in
entry 16), the alighting of the multi-criteria search 6.7 %, the first reads of a route 6.0 %
(`IntIterators.intIncIterator`, partly entry 17), `boardWithRegularTransfer` 5.8 %, the transfers
7.1 %, the trip search 4.9 %, `ArrivalView.arrivedBy` 2.2 %. A pass over the cases visits 2.9 M
routes and 110 M stop positions, makes 62 M candidates for stop arrivals and accepts 6.3 M.

What was learnt about choosing: the ops show less than half of the gains (the series is 0.78 in
ops and 0.64 in time). The two things the ops do not show are calls that the JIT compiler does
not inline because several uses share one piece of code (entries 8 and 10, found with
`RIG_JAVA_OPTS="-XX:+UnlockDiagnosticVMOptions -XX:+PrintInlining"` and one fork of `bench`:
look for "failed to inline" in the fork log), and memory (entries 9, 13, 17). A guess about
memory is worth a benchmark only if the reads it removes can be counted beforehand (entries 14
and 19 could not, and gave nothing). `accept` checks the oslo cases only; an idea that is not
identical by construction needs the norway cases too (entry 12).

A decision for the owner of the rig, before the two ideas that promise most:
`GeneralizedCostRelaxFunction.relax` overflows `int` (`value * normalizedRatio`) for a cost above
21 hours with the ratio 1.10 of the datasets, and above 5.8 hours with 4.0. Fixing it (the product
in `long`) changes results of long journeys, so it needs a new baseline. Not verified by a run
that it is what made entry 12 differ; see that entry.

- raptor, blocked on that decision: ask the pareto set of the stop before the destination pruning
  (entry 12, ops 0.9422 against step11). The patch is
  `../perf-rig-data/drafts/stop-set-before-destination-pruning.patch` (against step11), the
  design and the argument in `stop-set-before-destination-pruning.md`. It needs `--dataset norway
  check` besides `accept`.
- raptor, blocked for the same reason: reject a candidate when it is found, not when it is
  committed. `transitToStop` and `transferToStop` could ask the set of the stop
  (`StopArrivalParetoSet.positionOf` of the patch of entry 12) before they add a candidate to the
  `StopArrivalCandidates`: an arrival the set rejects now is rejected at the commit too, since a
  set only gets better. Four of five candidates would never be stored, about 3 % of the
  instructions. It holds only if the dominance is transitive, and not at stops with a listener.
- raptor, the stop arrival sets, 17 % of the time: `StopArrivalParetoSet.add` is 362 bytecodes,
  and the JIT compiler does not inline it in the commit loop ("hot method too big" in the log of
  entry 9), a call with seven arguments for each of 39 M candidates. Most are rejected by
  `goodElement`: a small method with only that test, calling the rest, would be inlined. No ops
  change, and a gain of a percent or two that 5 forks do not show in time: look at the
  instructions of `--counters`, with more forks.
- raptor, memory: the sets of the stops are an object and two arrays for each stop, somewhere in
  the heap; every candidate that reaches a set reads it. One `int[]` for all stops with a fixed
  block for each (the size, the criteria of `goodElement` and of the first elements), and the
  objects only for what does not fit, would make it one cache line for a stop. A rewrite of
  `StopArrivalParetoSet` and `McStopArrivals`; not designed. The oslo arrays would be 1 MB for a
  search, the norway ones 7 MB.
- raptor, `MultiCriteriaRoutingStrategy.board`: `prevArrival.arrivedBy(ACCESS)` is an interface
  call that is not inlined (2.2 %, 13 M boardings). Only an access arrival has no previous
  arrival, so `prevArrival.previous() == null && prevArrival.arrivedBy(ACCESS)` asks the same and
  makes the call for access arrivals only. Ops 0.07 % less: too small for the ops rule and for
  the time of 5 forks.
- raptor, memory: `TransitGroupPriorityRideFactory.createPatternRide` is a third of the allocated
  bytes of step18 (0.75 GB of 2.44 GB in a pass): a ride is created for every boarding and then
  offered to the pareto set of the rides, which keeps a few. Create it when the set accepts it,
  as for the stop arrivals (entry 9): the criteria of a ride are the trip sort index, the
  relative c1 and c2. Needs a set that compares values, as entry 8, for the two ride comparators.
- raptor, the destination pruning: the comparator of the destination arrivals is one of 18
  lambdas (`DestinationArrivalParetoSetComparators`), a class of its own for each configuration.
  A JVM that serves several configurations (depart after and arrive by, with and without transit
  group priority) has several of them at the call sites of `ParetoSet.qualify`, and the JIT
  compiler inlines none if no one has 90 % of the calls. One comparator class with the
  configuration in fields has no such limit; it is a rewrite of the 18 lambdas into one method,
  to be checked with `DestinationArrivalParetoSetComparatorsTest`. The cases of the rig are 96 %
  one configuration, so the rig shows no gain of it; it is for production.
- raptor, the standard (heuristic) searches, a fifth of the time: their alighting is 12.7 % with
  0.4 % of the ops. They visit every route that can be reached once in a round, and for every
  stop read the trip time and `BestTimes` (two `int[]` of all stops). Nothing is designed; ask
  first what of the two searches the multi-criteria search uses, and if a search can stop early.
- raptor, `StdTransferEarlyPruning.updateArrival`: `IntIntHashMap.getOrDefault` for every
  improved arrival of the standard searches, 1.8 %. A `BitSet` of the egress stops answers "not
  an egress stop" first. More ops (`BitSet.get` is 28 bytecodes with its assertions).
- raptor, memory: `RaptorRoutingRequestTransitData.routeIndexIterator` allocates a `BitSet` of all
  patterns in every round (1.4 %), and `patternStopIterator` an iterator for every route visit.
- raptor, memory (`needs time`): arrival and departure times of a trip interleaved in one array
  of `TripPatternForDates`, so the departure the trip search finds is in the cache line of the
  arrivals the ride reads next. At most one cache line for a boarding.
- raptor, `StopArrivalCandidates.clear` fills the object arrays with `null` (`Arrays.fill`, 1.0 %).
  Without it the list would keep rides and arrivals of the round before reachable until the next
  round overwrites them, and until the end of the search at most; not tried because of that.

## Being tried

Nothing.

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

### 9. accepted: a stop arrival is created when its stop accepts it, not for every candidate
- where: raptor, `McRangeRaptorWorkerState` with the new `StopArrivalCandidates`,
  `StopArrivalParetoSet.add`, `McStopArrivals`, `HeuristicsProvider`
- result: ops raptor 0.9730 against step8 (0.8405 against base, the product of the steps), and
  `B allocates less (43.3%)` (5.229 GB to 2.965 GB). Results identical; `accept`: 550 results
  identical, also repeated and on 8 threads, and all 9687 OTP tests pass
- commit: c637f400a1 on perf/raptor-1, build step9
- note: measured with the ops of the profile of step8: 62 M arrivals were created (34 M by
  transit, 29 M by transfer), the heuristic check rejected 23 M, the set of the stop got 39 M and
  accepted 6.3 M. Now `transitToStop` and `transferToStop` add a candidate to a
  `StopArrivalCandidates`: what the arrival is created from (the ride, or the arrival and the
  transfer) and its stop, arrival time, round, c1 and c2 in an `int[]`. `commit()` runs the same
  two checks in the same order on the values, and the set calls a supplier for the arrival when
  it accepts it, or when it rejects it and has a listener (the via stops need the rejected
  arrival), as the heuristic check does when the stop is debugged. The arrivals of a segment
  before (via searches) are kept in the same list, so the order is as it was. The c2 of an
  arrival by transit is asked of the stop arrival factory (`transitStopArrivalC2`): with c1 only
  the arrival has `NOT_SET` where the ride has 0.
  Three forms were tried. The first (two object slots and five values for every candidate, the
  loop in the worker state calling a method for one candidate): `B allocates less (43.0%)` but
  ops 1.0256, and in a time benchmark against step8 (5 forks, `--counters`, no warning) time B/A
  0.987 (0.959 to 1.016), no significant difference, instructions 1.0275 (1.0138 to 1.0415),
  cycles 0.9652 (0.9571 to 0.9734), cache misses 0.8330 (0.7897 to 0.8787). `-XX:+PrintInlining`
  (`RIG_JAVA_OPTS`, one fork of each build, the fork logs have it) showed why: the method for one
  candidate was "already compiled into a big method" and not inlined in the loop, a call with six
  arguments for every candidate. The second form (one object slot for an arrival by transit, the
  check written in the loop): ops 1.0080. The third, accepted, has the loop in
  `StopArrivalCandidates`, reading its arrays without a call: ops 0.9730.
  What that benchmark also showed: the cycles of the forks of a build agree to 0.5 %, the time
  does not, because the frequency of the cores differs between forks (3.43 to 3.71 GHz, cycles
  for a nanosecond). A difference of 3 % in time is below what 5 forks show, in cycles it is far
  above.
- time check, first try, no result: `bench --quick --levels raptor --forks 5 --counters
  --baseline step8 --candidate step9` (22:07 to 22:30) is UNEVEN, and fork A3 was run again
  (0.51 cores of other processes). Time B/A 0.982 (0.865 to 1.115), instructions 1.0023 (0.9767
  to 1.0287), cycles 0.9550 (0.9101 to 1.0021), cache misses 0.8430 (0.8054 to 0.8823), branch
  mispredictions 1.0316 (0.9925 to 1.0723), allocated bytes 0.571. The cores ran at 3.5 to
  3.8 GHz in the first seven forks and at 4.0 to 4.1 GHz in the last three (A3, A4, B4), which
  were 9 % faster in time for it. The benchmark started right after `accept`: the laptop was
  hot and ran slower until it had cooled, see the time check of entry 10. Fork B0 executed 4 % more
  instructions than the other four forks of step9 (2.397e11 against 2.305e11 to 2.317e11): the
  JIT compiler does not compile the same in every fork. Among the forks at the same frequency
  step9 has 5 % fewer cycles and 4 % less time than step8, but that is not a verdict.
- time check: `B faster (5.3%)` against step8, the same benchmark again (22:31 to 22:52), no
  warning (fork A1 was run again, 0.64 cores of other processes): time B/A 0.947 (99 % interval
  0.925 to 0.970), B/A of the total time 0.929 (0.907 to 0.951, computed by hand). Of the CPU:
  instructions 0.9922 (0.9751 to 1.0096), cycles 0.9246 (0.9011 to 0.9486), cache misses 0.7762
  (0.7480 to 0.8055), branch mispredictions 1.0204 (1.0026 to 1.0386). Allocated bytes 0.567.
  The cores ran at 4.1 to 4.25 GHz in all ten forks. The gain is memory: the same instructions,
  22 % fewer cache misses, 0.349 to 0.326 cycles for an instruction.

### 10. accepted: ParetoSet asks its comparator directly for the dominance in each direction
- where: raptor, `ParetoSet.add`, `qualify` and `removeDominatedElementsFromRestOfSetAndAddNewElement`
- result: ops raptor 0.9833 against step9 (0.8264 against base, the product of the steps), results
  identical; `accept`: 550 results identical, also repeated and on 8 threads, and all 9687 OTP
  tests pass
- commit: a87b62705b on perf/raptor-1, build step10
- note: `add` and `qualify` called `ParetoComparator.compare`, which calls the comparator twice,
  makes a `ParetoDominance` of the two answers and is followed by a switch on it; and they tested
  `goodElement` through three private helper methods. Now they call `leftDominanceExist` twice
  and branch on the two booleans: the same comparisons in the same order. The ops that are gone
  are those of `compare` (1.0 %), `ParetoDominance.of` (0.5 %) and the helpers (0.9 %).
  The reason to try it was the JIT log of step9 (`-XX:+PrintInlining` with `RIG_JAVA_OPTS`, the
  fork log `bench/20261127-211203-step8-vs-wt/B-0.log`): `ParetoSet.qualify` is inlined in the
  destination pruning with its loop, but the two calls of the test of `goodElement` were "failed
  to inline: virtual call", because the helper methods were shared by `add` (the pattern rides)
  and `qualify` (the destination arrivals) and saw the comparators of both. 44 M probes of a
  pass take that path. `qualify` is only used for the destination arrivals, so its own call sites
  see their comparator alone. What the time and the instructions say is in the time check below.
  `ParetoComparator.compare` and `ParetoDominance` are still used by `RaptorViaLocation` and by
  tests.
- time check: no significant difference in time, fewer instructions and cycles. `bench --quick
  --levels raptor --forks 5 --counters --baseline step9 --candidate step10` (23:29 to 23:50), no
  warning (fork B3 was run again, 0.54 cores of other processes): time B/A 0.982 (99 % interval
  0.913 to 1.055). Of the CPU: instructions 0.9418 (0.9181 to 0.9661), cycles 0.9598 (0.9349 to
  0.9855), cache misses 0.981 (0.937 to 1.027), branch mispredictions 0.997 (0.971 to 1.024).
  Allocated bytes 1.000. The ops said 1.7 % less work, the processor executes 5.8 % fewer
  instructions: the calls that are inlined now.
  The time interval is wide because the cores ran at 3.7 to 3.8 GHz in the first seven forks and
  at 4.0 GHz in the last three, as in the first time check of entry 9. Both benchmarks started
  right after `accept`, which loads all cores for twenty minutes (the build and the OTP tests);
  the benchmark of entry 9 that started after another benchmark ran at 4.1 to 4.25 GHz from the
  first fork. So the laptop is hot after `accept` and runs slower until it has cooled, about a
  quarter of an hour: a time benchmark should not start right after `accept` or a full build.
  (The "Modern Standby" events in the system log are at about the same times, 23:41 to 23:43
  here, but there was one in the benchmark at 4.1 to 4.25 GHz too, without any change.)

### 11. accepted: the trip to board is found with a binary search where the times are in trip order
- where: raptor (the code is in `application`), `TripScheduleBoardSearch`,
  `TripScheduleAlightSearch`, `TripPatternForDates`, `TripSearchTimetable`
- result: ops raptor 0.9578 against step10 (0.7916 against base, the product of the steps),
  results identical; `accept`: 550 results identical, also repeated and on 8 threads, and all 9689
  OTP tests pass
- commit: a9999e6d92 on perf/raptor-1, build step11
- note: the unbounded search did a binary search until 50 trips were left and then stepped
  through them (15 steps on average, 13 M searches in a pass). `TripPatternForDates` now notes,
  while it fills its arrays, whether the arrivals and the departures at every stop are in the
  order of the trips (`TripSearchTimetable.arrivalsInTripOrder()`, `departuresInTripOrder()`).
  Where they are, the trips that depart late enough are the last ones, the stepping ends at the
  first of them, and a binary search over all trips finds the same trip. Where trips overtake
  each other the search is as before (a test in each of the two search tests has such trips),
  and so is the search with a trip index bound (the standard search that is already on a trip:
  one or two steps). The alight search of the reverse searches is the mirror image.
  The flags cost two comparisons for each stop time when the transit data of a request is
  made, which is not in the raptor level; `e2e` has not been measured.
  A binary search has a branch that is hard to predict in every step, where the stepping had
  one for a search; the time check shows no more branch mispredictions for it.
- time check: `B faster (3.7%)` against step10, `bench --quick --levels raptor --forks 5
  --counters --baseline step10 --candidate step11` (00:45 to 01:07), no warning (forks B0 and B1
  were run again, 0.53 and 0.84 cores of other processes): time B/A 0.963 (99 % interval 0.932
  to 0.996), B/A of the total time 0.971. Of the CPU: instructions 0.9726 (0.9469 to 0.9991),
  cycles 0.9720 (0.9308 to 1.0150), cache misses 0.942 (0.869 to 1.021), branch mispredictions
  1.0095 (0.9814 to 1.0385). Allocated bytes 1.010. The benchmark started a quarter of an hour
  after `accept` had ended, and the cores ran at 4.0 to 4.3 GHz in all ten forks.

### 12. rejected: ask the pareto set of the stop before the destination pruning
- where: raptor, `McStopArrivals.addStopArrival`, `StopArrivalParetoSet`, `StopArrivalCandidates`
- result: ops raptor 0.9422 against step11, oslo results identical; `accept` passed (550 oslo
  results identical, also repeated and on 8 threads, all 9695 OTP tests). But
  `--dataset norway check --quick`: 1 of 161 raptor results differs, the api and routing results
  are identical
- why: it changes a recorded result. Norway case `transit-069` (Harstad to south of Oslo, 17 to
  25 hours) returns 21 raptor paths, not 22: the path with 7 transfers and c1 4049768 of
  iteration 52080 is gone. Build step11 gives the recorded result for the case, so it is this
  idea.
- note: the idea was to ask the set of the stop first (`positionOf`, cheap, rejects most) and the
  destination pruning only for a candidate the set would accept, except at stops with a listener.
  A candidate is accepted iff both pass, in either order; what differs is which candidates
  update `goodElement`, the hint of the two sets. With a transitive dominance a hint changes no
  result, and the dominance of the stop arrivals and of the destination arrivals is transitive
  if the relax function is monotone and never lowers a cost (worked out in the draft). The unit
  test `addsAnArrivalAtItsPositionOrNotAtAll` of the patch found no difference on random
  arrivals either.
  Most likely cause, NOT verified by a run: `GeneralizedCostRelaxFunction.relax` computes
  `(value * normalizedRatio) / 0x100 + slack` in `int`. With the 1.10x of this request
  (`relaxC1: f(x) = 1.10 * x + 720.0`, ratio 282/256) the product overflows for a c1 above
  7 615 000 (21 hours of generalized cost) and the relaxed cost becomes negative: an arrival that
  expensive is then never beaten on cost by one with a better c2, the dominance is no longer
  transitive, and the result depends on the order and the hints. The paths of this case cost
  3.7 to 6.1 million, with walks of 2 million each, so worse arrivals above 7.6 million during
  the search are likely. With a ratio of 4.0, the largest allowed, the limit is 5.8 hours.
  This is a bug in OTP whatever the idea: with such costs the result of today is arbitrary too.
  Fixing it (the product in `long`) changes results, so it is not done here: it needs a decision
  and a new baseline. After that fix this idea should give identical results; the code is kept
  as `../perf-rig-data/drafts/stop-set-before-destination-pruning.patch` (against step11), the
  design in `stop-set-before-destination-pruning.md`.
  Two things for the loop. `accept` checks the oslo cases only; an idea that is identical by an
  argument and not by construction needs the norway cases too, and they found this. And the
  norway graph needs a 12 GB heap: `--dataset norway check --quick` ran (2 minutes), but the
  full check of step11 (`check --build step11`, all 539 cases) was stopped by Claude Code after
  some minutes because the laptop (32 GB) was critically low on memory. It was not started
  again. So for the accepted steps 8 to 11: every 4th norway case is identical (145 of them
  with this idea on top of step11, and `transit-069` with step11 itself); the other norway
  cases are not checked.

### 13. accepted: the times of a pattern are kept by trip, then stop
- where: raptor (the code is in `application`), `TripPatternForDates`
- result: time raptor `B faster (7.8%)` against step11: B/A 0.922 (99 % interval 0.892 to 0.952),
  B/A of the total time 0.898. Of the CPU: instructions 0.998 (0.975 to 1.021), cycles 0.8863
  (0.8590 to 0.9145), cache misses 0.6385 (0.5860 to 0.6958), branch mispredictions 1.008 (0.987
  to 1.030). Allocated bytes 1.000. The ops are the same (a memory layout). Results identical;
  `accept`: 550 results identical, also repeated and on 8 threads, and all 9689 OTP tests pass
- commit: a2b8ada106 on perf/raptor-1, build step13
- note: tried as a `needs time` idea: full build, `check --quick`, then `bench --quick --levels
  raptor --forks 5 --counters --baseline step11` (02:06 to 02:28), no warning, started a quarter
  of an hour after the build; the cores ran at 3.9 to 4.2 GHz in all ten forks.
  `arrivalTimes` and `departureTimes` were `[stopPos * nTrips + tripIndex]`, the trips at a stop
  next to each other, which suited the stepping of the trip search. A ride reads the arrival of
  its trip at every stop it passes: `nTrips` ints apart, a cache line for each stop. Now they
  are `[tripIndex * nStops + stopPos]`: the stops of a trip are next to each other, and the
  binary search of entry 11 (which made this possible: its probes are far apart either way)
  reads the same trips at every stop of a route, so the cache lines of one stop serve the next.
  A third of the cache misses of the raptor level are gone. The patterns that are not in trip
  order still step through up to 50 trips, a cache line for each now; no case got slower by
  more than 1 %.
  Not tried: arrival and departure times of a trip interleaved in one array, so the departure
  that the search finds is in the cache line of the next arrivals.

### 14. rejected: ask the touched stops before the set of a stop if it is reached
- where: raptor, `McStopArrivals.hasArrivalsAfterMarker`
- result: time raptor B/A 1.003 against step13 (99 % interval 0.972 to 1.035): no significant
  difference. Of the CPU: instructions 1.014 (0.976 to 1.054), cycles 0.999 (0.953 to 1.047),
  cache misses 1.012 (0.931 to 1.100), branch mispredictions 0.998 (0.957 to 1.041). Results
  identical (`check --quick`)
- why: no gain. The idea was that the set object of every stop of every route visited is read
  from memory only to find that it has no new arrivals, and that `touchedStops.get(stop) &&
  arrivals[stop].hasElementsAfterMarker()` (the same answer, the bit set is 2 KB) saves those
  reads. The cache misses and the cycles are the same, so those reads are not where the cache
  misses are: either most stops of a visited route have no set yet, or their sets are in the
  cache. `bench --quick --levels raptor --forks 5 --counters --baseline step13` (03:12 to 03:33),
  no warning (fork B0 was run again), a quarter of an hour after the build, 3.9 to 4.2 GHz.
- note: what a `needs time` idea promises should be checked against the counters of a profile
  first, if there is a way: this one cost fifty minutes. A count of the stop positions that have
  a set, and of the sets with new arrivals, would have told.

### 15. time check: the whole series, step13 against base
- where: raptor, step13 (a2b8ada106) against base, `bench --quick --levels raptor --forks 5
  --counters --baseline base --candidate step13` (03:34 to 03:54)
- result: `B faster (25.8%)`: time B/A 0.742 (99 % interval 0.732 to 0.752), B/A of the total
  time 0.640 (8.23 s to 5.27 s for the 134 searches). Of the CPU: instructions 0.6689 (0.6536 to
  0.6846), cycles 0.6174 (0.5972 to 0.6382), cache misses 0.4485 (0.4234 to 0.4751), branch
  mispredictions 1.004 (0.982 to 1.026). Allocated bytes 0.582. Results identical.
- note: no warning, other processes used 0.13 to 0.25 cores, the cores ran at 3.9 to 4.25 GHz.
  The steps are 1, 2, 6, 8, 9, 10, 11 and 13. The searches that gained least are the arrive-by
  searches (0.86 to 0.88) and the short ones; the long searches and the paging searches gained
  most (0.53 to 0.57). The ops of the series are 0.7916 (the product of the steps, step13 has
  the ops of step11): the ops show less than half of what the time shows.

### 16. accepted: the heuristics of a stop are three values in an int array
- where: raptor, `HeuristicsProvider`
- result: ops raptor 0.9985 against step13 (0.7904 against base, the product of the steps),
  results identical; `accept`: 550 results identical, also repeated and on 8 threads, and all 9689
  OTP tests pass
- commit: fa5660cec8 on perf/raptor-1, build step16
- note: the provider kept a `HeuristicAtStop` object for each stop it was asked for, in an array
  of references: two reads from memory that depend on each other for every candidate checked
  against the destination (3.3 % of the time in the profile of step13). Now the min travel
  duration, the min number of transfers and the min cost of a stop are three ints next to each
  other in one array, fetched from the heuristics when a stop is first asked for, as before.
  The duration is kept plus one, so the zero of a new array is "not fetched" and the array needs
  no filling. The gain in ops is small (no accessor calls); the gain it is meant for is the
  memory, see the time check.
- time check: no significant difference. `bench --quick --levels raptor --forks 5 --counters
  --baseline step13 --candidate step16` (04:36 to 04:52), no warning, ten minutes after
  `accept`: time B/A 0.992 (99 % interval 0.952 to 1.033). Of the CPU: instructions 1.009 (0.970
  to 1.049), cycles 0.986 (0.956 to 1.016), cache misses 0.954 (0.909 to 1.000), branch
  mispredictions 0.992 (0.952 to 1.033). Allocated bytes 1.000. A few percent fewer cache
  misses at most; the step stays on its ops.

### 17. accepted: TripPatternForDates keeps what a visit of the route reads from its pattern
- where: raptor (the code is in `application`), `TripPatternForDates`
- result: ops raptor 0.9914 against step16 (0.7836 against base, the product of the steps),
  results identical; `accept`: 550 results identical, also repeated and on 8 threads, and all 9689
  OTP tests pass
- commit: 056b8ee79e on perf/raptor-1, build step17
- note: `stopIndex(pos)` (110 M calls in a pass, one for every stop position of every route
  visited), `slackIndex()`, `numberOfStopsInPattern()` and `transitReluctanceFactorIndex()` went
  to the `RoutingTripPattern`, one more object to read for each of the 2.9 M route visits. The
  three ints are fields now and the stop indexes an array of the `TripPatternForDates`, copied
  when the transit data of a request is made (the pattern does not hand out its array): a few
  ints for each pattern, next to time arrays of thousands. `e2e` has not been measured.
- time check: no significant difference in time, fewer cache misses. `bench --quick --levels
  raptor --forks 5 --counters --baseline step16 --candidate step17` (05:30 to 05:48), no warning,
  ten minutes after `accept`: time B/A 0.982 (99 % interval 0.947 to 1.018), B/A of the total
  time 0.976. Of the CPU: instructions 1.004 (0.972 to 1.037), cycles 0.971 (0.941 to 1.002),
  cache misses 0.905 (0.846 to 0.967), branch mispredictions 1.005 (0.973 to 1.038). Allocated
  bytes 1.000. A tenth of the cache misses were the pattern objects.

### 18. accepted: the trip search keeps the schedule of the last trip it found
- where: raptor (the code is in `application`), `TripScheduleBoardSearch`,
  `TripScheduleAlightSearch`
- result: `B allocates less (18.6%)` against step17 (2.994 GB to 2.438 GB), ops raptor 0.9955
  (0.7800 against base, the product of the steps). Results identical; `accept`: 550 results
  identical, also repeated and on 8 threads, and all 9689 OTP tests pass
- commit: d8018a23eb on perf/raptor-1, build step18
- note: `TripPatternForDates.getTripSchedule` creates a `TripScheduleWithOffset` whenever it is
  asked, and the search asked for every boarding it found (18 % of the allocated bytes of
  step17, 3.6 % of the time in the profile of step13). A search object is made for one visit
  of a route and used by one thread, and the arrivals at a stop often board the same trip: it
  keeps the last schedule with its trip index and returns it again for the same index. Several
  rides, arrivals and paths can now share one schedule object for the same trip; nothing in
  Raptor or the routing compares schedules by identity (searched for it), and the schedule only
  caches what it looks up about its own trip.
- time check: no significant difference in time, fewer cache misses. `bench --quick --levels
  raptor --forks 5 --counters --baseline step17 --candidate step18` (06:36 to 06:53), no warning,
  ten minutes after `accept`: time B/A 0.985 (99 % interval 0.956 to 1.016). Of the CPU:
  instructions 0.9955 (0.968 to 1.024), cycles 0.986 (0.960 to 1.014), cache misses 0.901 (0.866
  to 0.937), branch mispredictions 1.013 (0.980 to 1.046). Allocated bytes 0.814.

### 19. rejected: create the criteria array of a stop arrival set before its elements array
- where: raptor, `StopArrivalParetoSet` (the order of two field declarations)
- result: time raptor B/A 1.002 against step18 (99 % interval 0.967 to 1.037): no significant
  difference. Of the CPU: instructions 1.005 (0.985 to 1.025), cycles 1.004 (0.981 to 1.028),
  cache misses 0.997 (0.959 to 1.036), branch mispredictions 1.006 (0.985 to 1.027). Results
  identical (`check --quick`)
- why: no gain. The guess was that the criteria of the first elements would be in the cache line
  after the set object, and not one further on. The cache misses are the same: the two lines are
  next to each other either way and the processor fetches both. `bench --quick --levels raptor
  --forks 5 --counters --baseline step18` (07:10 to 07:30), no warning, ten minutes after the
  build.
- note: with entry 14 this is the second guess about the memory of the stop arrival sets that
  the counters did not confirm. The two that worked (13 and 17) removed reads that could be
  counted beforehand: a read for every stop of every ride, an object for every route visit.
  `compareC1Relaxed` still has a tenth of the time samples; what is slow there is most likely
  the first read of the set of a stop, which is one object in the heap for each stop whatever
  the order of its fields.

### 20. accepted: the boarding and alighting flags of a stop position are kept with its stop index
- where: raptor (the code is in `application`), `TripPatternForDates`
- result: ops raptor 0.9725 against step18 (0.7585 against base, the product of the steps),
  results identical; `accept`: 550 results identical, also repeated and on 8 threads, and all 9689
  OTP tests pass
- commit: 71b67723d2 on perf/raptor-1, build step20
- note: `boardingPossibleAt` and `alightingPossibleAt` asked two `BitSet` objects, 88 M times in
  a pass: an object and a `long[]` each, four more places in memory for every route visit, and
  `BitSet.get` with its helpers was 3.5 % of the ops (the ops count its assertions as if they
  ran, so that share promised more than there was). The stop index of the same stop position
  is read from an array of the `TripPatternForDates` right before (entry 17); the two flags are
  bits 30 and 29 of that int now, set from the bit sets when the transit data of a request is
  made. A stop index above 2^29 is refused there with an exception.
  The first `accept` failed with 26 OTP tests in error: the test helper `TestRouteData` gave the
  constructor `null` for the two bit sets, which were read only when a flag was asked for. The
  helper gives the bit sets of its pattern now, as `RaptorRoutingRequestTransitDataCreator`
  does; no assertion is changed.
