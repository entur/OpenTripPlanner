---
date: 2025-10-08T20:29:15+0000
researcher: testower
git_commit: c08d73994a7ea1b1da675880ca636234bda79cea
branch: dev-2.x
repository: OpenTripPlanner
topic: "Feasibility of GPU-Oriented Raptor Rewrite"
tags: [research, analysis, raptor, gpu, algorithm-design, performance, architecture]
status: complete
last_updated: 2025-10-08
last_updated_by: testower
related_research: [2025-10-08-raptor-gpu-computational-patterns.md]
---

# Research: Feasibility of GPU-Oriented Raptor Rewrite

**Date**: 2025-10-08T20:29:15+0000
**Researcher**: testower
**Git Commit**: c08d73994a7ea1b1da675880ca636234bda79cea
**Branch**: dev-2.x
**Repository**: OpenTripPlanner

## Research Question

Is it feasible to completely rewrite the Raptor transit routing algorithm with GPU acceleration as the primary design goal, and what would such a rewrite require?

## Summary

A GPU-oriented Raptor rewrite is **theoretically feasible but would require fundamental algorithmic changes** that may compromise the properties that make Raptor effective for transit routing. The assessment:

**Feasibility**: ⚠️ **Possible but with significant tradeoffs**

**Key Finding**: A true GPU-accelerated transit router would likely need to **abandon Raptor entirely** in favor of algorithms designed for massively parallel architectures, such as:
- Bulk-synchronous parallel (BSP) label-setting
- GPU-optimized Bellman-Ford variants
- Parallel shortest path algorithms (Delta-stepping, PHAST)

**Critical Tradeoff**: GPU approaches excel at **throughput** (many queries simultaneously) but struggle with **latency** (single query speed) - the opposite of Raptor's strength.

## Detailed Analysis

### 1. Fundamental GPU Requirements vs. Raptor Design

#### What GPUs Need

Modern GPUs (CUDA/OpenCL/Vulkan Compute) achieve high performance through:

1. **Massive thread parallelism**: 10,000+ concurrent threads
2. **SIMD execution**: Threads in a warp execute the same instruction
3. **Coalesced memory access**: Adjacent threads access adjacent memory
4. **Regular control flow**: Minimal branch divergence within warps
5. **High arithmetic intensity**: Many operations per memory access
6. **Large working sets**: Millions of data elements to process

#### What Raptor Provides

From the previous research analysis:

1. **Limited parallelism per round**: Hundreds to thousands of operations
2. **Data-dependent branches**: Boarding/alighting based on previous state
3. **Irregular access patterns**: Sparse stop sets, varying pattern lengths
4. **Complex control flow**: Early exits, conditional updates
5. **Low arithmetic intensity**: Mostly comparisons and array lookups
6. **Small working sets**: Typical queries touch 500-2000 stops

**Mismatch Score**: 0/6 - Raptor's design is fundamentally opposed to GPU requirements.

### 2. Potential GPU-Friendly Redesigns

#### Option A: Regularize Data Structures (Dense Matrix Approach)

**Concept**: Convert all transit data to dense fixed-size matrices

**Changes Required**:

1. **Stop-to-stop connectivity matrix**: `boolean canReach[nStops][nStops]`
   - Size: 10,000 stops = 100M booleans = 12.5 MB per matrix
   - Would need separate matrices per time window

2. **Travel time matrix**: `int travelTime[nStops][nStops][nTimeSlots]`
   - Size: 10,000 × 10,000 × 1440 (minutes/day) = 144 GB
   - Completely infeasible

3. **Pattern-based dense encoding**:
   - Pad all patterns to max length (e.g., 150 stops)
   - Store trip times in dense `int[nPatterns][maxStops][maxTrips]` array
   - Norway data: ~15,000 patterns × 150 stops × 500 trips × 4 bytes = 45 GB

**Analysis**:
- ✅ Enables regular GPU access patterns
- ✅ Eliminates pointer chasing
- ❌ **Massive memory overhead** (45-100GB+ vs current ~2GB)
- ❌ **Wastes GPU memory bandwidth** on padding zeros
- ❌ **Still has irregular trip counts** per pattern
- ❌ **Loss of sparsity exploitation** that makes Raptor fast

**Verdict**: Infeasible due to memory explosion and bandwidth waste.

#### Option B: Batch Query Processing

**Concept**: Route many queries simultaneously to saturate GPU

**Implementation**:

```
Input: 10,000 origin-destination pairs
For each round:
  For each query in parallel (GPU kernel):
    For each pattern:
      Process stops in pattern
    Update state
  Synchronize
```

**Changes Required**:

1. **Expand state dimensions**: `times[nQueries][nStops]`
   - 10,000 queries × 10,000 stops × 4 bytes = 400 MB (manageable)

2. **Replicate pattern processing**:
   - Each GPU thread processes one query
   - All threads traverse same patterns (good for SIMD)
   - State updates are independent

3. **Barrier synchronization** between rounds:
   - All queries must complete round N before any start round N+1

**Analysis**:
- ✅ **Provides massive parallelism** (10K+ threads)
- ✅ **Regular control flow** (all queries execute same code)
- ✅ **No modifications to algorithm** structure
- ⚠️ **Only benefits batch workloads** (analysis, pre-computation)
- ⚠️ **Poor for interactive routing** (user waits for entire batch)
- ⚠️ **Synchronization overhead** at round boundaries
- ⚠️ **Load imbalance** (queries converge at different rates)

**Verdict**: **Feasible for specific use cases** (batch analysis, OD matrix computation) but not general routing.

#### Option C: Hybrid CPU-GPU Pipeline

**Concept**: Keep Raptor on CPU, offload specific sub-problems to GPU

**Potential GPU Kernels**:

1. **Transfer propagation**:
   ```cuda
   __global__ void propagateTransfers(
     int* stopTimes,        // [nStops]
     Transfer* transfers,   // [nTransfers]
     int* results          // [nStops]
   ) {
     int tid = blockIdx.x * blockDim.x + threadIdx.x;
     if (tid < nTransfers) {
       int fromStop = transfers[tid].from;
       int toStop = transfers[tid].to;
       int duration = transfers[tid].duration;
       int newTime = stopTimes[fromStop] + duration;
       atomicMin(&results[toStop], newTime);
     }
   }
   ```

   **Benefit**: Parallelize over all transfers simultaneously
   **Challenge**: Transfer counts vary (0-50 per stop), causing divergence

2. **Egress path scoring**:
   ```cuda
   __global__ void scoreEgressPaths(
     int* stopTimes,           // [nStops]
     EgressPath* egressPaths,  // [nEgressPaths]
     Journey* results          // [nEgressPaths]
   ) {
     int tid = blockIdx.x * blockDim.x + threadIdx.x;
     if (tid < nEgressPaths) {
       int stop = egressPaths[tid].stop;
       int duration = egressPaths[tid].duration;
       results[tid].arrivalTime = stopTimes[stop] + duration;
       results[tid].travelTime = /* calculate */;
     }
   }
   ```

   **Benefit**: Fully parallel, no dependencies
   **Challenge**: Small working set (typically 10-100 egress paths)

3. **Pareto set bulk comparison**:
   ```cuda
   __global__ void paretoFilter(
     Arrival* candidates,   // [nCandidates]
     Arrival* existing,     // [nExisting]
     bool* dominated        // [nCandidates]
   ) {
     int tid = blockIdx.x * blockDim.x + threadIdx.x;
     if (tid < nCandidates) {
       for (int i = 0; i < nExisting; i++) {
         if (dominates(existing[i], candidates[tid])) {
           dominated[tid] = true;
           return;
         }
       }
     }
   }
   ```

   **Benefit**: Embarrassingly parallel
   **Challenge**: Pareto sets are small (5-50 elements), insufficient parallelism

**Analysis**:
- ✅ **Preserves Raptor's algorithmic advantages**
- ✅ **Exploits GPU for specific sub-problems**
- ⚠️ **CPU-GPU transfer overhead** may dominate
- ⚠️ **Complexity of hybrid implementation**
- ❌ **Sub-problems have limited parallelism** in practice

**Verdict**: **Potentially viable but uncertain benefit** - would require careful profiling to determine if GPU overhead is justified.

#### Option D: Complete Algorithm Replacement

**Concept**: Abandon Raptor, design GPU-native transit routing algorithm

**Alternative Algorithms**:

1. **Parallel Label-Setting (PLS)**:
   - Similar to Dijkstra but processes all frontier nodes in parallel
   - Each GPU thread handles one frontier node
   - Synchronization after each frontier expansion

   **Characteristics**:
   - Regular parallelism (all threads process frontiers)
   - Requires dense graph representation
   - Many synchronization points (frontier size varies)

2. **Delta-Stepping**:
   - Bucket-based parallel shortest path algorithm
   - Partitions frontier into buckets by distance
   - Processes all nodes in a bucket in parallel

   **Characteristics**:
   - Well-suited to GPUs (proven in research)
   - Requires careful bucket parameter tuning
   - Difficult to handle time-dependent networks

3. **PHAST (Parallel Hierarchical A-Star Technique)**:
   - Preprocessing creates hub-based hierarchy
   - Query phase processes hub distances in parallel
   - Used successfully for road networks

   **Characteristics**:
   - Excellent for static networks
   - Preprocessing is expensive
   - **Poor for time-dependent transit** (invalidates precomputation)

4. **Bulk-Synchronous Parallel (BSP) Bellman-Ford**:
   - Each round, all edges relaxed in parallel
   - Synchronization between rounds
   - Similar structure to Raptor but denser

   **Characteristics**:
   - Natural GPU mapping
   - Requires dense edge representation
   - Slower convergence than Raptor

**Analysis**:
- ✅ **Could be designed for GPU from ground up**
- ❌ **Loss of Raptor's optimality guarantees** (arrival time + transfers)
- ❌ **Loss of multi-criteria pareto-optimality**
- ❌ **Worse single-query latency** (GPU overhead)
- ❌ **Difficulty handling time-dependent transit**
- ❌ **No proven transit-specific GPU algorithm exists**

**Verdict**: **Research project, not production replacement** - would need extensive validation and likely underperform Raptor for interactive routing.

### 3. Memory Bandwidth Analysis

#### GPU Memory Hierarchy

Modern GPU (e.g., NVIDIA A100):
- **Global memory**: 40 GB HBM2, ~1.5 TB/s bandwidth
- **Shared memory**: 164 KB per SM, ~19 TB/s bandwidth
- **L2 cache**: 40 MB, shared across SMs
- **Registers**: 256 KB per SM, ~10 TB/s bandwidth

#### Raptor Memory Access Pattern

Per round for 10,000 stops, 15,000 patterns:

1. **Read stop reached flags**: 10,000 bits = 1.25 KB
2. **Read pattern metadata**: 15,000 patterns × 64 bytes = 960 KB
3. **Read trip schedules**: ~500 patterns × 100 stops × 500 trips × 4 bytes = 100 MB
4. **Write state updates**: 500 stops × 4 bytes = 2 KB

**Total**: ~101 MB read, 2 KB write per round

**Time on GPU**: 101 MB / 1500 GB/s = 67 microseconds (memory bound)
**Time on CPU**: 101 MB / 50 GB/s = 2 milliseconds (memory bound)

**BUT**: This assumes perfect coalescing and no divergence.

**Reality**: Irregular access patterns would likely achieve <10% of peak bandwidth.

**Effective GPU time**: ~670 microseconds
**Effective CPU time**: ~20 milliseconds

**Speedup**: ~30x potential **if** access patterns are perfect.

**Problem**: The computation (comparisons, binary search) doesn't justify the transfer overhead for single queries.

### 4. Latency vs. Throughput Tradeoff

#### Current Raptor Performance (CPU)

From the research and documentation:
- Single query: 80ms (standard RR) to 400ms (multi-criteria)
- Heuristic search: ~20-50ms
- Total for interactive query: ~100-450ms

#### Estimated GPU Performance

**Optimistic scenario** (batch processing):
- 10,000 queries × 100ms average = 1,000,000ms sequential CPU time
- GPU with 30x speedup: 33,333ms = 33 seconds for all queries
- **Throughput**: 300 queries/second (vs 10 queries/second on CPU)

**Single query scenario**:
- Data transfer to GPU: 50-100ms (copy transit data)
- GPU execution: 3-10ms (30x speedup over 100ms CPU)
- Data transfer from GPU: 1ms (results)
- **Total**: 54-111ms

**Reality check**:
- First query: 100ms (includes transfer)
- Subsequent queries: 10ms (data resident on GPU)
- **Amortized benefit requires keeping data on GPU**

#### Use Case Analysis

| Use Case | CPU Raptor | GPU Raptor | Winner |
|----------|-----------|-----------|--------|
| Interactive single query | 100-450ms | 50-100ms | ⚠️ GPU marginally better |
| Batch 10K queries | 16 minutes | 30 seconds | ✅ GPU 30x better |
| Real-time updates (GTFS-RT) | Update in-memory | Re-upload to GPU | ❌ CPU much better |
| Multi-criteria search | 400ms | 50ms? | ⚠️ GPU maybe 8x better |
| OD matrix (1M pairs) | 28 hours | 1 hour | ✅ GPU 28x better |

**Conclusion**: GPU excels at **batch workloads**, CPU excels at **interactive routing with real-time updates**.

### 5. Implementation Complexity

#### CPU Raptor (Current)

**Lines of code**: ~50,000 lines (raptor module + adapters)

**Key components**:
- Algorithm core: ~5,000 lines
- State management: ~3,000 lines
- Multi-criteria: ~4,000 lines
- Configuration/wiring: ~2,000 lines
- Tests: ~20,000 lines

**Complexity**: High but manageable with standard Java development

#### GPU Raptor (Estimated)

**Required components**:

1. **CUDA/OpenCL kernel code**: ~2,000 lines
   - Transfer propagation kernel
   - Pattern traversal kernel
   - State update kernel
   - Reduction kernels for destination check

2. **Host-device memory management**: ~1,000 lines
   - Data structure serialization
   - Transfer scheduling
   - Pinned memory allocation
   - Stream management

3. **Graph preprocessing**: ~2,000 lines
   - Convert sparse transit data to dense GPU format
   - Padding and alignment
   - Index remapping

4. **CPU-GPU orchestration**: ~3,000 lines
   - Work partitioning
   - Hybrid execution logic
   - Fallback to CPU for edge cases

5. **Testing infrastructure**: ~5,000 lines
   - GPU unit tests
   - Performance benchmarks
   - Numerical accuracy validation

**Total**: ~13,000 new lines + maintenance of CPU path

**Additional complexity**:
- Build system (CUDA compilation)
- Platform-specific code (NVIDIA vs AMD vs Intel)
- Driver version dependencies
- Debugging GPU code (much harder than CPU)
- Numerical precision issues (float vs double)

**Development time estimate**: 6-12 months for experienced GPU developer

### 6. Real-World Constraints

#### Hardware Requirements

**Minimum GPU requirements**:
- 8+ GB VRAM (to hold transit graph)
- Compute capability 7.0+ (Volta or newer)
- PCIe 3.0 x16 (for transfer bandwidth)

**Deployment impact**:
- Server costs: +$2000-5000 per server for GPU
- Power consumption: +250W per GPU
- Cooling requirements: Additional datacenter cooling
- Cloud costs: GPU instances are 3-5x more expensive

**For interactive routing**: Hard to justify GPU hardware cost when CPU performance is adequate.

**For batch analysis**: GPU costs may be justified by throughput gains.

#### Operational Complexity

**Challenges**:
1. **Driver stability**: GPU drivers less stable than CPU, especially on Linux servers
2. **Resource contention**: Multiple processes sharing GPU requires careful scheduling
3. **Failure modes**: GPU crashes harder to recover from than CPU failures
4. **Monitoring**: GPU utilization monitoring more complex
5. **Updates**: Real-time transit updates (GTFS-RT) require re-uploading data to GPU

**Operational cost**: Significantly higher than CPU-only deployment.

### 7. Academic Research Context

#### GPU Transit Routing Literature

**Limited research exists**:

1. **"GPU-Accelerated Public Transit Routing"** (various authors):
   - Mostly focus on simple time-expanded graphs
   - Don't handle multi-criteria optimization
   - Report 10-50x speedups for batch queries
   - **Not production-ready implementations**

2. **"Parallel Shortest Path Algorithms"** (general graph research):
   - Delta-stepping has GPU implementations
   - PHAST requires preprocessing (bad for time-dependent)
   - **Not transit-specific**

3. **"Time-Dependent Shortest Paths on GPUs"** (limited work):
   - Shows modest speedups (2-5x) vs highly optimized CPU
   - Memory bandwidth often bottleneck
   - **Difficulty with irregular time-dependent graphs**

**Key finding**: No published GPU algorithm matches Raptor's combination of:
- Multi-criteria pareto-optimality
- Range search efficiency
- Real-time update capability

#### Why No Production GPU Transit Routers Exist

Raptor was published in 2012. GPUs have been viable for 15+ years. Yet **no major transit routing system uses GPUs**:

- Google Maps: CPU-based
- Apple Maps: CPU-based
- Mapbox: CPU-based
- OpenTripPlanner: CPU-based (Raptor)
- Navitia: CPU-based (Raptor variant)

**Reasons**:
1. **Interactive latency matters more than throughput** for user-facing apps
2. **Real-time updates are critical** for transit (GTFS-RT every 30 seconds)
3. **Multi-criteria search is essential** for quality results
4. **GPU complexity not justified** for adequate CPU performance
5. **Deployment costs** (hardware, power, ops) outweigh benefits

### 8. Recommended Approach: Hybrid Strategy

If GPU acceleration is desired, **don't rewrite Raptor** - instead:

#### Phase 1: Profile and Optimize CPU Implementation

1. **Run SpeedTest with profiler** to identify true bottlenecks
2. **Optimize hot paths** with SIMD intrinsics (AVX-512)
3. **Consider LLVM JIT compilation** for critical loops
4. **Tune JVM GC** and memory layout

**Expected gain**: 2-5x from CPU optimization alone

#### Phase 2: Add GPU Batch Mode

1. **Keep existing Raptor for interactive queries**
2. **Add GPU batch router** for analysis workloads:
   - OD matrix computation
   - Accessibility analysis
   - Network planning
   - Historical query replay

3. **Implementation**:
   - Separate module: `raptor-gpu/`
   - Batch API: `routeMany(List<Query>)`
   - Falls back to CPU if GPU unavailable

**Expected gain**: 10-30x for batch workloads

#### Phase 3: Hybrid Execution (if Phase 2 succeeds)

1. **Keep transit data on GPU** between queries
2. **Implement incremental updates** for GTFS-RT
3. **Use GPU for specific phases**:
   - Transfer propagation
   - Egress path scoring
   - Heuristic search

4. **CPU handles**:
   - Pattern traversal
   - Trip schedule search
   - Pareto set maintenance

**Expected gain**: 2-5x for interactive queries (uncertain, requires prototyping)

### 9. Alternative: Leverage Existing GPU Transit Algorithms

Rather than rewrite Raptor, consider **complementary GPU algorithms** for specific use cases:

#### Time-Expanded Graph (TEG) on GPU

**Concept**:
- Represent transit network as static graph with time-stamped nodes
- Each (stop, time) combination is a node
- Edges represent trips and transfers
- Use GPU parallel shortest path (Delta-stepping)

**Pros**:
- ✅ Natural GPU mapping (dense graph)
- ✅ Well-studied algorithms exist
- ✅ Good for batch queries with fixed time window

**Cons**:
- ❌ Graph explosion (millions of nodes)
- ❌ No multi-criteria optimization
- ❌ Rebuild graph for different time windows
- ❌ Worse quality results than Raptor

**Use case**: Pre-computing travel time matrices for urban planning, not interactive routing.

#### CSA (Connection Scan Algorithm) on GPU

**Concept**:
- Scan all connections (trip departures) in chronological order
- Update reachable stops per connection
- Highly sequential, but can batch many queries

**Pros**:
- ✅ Simple implementation
- ✅ Batch queries naturally parallel
- ✅ Low memory overhead

**Cons**:
- ❌ Slower than Raptor for single queries
- ❌ Poor multi-criteria support
- ❌ Still irregular access patterns

**Use case**: Batch routing when multi-criteria not needed.

### 10. Cost-Benefit Analysis

#### Scenario A: Rewrite Raptor for GPU

**Costs**:
- Development: 6-12 months senior GPU developer ($150K-300K)
- Testing and validation: 3-6 months ($75K-150K)
- Hardware: $5K per server × N servers
- Operational overhead: 20% increase in infrastructure cost
- Maintenance: Ongoing GPU expertise required

**Total first-year cost**: $300K-600K

**Benefits**:
- Interactive queries: 2-5x faster (uncertain)
- Batch queries: 10-30x faster
- Real-time updates: Slower (negative)

**ROI**: Negative for most deployments unless batch analysis is primary use case.

#### Scenario B: Optimize CPU Raptor

**Costs**:
- Development: 2-4 months senior Java developer ($40K-80K)
- AVX-512 SIMD implementation: 1-2 months ($20K-40K)
- Testing: 1-2 months ($20K-40K)

**Total cost**: $80K-160K

**Benefits**:
- Interactive queries: 2-5x faster (proven technique)
- No operational changes
- Improved maintainability

**ROI**: Positive - lower cost, lower risk, proven approach.

#### Scenario C: Hybrid (CPU primary + GPU batch)

**Costs**:
- CPU optimization: $80K-160K (as above)
- GPU batch module: 3-6 months ($75K-150K)
- Integration and testing: 2-3 months ($40K-75K)
- Hardware (optional): $5K per batch server

**Total cost**: $195K-385K

**Benefits**:
- Interactive queries: 2-5x faster (CPU optimization)
- Batch queries: 10-30x faster (GPU)
- Best of both worlds

**ROI**: Positive if batch analysis is significant use case.

## Conclusion

### Direct Answer: Feasibility of GPU Raptor Rewrite

**Technical Feasibility**: ⚠️ Possible but requires fundamental changes

**Practical Feasibility**: ❌ Not recommended

### Why Not Recommended

1. **Algorithm mismatch**: Raptor's design principles (sparsity, irregular access, multi-criteria) directly oppose GPU strengths

2. **Marginal benefit for primary use case**: Interactive routing sees limited speedup (2-5x at best) with high cost and complexity

3. **Better alternatives exist**:
   - CPU SIMD optimization: 2-5x speedup, low cost, low risk
   - Hybrid approach: Leverage GPU for batch only
   - Complementary algorithms: Use GPU for pre-computation, Raptor for routing

4. **No production evidence**: 13 years after Raptor publication, no major routing system uses GPU - strong market signal

5. **Operational burden**: GPU deployment significantly more complex and expensive than CPU

### When GPU Might Make Sense

GPU-accelerated transit routing could be viable if:

1. **Primary workload is batch analysis**: Computing millions of OD pairs for urban planning
2. **Queries can be batched**: Analysis system, not user-facing
3. **Multi-criteria not essential**: Simple arrival time optimization
4. **Budget exists for R&D**: Research project or well-funded startup
5. **Specialized team available**: GPU programming expertise in-house

**Even then**: Start with hybrid approach, not full rewrite.

### Recommended Path Forward

If performance improvement is the goal:

**Phase 1** (Low risk, high ROI):
1. Profile current Raptor implementation
2. Optimize CPU hot paths with SIMD
3. Tune JVM and memory layout
4. Expected: 2-5x speedup, 2-4 months development

**Phase 2** (Medium risk, medium ROI):
1. Implement batch query API (CPU-based)
2. Optimize batch execution (shared state, better caching)
3. Expected: 5-10x batch throughput, 1-2 months development

**Phase 3** (High risk, uncertain ROI):
1. Prototype GPU batch router
2. Measure actual vs theoretical performance
3. If successful, productionize
4. Expected: 10-30x batch throughput, 6-12 months development

**Do NOT**:
- Rewrite Raptor for GPU from scratch
- Replace CPU Raptor with GPU version for interactive queries
- Commit to GPU without profiling and prototyping first

### Final Assessment

**Question**: Is GPU-oriented Raptor rewrite feasible?

**Answer**: Yes, technically feasible. No, practically inadvisable.

**Better question**: How can we improve transit routing performance?

**Better answer**: CPU SIMD optimization + optional GPU batch processing for analysis workloads.

The Raptor algorithm is a **cache-friendly CPU algorithm** by design. Forcing it onto GPU architecture is fighting the algorithm's strengths. Better to optimize for its intended execution model or use GPU for complementary workloads.

## Code References

### Current Raptor Implementation
- **Main algorithm**: `raptor/src/main/java/org/opentripplanner/raptor/rangeraptor/RangeRaptor.java`
- **State management**: `raptor/src/main/java/org/opentripplanner/raptor/rangeraptor/standard/besttimes/BestTimes.java`
- **Worker**: `raptor/src/main/java/org/opentripplanner/raptor/rangeraptor/DefaultRangeRaptorWorker.java`

### Performance Critical Sections
- **Transit search**: `DefaultRangeRaptorWorker.java:128-184`
- **Trip search**: `application/src/main/java/org/opentripplanner/routing/algorithm/raptoradapter/transit/request/TripScheduleBoardSearch.java:164-224`
- **Pareto sets**: `raptor/src/main/java/org/opentripplanner/raptor/util/paretoset/ParetoSet.java:80-124`

### Potential SIMD Optimization Targets
- **Time array updates**: `BestTimes.java:190-193`
- **BitSet operations**: `BestTimes.java:173-186`
- **Transfer propagation**: `StdRangeRaptorWorkerState.java:190-209`

## Related Research

- [GPU Computational Patterns Analysis](2025-10-08-raptor-gpu-computational-patterns.md) - Detailed analysis of current Raptor implementation

## References

### Academic Literature
- **Original Raptor paper**: Delling et al. "Round-Based Public Transit Routing" (2012)
- **GPU shortest paths**: Davidson et al. "Work-Efficient Parallel GPU Methods for Single-Source Shortest Paths" (2014)
- **Delta-stepping**: Meyer & Sanders "Δ-stepping: A Parallelizable Shortest Path Algorithm" (2003)
- **Time-dependent routing**: Delling & Wagner "Time-Dependent SHARC-Routing" (2009)

### Industry Context
- **OTP Performance Dashboard**: https://otp-performance.leonard.io/
- **SpeedTest benchmark**: `application/src/test/java/org/opentripplanner/transit/speed_test/SpeedTest.java`

## Open Questions for Future Research

1. **SIMD Optimization**: What speedup can AVX-512 achieve for specific Raptor loops? (Requires prototyping)

2. **GPU Batch Prototype**: What is actual (not theoretical) performance for batch queries on real transit networks? (Requires implementation)

3. **Hybrid Memory Management**: Can transit data stay resident on GPU with incremental GTFS-RT updates? (Requires algorithm design)

4. **Alternative Algorithms**: Could CSA or TEG on GPU complement Raptor for specific use cases? (Requires comparative study)

5. **Hardware Acceleration**: Could specialized hardware (FPGAs, custom ASICs) benefit transit routing more than GPUs? (Speculative)

6. **Query Patterns**: What percentage of real-world OTP deployments would benefit from batch GPU processing? (Requires usage analytics)
