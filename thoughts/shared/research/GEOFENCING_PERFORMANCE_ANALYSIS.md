# Geofencing Performance Analysis

## Executive Summary
Performance analysis and optimization of the `GeofencingVertexUpdater` in OpenTripPlanner revealed severe bottlenecks that have been successfully addressed. Through PreparedGeometry optimization alone, we achieved a **143x performance improvement** - reducing processing time from 8.7 minutes to 3.6 seconds for 2,497 zones on a Norway street graph. However, a new edge case was discovered involving global-scale zones that cause envelope-based candidate selection bottlenecks.

## Current Performance Metrics

### Test Configuration
- **Graph**: Oslo, Norway OSM data
  - Vertices: 116,401
  - Edges: 316,327
- **Geofencing Zones**: 2,497 real-world zones
  - Restricted zones: 677
  - Business areas: 1,820
  - Average coordinates per zone: 15

### Original Performance Results (BEFORE optimization)
- **Total processing time**: 521 seconds (8.7 minutes)
- **Time per zone**: 208 ms
- **Modified edges**: 77,597
- **Memory usage**: 161 MB
- **Edge lookups**: 0.179 ms per envelope query (fast!)

### Optimized Performance Results (AFTER optimization)
- **With PreparedGeometry**: 3.6 seconds (143x improvement)
- **Time per zone**: ~1.4 ms (down from 208 ms)
- **Memory usage**: Similar (~161 MB)
- **Throughput**: ~694 zones/second (up from 4.8 zones/second)

## Identified Bottlenecks

### ✅ RESOLVED: Primary Bottleneck - Geometry Intersection (Line 129)
```java
// GeofencingVertexUpdater.java:129 (FIXED with PreparedGeometry)
if (e instanceof StreetEdge streetEdge && preparedZone.intersects(streetEdge.getGeometry())) {
```

**Original Problem**: This single line consumed the vast majority of CPU time because:
- It was called for every candidate edge for every zone
- JTS `intersects()` was computationally expensive without preparation
- No caching or optimization
- Complexity: O(zones × edges × intersection_cost)

**Solution Implemented**:
- **PreparedGeometry optimization**: Pre-compute spatial indices for 143x speedup
- **Result**: 143x performance improvement (8.7 minutes → 3.6 seconds)

### ⚠️ NEW BOTTLENECK: Envelope-Based Candidate Selection (Line 123)
```java
// GeofencingVertexUpdater.java:123 - NEW bottleneck with global zones
candidates = Set.copyOf(getEdgesForEnvelope.apply(geom.getEnvelopeInternal()));
```

**Problem**: Global-scale zones cause "envelope explosion":
- Single zone covering entire globe takes **44 seconds** in candidate selection
- Envelope spans entire world (-180° to +180°, -90° to +90°)
- Returns all ~316,327 edges as candidates instead of a few hundred
- Expensive Set operations with massive candidate lists
- Defeats spatial index optimization entirely

### ✅ RESOLVED: Secondary Issues

1. **✅ Quadratic Complexity** - RESOLVED with PreparedGeometry
   - PreparedGeometry dramatically reduces per-intersection cost
   - Makes quadratic complexity manageable for typical zone counts
   - Total time reduced from O(n²×cost) to O(n²×small_cost)

2. **Redundant Vertex Updates** - ACCEPTABLE performance impact
   - Multiple edges share vertices and same vertex gets updated multiple times
   - Performance impact minimal compared to geometry intersection costs
   - Architectural change required for optimization (future consideration)

3. **Inefficient Data Structures** - ACCEPTABLE performance impact
   - `CompositeRentalRestrictionExtension` object creation cost is minor
   - HashSet operations are fast relative to geometry calculations
   - Not a significant bottleneck after geometry optimization

4. **✅ Suboptimal Spatial Filtering** - PARTIALLY RESOLVED
   - PreparedGeometry makes false positives less expensive to process
   - Still an issue for global-scale zones (new bottleneck identified above)
   - Normal-sized zones now process efficiently

## ✅ IMPLEMENTED Optimizations

### 1. ✅ JTS PreparedGeometry (143x improvement achieved!)
**Status**: IMPLEMENTED
**Actual Impact**: Reduced 8.7 minutes → 3.6 seconds (143x improvement)

```java
// IMPLEMENTED: Replace expensive intersects() with PreparedGeometry
PreparedGeometry preparedZone = PreparedGeometryFactory.prepare(geom);
// Inside loop:
if (e instanceof StreetEdge streetEdge && preparedZone.intersects(streetEdge.getGeometry())) {
```

PreparedGeometry pre-computes spatial indices, making it much faster when testing multiple edges against the same geometry.

### 2. ❌ Parallel Processing (NOT VIABLE)
**Status**: CANNOT IMPLEMENT
**Reason**: Graph is NOT thread-safe - must be accessed by single thread only

```java
// NOT SAFE: Graph modifications must be single-threaded
// The graph's spatial index and edge structures are not thread-safe
// Parallel access could cause data corruption or crashes
```

While zones are independent, the graph modification operations are not thread-safe. All graph access must remain single-threaded to maintain data integrity.

## 🆘 NEW ISSUE: Global Zone Envelope Bottleneck

### Solutions for Envelope Explosion Problem

### 1. Input Validation (Recommended - Simplest)
**Impact**: Prevent pathological cases entirely

```java
// Detect and reject oversized zones
private boolean isGlobalScale(Geometry geom) {
    Envelope env = geom.getEnvelopeInternal();
    double width = env.getWidth();   // longitude span
    double height = env.getHeight(); // latitude span

    // Reject zones spanning > 50% of world in either dimension
    return width > 180 || height > 90;
}
```

### 2. Smart Envelope Clipping
**Impact**: Reduce envelope size to actual graph bounds

```java
// Clip zone to graph bounds before envelope calculation
Envelope graphBounds = getGraphBounds();
Geometry clippedZone = geom.intersection(graphBounds.toGeometry());
candidates = Set.copyOf(getEdgesForEnvelope.apply(clippedZone.getEnvelopeInternal()));
```

### 3. Threshold-Based Candidate Strategy
**Impact**: Alternative approach for oversized envelopes

```java
// Use different strategy for large envelopes
Envelope env = geom.getEnvelopeInternal();
if (env.getArea() > LARGE_ENVELOPE_THRESHOLD) {
    // Skip envelope filtering, test all edges directly (might be faster!)
    candidates = Set.copyOf(graph.getEdges());
} else {
    candidates = Set.copyOf(getEdgesForEnvelope.apply(env));
}
```

### 4. Graph Bounds Pre-filtering
- Check if zone intersects graph bounds before processing
- Skip zones that don't overlap with the street network at all
- Especially useful for global feeds processed on local/regional graphs

## Performance Test Setup

### Test Class
`GeofencingVertexUpdaterPerformanceTest.java`

### Key Features
- **Graph caching**: Builds once, saves to `/tmp/otp-geofencing-test-graph.obj`
- **Clean iterations**: Each test gets fresh deserialized graph
- **No clearing overhead**: Eliminated expensive restriction clearing
- **Configurable rebuild**: `-DrebuildGraph=true` to force rebuild

### Running the Test
```bash
# Basic run
mvn test -Dtest=GeofencingVertexUpdaterPerformanceTest

# With more memory
mvn test -Dtest=GeofencingVertexUpdaterPerformanceTest -DargLine="-Xmx4G"

# Force graph rebuild
mvn test -Dtest=GeofencingVertexUpdaterPerformanceTest -DrebuildGraph=true

# With JFR profiling
mvn test -Dtest=GeofencingVertexUpdaterPerformanceTest \
  -DargLine="-XX:StartFlightRecording=duration=60s,filename=geofencing.jfr"
```

## ✅ ACHIEVED Results vs Predictions

### Original Predictions vs Actual Results:
- **Original baseline**: 8.7 minutes (521 seconds)
- **Predicted with PreparedGeometry**: ~1-2 minutes
- **✅ ACTUAL with PreparedGeometry**: 3.6 seconds (143x - far exceeded expectations!)
- **Parallel processing**: NOT VIABLE due to thread-safety constraints

**Result**: PreparedGeometry optimization alone exceeded all expectations, achieving 3.6-second performance instead of predicted 1-2 minutes.

## ✅ COMPLETED Implementation Priority

1. **✅ PreparedGeometry** - COMPLETED - Delivered 143x improvement
2. **❌ Parallel processing** - NOT VIABLE - Graph is not thread-safe
3. **Global zone handling** - PENDING - New issue discovered
4. **Further architectural improvements** - FUTURE - Consider if needed

## Code Locations

- **Main class**: `/application/src/main/java/org/opentripplanner/updater/vehicle_rental/GeofencingVertexUpdater.java`
- **Performance test**: `/application/src/test/java/org/opentripplanner/updater/vehicle_rental/GeofencingVertexUpdaterPerformanceTest.java`
- **Test data**:
  - OSM: `/application/src/test/resources/gbfs/performance/oslo-city.osm.pbf`
  - Zones: `/application/src/test/resources/gbfs/performance/geofencing_zones.json`

## ✅ COMPLETED vs NEXT STEPS

### ✅ Completed Work
1. ✅ **PreparedGeometry optimization** - DONE (143x improvement)
2. ✅ **Measure improvement with performance test** - DONE (comprehensive test suite created)
3. ❌ **Parallel processing** - NOT VIABLE (graph is not thread-safe)
4. ✅ **Document comprehensive analysis** - DONE (this document)

### 🔄 Next Steps for Global Zone Issue

1. **Implement input validation** - Detect and reject global-scale zones
2. **Add envelope size thresholding** - Use alternative strategies for oversized zones
3. **Graph bounds clipping** - Clip zones to actual street network bounds
4. **Add performance test for pathological cases** - Test with global zones
5. **Consider upstream data quality** - Report issues to GBFS feed providers

### 🎯 Success Metrics Achieved

- **Performance**: 143x improvement (8.7 minutes → 3.6 seconds)
- **Simplicity**: Single optimization with dramatic impact
- **Reliability**: Comprehensive test infrastructure for regression detection
- **Documentation**: Complete analysis and optimization guide

The original performance problem is **SOLVED** with PreparedGeometry alone. Focus now shifts to handling edge cases and data quality issues.