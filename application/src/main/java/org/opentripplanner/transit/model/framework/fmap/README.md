

# Fast collections

## Domain Overview

This package contains fast collections.

The fast collections are used in the OTP Repositories. Then common senarie is this:

1. The graph is built using scheduled data. This is the majority of the data. The graph is build 
   in stages and frozen after the all build steps/modules are done. This is called the scheduled
   graph.
2. When the server starts up, we will keep this scheduled graph and perform queries and do routing 
   on it.
3. We will also start adding new data to the graph as real-time data. The real-time data usually 
   modify < 10% of the data, but never all of it and allways in small increments. The data can be
   grouped in to feeds, and one updater will in most cases only modify data within a feed. After an
   updater has added a small set of updates, the graph is frozen, and a snapshot is taken.
4. The snapshot is then used for routing and queries, while the updater continues to add more
   updates to the graph. The snapshot is immutable and can be used by multiple threads at the same
   time. The updater will continue to add new data to the graph, and when it has added a new set of
   updates it will freeze the graph and take a new snapshot.


## Data scale reference (Norway dataset)

To ground the size estimates used below in real numbers, here are entity counts from a full national
Norwegian NeTEx export available locally at `/Users/thomasgran/code/entur/otp/data/otp/norway`
(not checked into this repo). It is loaded as ~5 000 per-line NeTEx files plus a national stop registry
(`_stops.xml`, 403 MB), per the `netexDefaults` block in that dataset's `build-config.json`.

These are raw NeTEx source-element counts, obtained directly from the XML files with `grep -oE` (not by
loading the built OTP graph), so they are pre-deduplication/pre-graph-build and should be read as upper
bounds on the eventual `EntityMap`/`IndexMap` sizes:

| NeTEx element            | OTP equivalent (roughly)            | Count       |
|--------------------------|-------------------------------------|-------------|
| `Quay`                   | `RegularStop`                       | 101 781     |
| `StopPlace`              | `Station`                           | 57 977      |
| `FlexibleStopPlace`      | flex `AreaStop`                     | 625         |
| `ScheduledStopPoint`     | (intermediate; resolved to a stop)  | 116 433     |
| `Line` / `FlexibleLine`  | `Route`                             | 4 421 / 535 |
| `Route` (NeTEx)          | (intermediate path within a `Line`) | 26 821      |
| `JourneyPattern`         | `TripPattern`                       | 32 850      |
| `ServiceJourney`         | `Trip`                              | 403 092     |
| `DatedServiceJourney`    | `TripOnServiceDate`                 | 2 293 067   |
| `TimetabledPassingTime`  | per-stop entry inside a `TripTimes` | 9 941 600   |
| `Operator` / `Authority` | `Operator` / `Agency`               | 293 / 89    |
| `DestinationDisplay`     | `DestinationDisplay`                | 15 728      |

Takeaways:
- Stops (`Quay`/`StopPlace`) and trips (`ServiceJourney`) land squarely inside the 10 000-500 000 range
  assumed in the "Snapshot / Mutable map" section below.
- `DatedServiceJourney` -> `TripOnServiceDate` is the outlier: **2.29 million** entities nationwide, well
  above the assumed upper bound. If `EntityMap` is used for this entity type, the design needs to scale
  to millions, not hundreds of thousands - this strengthens the case for strategies that avoid full-map
  copies on every snapshot (persistent trie, dense-array, MVCC) over the plain full-copy `HashMap`
  baseline.
- Small registries (`Operator`, `Authority`, `Network`, `FlexibleLine`) stay in the low hundreds - a
  plain `HashMap` is good enough for these regardless of strategy. The optimization effort should focus
  on stops, trip patterns, trips, and dated trips, where the volume actually matters.

### Real-time update volumes (Norway SIRI-ET dataset)

A real SIRI-ET (EstimatedTimetable) snapshot is available locally at
`/Users/thomasgran/code/entur/otp/data/realtime/norway/2026-06-24/et.xml` (223 MB, not checked into
this repo; the accompanying `sx.xml` - SituationExchange/disruptions - is out of scope here). It was
analyzed with a streaming XML parse (`xml.etree.ElementTree.iterparse`, clearing each
`EstimatedVehicleJourney` after reading it, to keep memory bounded over the 223 MB file) rather than
by loading it into OTP.

This file is a single current-state snapshot (one `ServiceDelivery`/`EstimatedTimetableDelivery`), not
a stream of discrete push messages, so there is no explicit "this is one update batch" boundary in the
data. As a proxy, each `EstimatedVehicleJourney` carries its own `RecordedAtTime` - when its source
operator last recorded/updated that journey - so grouping journeys by `RecordedAtTime` rounded to the
minute approximates how many trips change together:

| Metric                                                        | Value                                                       |
|---------------------------------------------------------------|-------------------------------------------------------------|
| `EstimatedVehicleJourney` elements                            | 15 053                                                      |
| ...of which distinct `DatedVehicleJourneyRef`                 | 13 665 (some trips carry more than one recorded version)    |
| `EstimatedCall` elements (stop-level updates)                 | 327 750 (avg 21.8 / trip, max 219)                          |
| Distinct `LineRef` touched                                    | 1 237                                                       |
| Distinct `StopPointRef` touched                               | 38 393 (~38% of the 101 781 `Quay`s in the static registry) |
| Distinct `DataSource` (operator/feed) codes                   | 22 (out of ~89 `Authority` codes in the static registry)    |
| Whole-trip cancellations (`Cancellation=true` on the journey) | 102 (0.7%)                                                  |
| Extra/unplanned trips (`ExtraJourney=true`)                   | 39 (0.26%)                                                  |
| Individual stop-skips (`Cancellation=true` on a call)         | 2 267 of 327 750 calls (0.7%)                               |
| Non-monitored journeys (`Monitored=false`)                    | 53 (0.35%)                                                  |

Update cadence, from grouping journeys into 933 distinct `RecordedAtTime`-by-minute buckets (almost
all within a single day):

| Journeys recorded per minute, system-wide |                                         |
|-------------------------------------------|-----------------------------------------|
| Median                                    | 2                                       |
| Mean                                      | 16.1 (pulled up by bursts)              |
| 90th percentile                           | ~40                                     |
| 99th percentile                           | ~117                                    |
| Max (single busiest minute)               | 1 332 (likely one operator's bulk push) |

Takeaways:
- The update volume per minute is small relative to the dataset's total size - a median of 2 and a
  mean of ~16 trips/minute, against 403 092 `ServiceJourney`/`Trip` and 101 781 `Quay`/`RegularStop`
  nationwide - which matches the README's "only a few entities are modified for each update" assumption.
- Updates are concentrated in a handful of feeds rather than evenly spread: only 22 of ~89 operator
  codes (`DataSource`) appear at all, and the top 3 (`SKY`, `RUT`, `AKT`) account for ~58% of all
  journeys in the snapshot.
- The busiest single minute (1 332 journeys) is still tiny compared to the 2.29 million
  `TripOnServiceDate` / 403 092 `Trip` totals - even a large burst update is several orders of magnitude
  smaller than the full map, reinforcing that snapshot cost should be dominated by map size (favoring
  the non-full-copy strategies), not by update-batch size.
- Whole-trip cancellations, extra trips, non-monitored flags, and stop-skips are all sub-1% - rare
  enough that a `BitSet`-per-feature index (see the dense-array strategy below) would stay extremely
  sparse for these flags.

## Collections needed

### Entity Map

An entity map is a map which is a specific type of entities extending the TransitEntity class. 

### Index Map

There are two types of index maps:
1. A map from id (entity of type A) to another entity of type B. This represents a one-to-one relationship.
2. A map from id (entity of type A) to a list of entities of type B. This represents a one-to-many relationship.


### General description

Both maps toggle between a snapshot and a mutable map. The EntityMap should be the master map, and
the index map should be updated based on the master map.


### Snapshot / Mutable map

There are multiple strategies we could use to implement the snapshot / mutable map. We want:
1. Very fast access to the data, close to what we get from the java.util.HashMap today.
2. Fast and memory-efficient transformation from the mutable map to the snapshot map. The maps could 
   contain from 10 000 to 500 000 entities. A typical senario here is that only a few entities are 
   modified for each update.
3. Efficient inserts and removes to the mutable map - this is probably not as important as 1 and 2.

We want to try out and compare several alternative implementations before settling on one. Candidates:

1. **Full-copy HashMap** (baseline, what OTP does today in `DefaultEntityById` / `TimetableSnapshot`).
   Mutable map is a plain `java.util.HashMap`. The snapshot is produced with `Map.copyOf()`.
    - Read: O(1), identical to `HashMap`.
    - Snapshot: O(n) - copies every entry, regardless of how many actually changed.
    - Simple, well understood, zero new dependencies.

2. **Persistent hash trie (HAMT / CHAMP)**. The map is an immutable tree with structural sharing
   (as used by Clojure's `PersistentHashMap` or Scala's `HashMap`). "Mutating" produces a new root
   that shares almost all nodes with the old one.
    - Read: O(log32 n), a small constant factor slower than `HashMap`.
    - Snapshot: O(log32 n) per changed entry, no full copy needed.
    - No copy-on-write needed for the snapshot at all - the old root is itself already a valid snapshot.
    - Would require either a new dependency or a hand-rolled implementation.

3. **Delta / overlay map**. The mutable map is a small overlay (added/changed/removed entries) sitting
   on top of an immutable base snapshot. Reads check the overlay first, then fall back to the base.
   When the overlay grows too large (or on a fixed schedule) it is compacted into a new base snapshot.
    - Read: O(1) but with an extra overlay lookup until the next compaction.
    - Snapshot: O(overlay size) - very cheap between compactions.
    - Compaction is still O(n), but happens rarely instead of on every update batch.

4. **Dense array-backed map**. Each entity is assigned a stable, dense `int` index (via an id -> index
   allocator); storage is a plain `Object[]` (or a primitive-friendly variant) addressed by that index.
    - Read: O(1), faster than `HashMap` in practice - array indexing, no hashing or bucket traversal.
    - Snapshot: `Arrays.copyOf()` (cheap arraycopy) or a copy-on-write array reference swap.
    - Needs an id -> dense-index allocator; index reuse/removal needs care (tombstones or compaction).

   **Why this is especially attractive: feature `BitSet`s.** A dense `int` index is not just a faster
   array offset - it is also a *bit position*. Once every entity has a stable index, any boolean
   property of the entity ("is wheelchair accessible", "is cancelled", "belongs to feed X", "mode is
   BUS", "has real-time data today", "is in the currently banned-routes set", ...) can be represented
   as a single `BitSet` of length `capacity`, where bit `i` says whether the entity at index `i` has
   that property.

   This is exactly the pattern Raptor's own SPI already uses internally, just not yet tied to the
   application-level transit model: `TripPatternForDates` carries `boardingPossible`/`alightingPossible`
   `BitSet`s indexed by stop position, and `DefaultTransitDataProviderFilter.filterAvailableStops()`
   builds/clones a `BitSet` over stop index to decide which stops are usable for a request. If
   `EntityMap` assigned the *same* dense index Raptor already uses for stops/patterns (or one that maps
   to it via a single array lookup), filtering could be pushed up into the application layer using the
   same trick, instead of being reinvented per call site.

   Consequences for the trip search:
    - **Filtering becomes word-at-a-time bitwise math.** A multi-criteria filter such as "wheelchair
      accessible AND not cancelled AND mode = BUS" is `a.and(b)` / `a.andNot(c)` over three `BitSet`s -
      O(capacity/64) machine words, not O(n) virtual predicate calls or hashmap probes. The result
      `BitSet` is then walked with `nextSetBit()`, so only entities that pass *all* filters are ever
      touched - candidates that fail any filter cost nothing beyond a few skipped words.
    - **Snapshotting a feature index is cheap and size-predictable.** `BitSet.clone()` (or a manual
      `long[]` copy) costs `capacity/64` words regardless of entity size - for 500 000 entities that is
      ~62 KB per feature, copied in microseconds. This is far cheaper than copying a `HashMap` of the
      same cardinality, and is independent of how large the entities themselves are.
    - **Incremental maintenance is O(1) per change.** When a single entity's feature value flips (e.g. a
      real-time update marks a trip cancelled), updating the index is a single `set(i)`/`clear(i)` at its
      stable position - no rehashing, no traversal - which matches the "only a few entities change per
      update" access pattern called out above.
    - **This is effectively a specialized `IndexMap`.** For boolean or small-enum-valued properties, a
      `BitSet` (or one `BitSet` per enum value) supersedes a `Map<V, List<Entity>>`-style `IndexMap`:
      same query ("give me all entities with property X"), far less memory and faster set algebra. The
      list-valued `IndexMap` described above is still needed for genuine one-to-many *entity* relations
      (e.g. route -> trips), but categorical/boolean attributes should go through bitsets instead.
    - **Open questions to settle during implementation:** whether index slots are ever reused after an
      entity is removed (simplest: never reuse, compact periodically instead - otherwise every derived
      `BitSet` must be recompacted too); whether to reuse Raptor's existing stop/pattern index directly
      or maintain a parallel one; and whether `java.util.BitSet` is sufficient or a compressed bitmap
      (e.g. Roaring) is worth a new dependency for very sparse features - to be answered by benchmarking,
      not assumed up front.

5. **Versioned / MVCC map**. Each entry carries a version (epoch). A snapshot is just "freeze the current
   epoch number" - no copying at all. Readers see the entry version visible at their epoch.
    - Read: O(1) plus a version check.
    - Snapshot: O(1) - the cheapest of all options.
    - Requires epoch-based reclamation to garbage-collect old versions once no live snapshot needs them;
      the most complex option to implement correctly.

We will implement a few of these as interchangeable strategies behind the same `EntityMap`/`IndexMap`
API, and benchmark them against each other (read throughput, snapshot latency under realistic update
sizes, and memory overhead) before picking a default.

All five strategies above have been implemented (`fmap/hashmap`, `fmap/trie`, `fmap/overlay`,
`fmap/densearray`, `fmap/mvcc`) and exercised with a hand-rolled benchmark (no JMH) plus a shared
snapshot-isolation contract test suite. Results below. Reproduce with the (test-only, not part of
the regular test suite) `EntityMapBenchmark` (timing) and `EntityMapMemoryBenchmark` (memory, run it
directly with `java -Xms<N> -Xmx<N>`, not through Maven, to keep the heap size fixed) classes in
`application/src/test/java/.../fmap/`.

#### Benchmark results: snapshot cost and read throughput

Methodology: build `N` entities, then run 20 update rounds that each touch ~0.1% of entries (mix of
replacing an existing entity and adding a brand-new one) and take a snapshot, then do 500 000 random
reads (~80% hits) against the final snapshot. At `N` = 2 000 000 - the scale that matters most, since
it is in `TripOnServiceDate`'s range (see "Data scale reference" above):

| Strategy                   | Build (ms) | Avg snapshot (ms) | Max snapshot (ms) | Read (ops/sec) |
|----------------------------|------------|-------------------|-------------------|----------------|
| HashMap (baseline)         | 94         | 432.2             | 925.2             | 4 441 413      |
| Persistent trie            | 609        | 0.0003            | 0.0028            | 6 322 501      |
| Overlay (threshold 10 000) | 71         | 101.6             | 879.8             | 3 881 956      |
| Dense array                | 97         | 1.3               | 17.2              | 6 630 362      |
| Versioned/MVCC             | 148        | 0.0003            | 0.0033            | 8 377 577      |

(Full results at N = 10 000 / 100 000 / 500 000 follow the same pattern; snapshot cost for the
baseline scales roughly linearly with N - 0.34 ms avg at 10k, 17.5 ms at 100k, 38.0 ms at 500k, 432 ms
at 2M - while the trie and MVCC stay at microseconds regardless of N.)

Takeaways:
- The baseline's snapshot cost is exactly the liability the "Data scale reference" section predicted:
  it scales linearly with map size, not update size, so at `TripOnServiceDate` scale a single snapshot
  can cost the better part of a second (worse under GC pressure - the max here is 925 ms).
- The persistent trie and MVCC strategies deliver the promised O(1) snapshot, independent of N - both
  sit at fractions of a millisecond even at 2 million entries, because they never copy anything on
  `snapshot()`.
- Dense array is the best non-structural-sharing option: still O(n) per snapshot, but a flat
  `Arrays.copyOf` is a far cheaper constant factor than rebuilding a hash table, landing ~2-3 orders of
  magnitude below the baseline.
- Overlay's *average* snapshot cost is much better than the baseline, but its *max* is just as bad
  (879.8 ms) - that's the compaction spike. It trades "always a bit slow" for "usually fast,
  occasionally as slow as the baseline," which only pays off if compactions are infrequent relative to
  how often snapshots are read.
- Read throughput differences across strategies are smaller (4.4M-8.4M ops/sec) than the snapshot-cost
  differences (sub-millisecond vs sub-second) - for this workload, snapshot cost is the dominant
  concern, not raw read speed.
- Build cost is the trie's weak point (609 ms at 2M vs 94-148 ms for the others) - it pays at insert
  time for the structural sharing it gives away for free at snapshot time.

#### Memory consumption

Rather than estimate this analytically, it was measured directly: each `(strategy, size)` combination
was run in its own JVM process with a fixed `-Xms == -Xmx` (a single shared, long-running process with
the default dynamically-sized heap - as the timing benchmark above uses - makes
`Runtime.totalMemory() - Runtime.freeMemory()` far too noisy to trust for memory deltas; pinning the
heap size removes that noise at the cost of one process launch per data point).

**Per-entry overhead.** Building `N` entities and holding the live map plus one snapshot, the total
bytes/entry breaks down into an *irreducible* part - the `FmapTestEntity` + `FeedScopedId` + its
backing `String`/`byte[]` objects, which every strategy must pay regardless of map structure (measured
at ~100 bytes/entry, by storing entities in a plain `ArrayList` with no map at all) - and the
*structural* part each map strategy adds on top:

| Strategy                   | Total bytes/entry | Structural-only bytes/entry |
|----------------------------|-------------------|-----------------------------|
| Overlay (threshold 10 000) | ~121-138          | ~19-38                      |
| Persistent trie            | ~135-143          | ~35-43                      |
| HashMap (baseline)         | ~152-170          | ~50-70                      |
| Versioned/MVCC             | ~160-173          | ~59-73                      |
| Dense array                | ~162-181          | ~61-81                      |

(Ranges are across N = 10k/100k/500k/2M; the structural number is the total minus the ~100 bytes/entry
irreducible cost above.) For a single retained snapshot, the strategies are within about 2x of each
other - the irreducible entity cost dominates more than the map structure does.

**The real differentiator: retaining multiple snapshots.** A single snapshot understates the practical
difference, because OTP's actual usage pattern keeps a snapshot alive per in-flight request while the
updater keeps moving forward. Simulating that - 500 000 entities, 20 update rounds (~0.1%
touched/round), retaining *every* round's snapshot simultaneously, instead of just the last one -
shows the structural-sharing strategies pulling far ahead:

| Strategy                   | 1 snapshot retained | 20 snapshots retained | Cost per extra retained snapshot            |
|----------------------------|---------------------|-----------------------|---------------------------------------------|
| HashMap (baseline)         | 95.7 MB             | 234.5 MB              | ~7.3 MB                                     |
| Dense array                | 101.1 MB            | 139.0 MB              | ~2.0 MB                                     |
| Persistent trie            | 85.5 MB             | 89.4 MB               | ~0.2 MB                                     |
| Versioned/MVCC             | 99.3 MB             | 100.7 MB              | ~0.07 MB                                    |
| Overlay (threshold 10 000) | 79.5 MB             | ~77-80 MB (flat)      | ~0 MB, while under the compaction threshold |

This is the strongest argument in the whole comparison: each additional snapshot the baseline keeps
alive costs roughly another full copy's worth of memory (~7.3 MB per 500k-entry snapshot here), while
the trie and MVCC strategies cost almost nothing per extra snapshot (~35-100x cheaper) because they
share structure with every snapshot that came before. Dense array sits in between - cheaper per byte
than the baseline's hash table, but still a full array copy every time. Overlay stays flat for as long
as it avoids compaction, then would jump by a full copy's cost the moment it compacts.

### Automatic update of the index map (deferred)

The IndexMap should be updated automatically by subscribing to events. A way to do this would be to
provide a function from A to the key and another function for the value. The when a new entity v is
updated in the master map, then an event with the new v and old v´ is sent to the index. The index
uses the functions to update the index.

This is deferred for now and will be designed once the `EntityMap` snapshot/mutable strategy is settled.

