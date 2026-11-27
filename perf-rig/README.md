# perf-rig

A correctness and performance rig for experimenting with OTP: refactor aggressively, then prove
that every routing result is unchanged and measure what got faster. Not OTP-style code; not for
upstream.

## What it does

- **Correctness (`check`)**: loads a real graph the way `OTPMain --load` does (same Dagger wiring,
  same config), runs a few hundred varied Transmodel `trip` queries and writes three layers per
  query, which are compared with a recorded baseline:
  - `api`: the full GraphQL JSON (every leg, estimated call, polyline, step, cost, page cursor)
  - `routing`: every field of every itinerary/leg of the `RoutingResponse`, exact doubles,
    geometries as hashes of all coordinates, walk steps with the hash of the edges they traverse
  - `raptor`: every Raptor call: the request with all access/egress (= the street search results),
    and every path returned, in Raptor's order
  - `--repeat` runs everything again in the same JVM (caches), `--threads N` runs it concurrently
    (thread safety); both must give the same results as the first run. The requests must leave
    the graph as it was: a temporary edge that stays is a difference.
- **Coverage (`coverage`)**: a check says nothing about code that no case runs. `coverage` lists
  the methods changed since the baseline that no case executes, and how much of each package the
  cases execute.
- **Work done (`bench --ops`)**: an agent counts the bytecode instructions executed, per item and
  per method. The count does not depend on the machine, the operating system or the JIT compiler:
  the same build gives the same total within 0.01 % in another JVM run, so a 0.1 % change of the
  work shows, and the report says in which methods. It does not show memory, cache and JIT
  effects.
- **Time (`bench`)**: replays the same queries in interleaved A/B forks (ABBA) with warmup and
  iterations: wall time and allocated bytes per item. A fork is one sample of a build: the
  confidence interval is the 99 % t-interval of the difference between the forks of A and of B.
  Two JVM runs of the same code differ by 1-2 %, so time confirms a gain of a few percent, not a
  small one, see Notes.
- **Profile (`profile`)**: the methods of a build with the most time samples, allocated bytes and
  ops, per level: where to look for a gain.
- Both benchmarks and the profile measure these levels, and fingerprint every result, so a gain
  only counts when the results are identical in both builds and equal to the first, recorded run:
  `api`: the GraphQL call; `e2e`: `RoutingService.route`; `raptor`: the captured
  `RaptorService.route` calls replayed alone; `street`: direct, access and egress street searches
  alone (linking excluded); `link`: linking origin/destination alone.

## The loop

Experiments are worked on in this worktree, on a branch off `perf-rig` (see "A series of
experiments"). All commands run from the repo root, one at a time (a second one stops, or waits
with `--wait`), and use the dataset `oslo` unless `--dataset norway` comes first.

**0. Baseline, once per base commit**, from a clean tree (35 min with Norway and the OTP tests):

```bash
python perf-rig/rig.py build                    # full Maven compile + rig: build 'wt'
python perf-rig/rig.py freeze base --force      # freeze it as 'base'
python perf-rig/rig.py record base --build base --repeat --threads 8   # golden results
python perf-rig/rig.py --dataset norway record base --build base --repeat --threads 8
python perf-rig/rig.py test --scope all --name base   # OTP test record of the baseline
```

The test record is needed because some OTP tests fail on Windows on every build; later runs are
compared with it, not with "no failures".

**1. Find where the work is** (4 min): `rig.py profile --quick --levels raptor` (or `street`,
`e2e`) lists the methods with the most time samples, allocated bytes and ops. A method with a far
larger share of the time than of the ops waits for memory; the other way round it is cheap code.

**2. Before changing a file, see what the rig would notice there** (1.5 min per bug):
`rig.py coverage --quick`, then `rig.py mutate FILE... --count 10` plants small bugs in the
executed code of the files. A bug that is NOT FOUND shows what a mistake there gets away with.

**3. Change the code, then one round** (4-7 min): `rig.py try --levels raptor` compiles the changed
files (`build --fast`), checks every 4th case against the baseline (`check --quick`) and compares
the work done with `base` (`bench --ops --quick`): are the results identical, how much less work,
in which methods. Repeat until the change does what was intended.

**4. Accept, then commit** (25 min): `rig.py accept` does a full build (Dagger, changed
signatures), needs every changed method to be executed by a case (`coverage`), and runs all cases,
also repeated and on 8 threads, with all OTP tests (`check --repeat --threads 8 --tests all`): the
tests of the changed module alone would not notice what breaks in a module that uses it. One idea
per commit.

**5. Compound**: `rig.py freeze step1` after an accepted change. The next
`rig.py try --against step1` shows what the next change gains alone, `--against base` the total;
the results are always checked against the record of `base`. Each benchmark adds a line per level
to `bench/history.tsv`.

**6. The time check**, a check of its own and not a part of a series: when it is asked for, the ops
say the total is several percent, and the machine does nothing else.

```bash
python perf-rig/rig.py bench --quick --levels raptor --forks 5   # the levels the steps changed; half an hour
python perf-rig/rig.py --dataset norway check --repeat --threads 8 --tests none
python perf-rig/rig.py test --scope all         # the whole OTP test suite
```

The benchmark compares `base` with the working tree. Without `--quick` a fork takes 8 min, not 3.

- `B faster`: the gain is confirmed.
- A warning about other processes or uneven forks: no result, run it again.
- `no significant difference` with the ops ratio inside the interval: time can not show a gain
  that small. More forks make the interval narrower.
- `B slower`, or an interval that lies above the ops ratio: a step costs time that the ops do not
  show. Compare the steps one by one (`bench --quick --forks 5 --baseline step1 --candidate step2`).

The result is an entry in the journal, with the steps it covers. An idea marked `needs time` in
the journal is tried here too, with this benchmark against the last step in place of the ops
comparison of `try`: it needs `B faster`.

**A series of experiments** (the skill `perf-loop` in `.claude/skills` runs the loop this way):

- `perf-rig/JOURNAL.md` on branch `perf-rig` has every idea that was tried on any branch, accepted
  or not, and the ideas not tried yet. Before working on a branch, get it:
  `git checkout perf-rig -- perf-rig/JOURNAL.md`, and read it before picking an idea.
- The result of an idea is written there, also when the idea is rejected and its code is thrown
  away, and committed with `rig.py journal "<message>"`: on the branch worked on and, without a
  checkout, on `perf-rig`.
- A series is a branch `perf/<name>` off `perf-rig`, with one commit for each accepted idea. Stay
  on it: the gains add up, and the profile after a gain shows the next one.
- An idea is accepted on the work done (ops), which a busy machine does not change. The time is
  not measured in a series: that is the time check (6).
- Start a new branch off `perf-rig` for another design of code the series already changed (the
  two are then compared with `base`), for a rewrite that later ideas should not build on yet, or
  when several ideas in a row gave nothing.
- An accepted idea is frozen as `step<N>`, N being its number in the journal. The frozen builds
  are in the data directory only; the journal has the commit of each, to build it again.
- To get a gain upstream, cherry-pick its commit to a branch off `dev-2.x`.

**Rules**

1. A difference is a regression until explained. An intended change of results gets a new
   baseline (step 0, with a note why), never a silent one.
2. A performance claim needs 0 result mismatches, and a time `bench` with a verdict "B faster",
   i.e. a confidence interval that excludes 1.0. `bench --ops` finds and explains a change of the
   work done, but less work is not always less time, and a gain from memory layout does not show
   in the ops at all. Measure at the level the change is in (`raptor`, `street`, `link`).
3. Code that no case runs is not checked: if `coverage` lists a changed method, add a case that
   runs it (`<dataset>.extra.json`), or say that only the OTP tests cover the change.
4. A change of graph building or of the serialized graph: build a graph per build
   (`rig.py graph --build base`, `rig.py graph`) and compare them with `graph-fingerprint` and
   `graph-diff`, then `check` on the new graph: the fingerprint does not include the transfers.
5. After changing the rig or the cases: `rig.py unit` (unit tests of the rig, seconds),
   `rig.py selftest --fast --quick`, then a new baseline.

## Layout

- `perf-rig/rig.py`: the CLI (Python 3, stdlib only).
- `perf-rig/src`: the Java side, compiled by `rig.py` with `javac` against the OTP build under
  test (not part of the Maven build). It is compiled into every build from the current sources, so
  a frozen baseline and the candidate always run the same rig code. If an experiment changes OTP
  code the rig uses, the rig is changed with it; a frozen build then keeps the rig classes compiled
  for it earlier (with a warning). A few classes live in OTP packages to reach package-private
  code (`RigRaptorServiceFactory`, `RigStreetSearch`).
- `perf-rig/agent`: the agent of `bench --ops`, `coverage` and `profile`.
- `perf-rig/cases/<dataset>.json`: the cases (Transmodel trip query variables), generated with a
  seed by `gen-cases`; `<dataset>.extra.json`: hand-written cases, kept when the cases are
  generated again.
- Production changes, each its own commit:
  - `ConstructApplicationFactory` exposes five more bindings, so the rig can build
    `DefaultRoutingService` like `RequestScopedModule` does.
  - Determinism: OTP gave different results for the same request, in another JVM run or even in
    the same one, where it iterated sets in identity-hash or `Set.of` order. In the linking
    (`LinkingContextFactory`, `VertexLinker`, `HashGridSpatialIndex.query`,
    `StreetNearbyStopFinder`) the order of the origin vertices and zero-distance accesses changed
    the tie-breaking in A* and Raptor, and for a station-to-station query even the page cursor. In
    the transfer optimization the order of the path tails decided which of two transfers with the
    same cost was returned. These sets are insertion ordered now.
  - Reproducible graph builds: `VertexLabel.VertexWithEntityLabel` hashed an enum by identity, so
    the vertices of the graph were iterated in another order in each build, stops were linked in
    another order, split points moved by ~1 mm, and 10 % of the results changed. Two builds now
    give identical graphs (`graph-fingerprint`/`graph-diff`), so a change of graph building can be
    checked by building a graph for each build (`rig.py graph --build NAME`).
- Data outside the repo, in `../perf-rig-data` (or `$PERF_RIG_DATA`): `datasets/` (config + inputs),
  `graphs/`, `builds/` (frozen class copies), `records/`, `bench/`, `profile/`, `coverage/`, `logs/`.

## Commands

- `--quick` (record/check/bench/coverage/profile): every 4th case of each category. A baseline of
  all cases can be checked with any selection. `--only REGEX` selects by case id or category.
- `rig.py build [--fast]`: every command that runs build `wt` stops if the OTP sources changed
  after it was compiled.
- `rig.py bench --ops`: one fork per build, 1 warmup and 2 iterations; the report has the totals
  per level, the items with more or less work, the methods with the most work and the methods
  whose work changed. `noise` is the difference between the iterations of one build.
- `rig.py bench` (time): 3 forks per build, on the fastest cores only (`--cpus fast`, the P-cores
  of a hybrid CPU; `--cpus all`, or a list like `2-9`), at high priority and with `-Xbatch`. The
  report gives the time of each fork, and warns if a fork was uneven or other processes used the
  CPU. Both kinds of benchmark write `summary.json` next to the report.
- `rig.py mutate FILE... [--count N]`: needs `coverage [--quick]` first, and checks each bug with
  the same cases. "found: the run fails" is a bug that makes a request fail.
- `rig.py test [--scope changed|all]` runs the OTP tests (`changed`: the modules changed since the
  commit of build `base`, with their dependencies), writes `tests/<name>.json` and compares it with
  `tests/base.json`. `check --tests none|changed|all` chooses the tests of a check.
- `rig.py journal MESSAGE` commits `perf-rig/JOURNAL.md` on the current branch and on `perf-rig`.
  It stops if the file lacks an entry that `perf-rig` has, so an entry of another branch is not
  lost.
- `rig.py selftest [--fast] [--quick]` applies each patch in `perf-rig/mutations`, builds, checks
  and reverts: every planted bug (`*.differs.patch`) must be found, a change that keeps all results
  (`*.same.patch`) must not be. Run it after changing the rig or the cases. A mutation that gives
  identical results may be equivalent (it cannot change any result), not a gap: an inclusive
  `isBefore` in Raptor only touches the standard (heuristic) search, where equal times change
  nothing, and a 60 s overestimate of the remaining cost never decided a destination pruning.
- `rig.py graph-fingerprint --build B` + `rig.py graph-diff A B`: the content of two graphs
  (every public getter of every vertex and edge, stops, patterns, timetables), for changes of graph
  building or of the street model.
- `rig.py diff A B` compares two records; reports go to `records/<dataset>/<B>/diff-vs-<A>.txt`.
- `rig.py report <bench dir>` prints a benchmark report again.
- `rig.py graph [--build NAME]` builds the dataset graph. A change of the graph format or of graph
  building needs a graph per build: `rig.py graph --build base` and `rig.py graph` (for `wt`); each
  build loads `graphs/<dataset>/<build>/graph.obj` if it exists, else `graphs/<dataset>/default`.

## Datasets

A dataset is a directory in `../perf-rig-data/datasets` with its inputs, the three OTP configs
(timeouts raised, no updaters, an absolute service period so a rebuilt graph has the same trips)
and optionally `rig.json`: `heap`, `graphHeap`, `graphInTwoSteps`, `raptorChunk`, `busyStops`. The
inputs are hard links to the `otp-data` files. A new dataset: `rig.py --dataset NAME graph`,
`gen-cases`, `record`. The cases use dates in the second week of the service period.

- `oslo`: Ruter NeTEx of 2026-11-09 and the Oslo/Akershus OSM extract, service period 2026-11-23
  to 2026-12-20. Bicycle transfers are added for coverage; car transfers are left out (no car
  ferries, and an empty transfer type fails the startup). 488 generated cases and 2 hand-written
  ones.
- `norway`: the national NeTEx of 2026-11-19 and the Norway OSM extract, same period, with car and
  bicycle transfers; 104 000 stops, 26 000 patterns. The graph is built in two steps with a 14 GB
  heap (10 min), and runs with 12 GB. 488 generated cases, a third of them across the country, and
  the 51 cases of the OTP speed test (`test/performance/norway`) that need no rental data. Most
  stops of a national graph have a few departures a day, so every second stop of a case is picked
  by its number of patterns (`busyStops`); a third of the results are still empty.

## Notes

- A full Maven compile of `application` takes 4-10 min on this laptop. `build --fast` compiles only
  changed files; use it while iterating, and a full `build` before trusting a result (Dagger and
  changed signatures need the full compile).
- Timeouts are raised in the dataset router-config, so results never depend on machine speed.
- The ParallelRouting feature is not supported (the Raptor capture is per thread).
- `bench --ops` counts OTP, `java.util`, `java.time`, JTS, HPPC, Guava and graphql-java, not
  `java.lang`. Methods the JIT compiler replaces with its own code are not counted, and code it
  removes (an assert, a trivial getter) counts as if it ran. The instrumented code runs 1.7 times
  slower; its timings mean nothing.
- Time on this laptop (2 fast and 10 slow cores), measured with the same code as A and B:
  - On all cores, one fork of four ran its street searches 60 % slower than the others.
  - On the fast cores the forks still differed by 2 % (standard deviation of 10 forks), whatever
    the number of iterations: the JIT compiler does not compile the same way in each run. With
    `-Xbatch` it does more so: Raptor 0.8 %, street searches 1-2.5 %.
  - With a 95 % interval, 3 forks of each gave "B faster 2.6 %" for the street searches. An
    experiment series makes many comparisons, so the interval is 99 %.
  - So 3 forks of each build show a Raptor difference from about 3 %, 5 forks from 2 %, and the
    street searches need two to three times that. The allocated bytes usually differ by 1 % or
    less (once 7 % for the street searches).
- Thread CPU time on Windows has 15.6 ms ticks, so the reports use wall time.
- `mutate` with 40 bugs in 16 core files (Raptor, A*, street edges, linking, transfer optimization,
  itinerary mapping), 2026-11-27: `check --quick` found 26. Of the others, 6 can not change a
  result, 4 are in features the data does not have (frequency-based trips, taxi, rental zones, a
  transfer without edges), and 3 led to the check of the edges of the graph, the `area` cases and
  the bicycle triangle cases.
- Under concurrency, OTP may order equal-cost results differently (other requests link temporary
  edges to the same vertices); the concurrent check ignores only the order of access/egress.
