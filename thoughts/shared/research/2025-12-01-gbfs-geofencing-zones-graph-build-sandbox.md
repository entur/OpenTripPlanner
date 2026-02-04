---
date: 2025-12-01T12:00:00+01:00
researcher: Claude
git_commit: 783545c125e75f90790dc2770e1bb53613ffc2bd
branch: feature/propulsion-type-aware-bike-and-scooter-routing
repository: OpenTripPlanner
topic: "POC Sandbox Feature for GBFS Geofencing Zones at Graph Build Time"
tags: [research, codebase, sandbox, geofencing, gbfs, graph-builder, vehicle-rental]
status: complete
last_updated: 2025-12-01
last_updated_by: Claude
---

# Research: POC Sandbox Feature for GBFS Geofencing Zones at Graph Build Time

**Date**: 2025-12-01T12:00:00+01:00
**Researcher**: Claude
**Git Commit**: 783545c125e75f90790dc2770e1bb53613ffc2bd
**Branch**: feature/propulsion-type-aware-bike-and-scooter-routing
**Repository**: OpenTripPlanner

## Research Question

How can we create a POC sandbox feature for adding GBFS geofencing zones to the graph during graph build time, based on the existing real-time updater code in `application/src/main/java/org/opentripplanner/updater/vehicle_rental`?

## Summary

Creating a sandbox feature for loading GBFS geofencing zones at graph build time is feasible and would follow the established patterns for sandbox extensions. The key insight is that the **current implementation applies geofencing zones at runtime via the `VehicleRentalUpdater`**, but this logic can be adapted for graph build time by:

1. Creating a new sandbox feature with an `OTPFeature` flag (e.g., `GbfsGeofencingBuildTime`)
2. Reusing the existing `GbfsFeedLoaderAndMapper`, `GbfsGeofencingZoneMapper`, and `GeofencingVertexUpdater` classes
3. Creating a `GeofencingZoneGraphBuilder` that fetches GBFS data and applies zones during graph build
4. Storing zone data in a repository that persists with the graph serialization

## Detailed Findings

### Current Geofencing Zone Implementation (Runtime)

The current implementation works as follows:

1. **Data Loading**: `GbfsVehicleRentalDataSource` → `GbfsFeedLoaderAndMapper` → `GbfsFeedMapper` → `GbfsGeofencingZoneMapper`
   - Location: `application/src/main/java/org/opentripplanner/updater/vehicle_rental/datasources/gbfs/`

2. **Zone Application**: `VehicleRentalUpdater.VehicleRentalGraphWriterRunnable` creates a `GeofencingVertexUpdater` that applies `RentalRestrictionExtension` to intersecting street edges
   - Location: `application/src/main/java/org/opentripplanner/updater/vehicle_rental/VehicleRentalUpdater.java:227-245`

3. **Routing Effect**: `StreetEdge.traverse()` checks `tov.rentalTraversalBanned(state)` and `tov.rentalDropOffBanned(state)`
   - Location: `application/src/main/java/org/opentripplanner/street/model/edge/StreetEdge.java:333-413`

### Key Classes to Reuse

| Class | Location | Purpose |
|-------|----------|---------|
| `GeofencingZone` | `service/vehiclerental/model/GeofencingZone.java:12` | The zone model (record) |
| `GeofencingVertexUpdater` | `updater/vehicle_rental/GeofencingVertexUpdater.java:35` | Applies zones to edges |
| `GbfsFeedLoaderAndMapper` | `updater/vehicle_rental/datasources/gbfs/GbfsFeedLoaderAndMapper.java:20` | Loads and maps GBFS feeds |
| `GbfsGeofencingZoneMapper` (v2/v3) | `updater/vehicle_rental/datasources/gbfs/v*/GbfsGeofencingZoneMapper.java` | Maps GBFS zones to internal model |
| `GeofencingZoneExtension` | `service/vehiclerental/street/GeofencingZoneExtension.java:17` | Extension applied to edges |
| `BusinessAreaBorder` | `service/vehiclerental/street/BusinessAreaBorder.java:12` | Border restriction extension |

### Sandbox Feature Structure

Based on the `Emission` sandbox feature pattern, the POC should have this structure:

```
application/src/ext/java/org/opentripplanner/ext/gbfsgeofencing/
├── configure/
│   ├── GbfsGeofencingRepositoryModule.java    # Dagger module for repository
│   └── GbfsGeofencingGraphBuilderModule.java  # Dagger module for graph builder
├── config/
│   └── GbfsGeofencingConfig.java              # Configuration mapping
├── parameters/
│   └── GbfsGeofencingParameters.java          # Build parameters
├── internal/
│   ├── DefaultGbfsGeofencingRepository.java   # Repository implementation
│   └── graphbuilder/
│       └── GbfsGeofencingGraphBuilder.java    # Graph builder module
├── GbfsGeofencingRepository.java              # Public repository interface
└── package.md                                 # Documentation
```

### Implementation Steps

#### Step 1: Add OTPFeature Flag

In `application/src/main/java/org/opentripplanner/framework/application/OTPFeature.java`:

```java
GbfsGeofencingBuildTime(
  false,
  true,
  "Load GBFS geofencing zones at graph build time instead of runtime."
),
```

#### Step 2: Create Configuration Mapping

Create `GbfsGeofencingConfig.java` to parse `build-config.json`:

```java
public class GbfsGeofencingConfig {
  public static GbfsGeofencingParameters mapConfig(String parameterName, NodeAdapter root) {
    var c = root.of(parameterName).since(V2_8).asObject();
    return new GbfsGeofencingParameters(
      c.of("feeds").asList(GbfsGeofencingConfig::mapFeed)
    );
  }

  private static GbfsGeofencingFeedParameters mapFeed(NodeAdapter node) {
    return new GbfsGeofencingFeedParameters(
      node.of("url").asString(),
      node.of("network").asString(null),
      // ... other params from GbfsVehicleRentalDataSourceParameters
    );
  }
}
```

#### Step 3: Create Repository Interface

```java
public interface GbfsGeofencingRepository extends Serializable {
  void addGeofencingZones(List<GeofencingZone> zones);
  Collection<GeofencingZone> getGeofencingZones();
  Map<StreetEdge, RentalRestrictionExtension> getModifiedEdges();
  void setModifiedEdges(Map<StreetEdge, RentalRestrictionExtension> edges);
}
```

#### Step 4: Create Graph Builder Module

The key class - reuses existing updater infrastructure:

```java
public class GbfsGeofencingGraphBuilder implements GraphBuilderModule {

  private final List<GbfsGeofencingFeedParameters> feedParameters;
  private final GbfsGeofencingRepository repository;
  private final OtpHttpClientFactory httpClientFactory;
  private final Graph graph;

  @Override
  public void buildGraph() {
    List<GeofencingZone> allZones = new ArrayList<>();

    for (var feedParams : feedParameters) {
      // Reuse existing GBFS loading infrastructure
      var params = new GbfsVehicleRentalDataSourceParameters(
        feedParams.url(),
        feedParams.language(),
        false, // allowKeepingRentedVehicleAtDestination
        feedParams.httpHeaders(),
        feedParams.network(),
        true,  // geofencingZones enabled
        false, // overloadingAllowed
        Set.of() // empty rental pickup types
      );

      try {
        var loaderAndMapper = new GbfsFeedLoaderAndMapper(params, httpClientFactory);
        loaderAndMapper.update();
        allZones.addAll(loaderAndMapper.getGeofencingZones());
      } catch (Exception e) {
        LOG.error("Failed to load geofencing zones from {}", feedParams.url(), e);
      }
    }

    if (!allZones.isEmpty()) {
      // Reuse existing zone application logic
      var updater = new GeofencingVertexUpdater(graph::findEdges);
      var modifiedEdges = updater.applyGeofencingZones(allZones);

      repository.addGeofencingZones(allZones);
      repository.setModifiedEdges(modifiedEdges);

      LOG.info("Applied {} geofencing zones to {} edges at build time",
               allZones.size(), modifiedEdges.size());
    }
  }
}
```

#### Step 5: Register in Dagger Components

In `GraphBuilderFactory.java`:
```java
@Component(
  modules = {
    // ... existing modules
    GbfsGeofencingGraphBuilderModule.class,  // Add
  }
)
public interface GraphBuilderFactory {
  @Nullable
  GbfsGeofencingGraphBuilder gbfsGeofencingGraphBuilder();  // Add
  // ...
}
```

In `GraphBuilder.create()`:
```java
graphBuilder.addModuleOptional(
  factory.gbfsGeofencingGraphBuilder(),
  OTPFeature.GbfsGeofencingBuildTime
);
```

#### Step 6: Handle Serialization

Add to `SerializedGraphObject.java`:
```java
public final @Nullable GbfsGeofencingRepository gbfsGeofencingRepository;
```

### Configuration Example

In `build-config.json`:
```json
{
  "gbfsGeofencing": {
    "feeds": [
      {
        "url": "https://example.com/gbfs/gbfs.json",
        "network": "MyNetwork"
      }
    ]
  }
}
```

In `otp-config.json`:
```json
{
  "otpFeatures": {
    "GbfsGeofencingBuildTime": true
  }
}
```

### Considerations for POC

1. **Conflict with Runtime Updates**: If both build-time and runtime geofencing are enabled, zones will be applied twice. The POC should either:
   - Disable runtime geofencing for networks configured in build-time
   - Clear build-time zones when runtime updater runs

2. **Zone Freshness**: Build-time zones are static until graph rebuild. Consider adding timestamp logging.

3. **Error Handling**: GBFS feed failures during build should be non-fatal (log warning and continue).

4. **Edge Serialization**: The `RentalRestrictionExtension` applied to vertices is already serializable. No additional work needed.

5. **Testing**: Create test with mock GBFS server (see existing `GbfsFeedLoaderTest.java`).

## Code References

- `VehicleRentalUpdater.java:227-245` - Current runtime zone application
- `GeofencingVertexUpdater.java:47-91` - Zone application logic to reuse
- `GbfsFeedLoaderAndMapper.java:96-106` - GBFS loading and mapping
- `EmissionGraphBuilder.java:27-90` - Template for sandbox graph builder
- `OTPFeature.java:106-155` - Sandbox feature flag definitions
- `GraphBuilder.java:176` - Where sandbox modules are added
- `GraphBuilderFactory.java:51-62` - Dagger component module registration

## Architecture Insights

The OTP architecture separates build-time and runtime data loading:
- **Build-time**: Data loaded via `GraphBuilderModule` implementations, stored in repositories, serialized with graph
- **Runtime**: Data loaded via `PollingGraphUpdater` implementations, applied to graph dynamically

For geofencing zones, build-time loading is appropriate when:
- Zones are relatively static (don't change frequently)
- You want to avoid external dependencies at runtime
- Graph rebuild cadence matches zone update frequency

## Open Questions

1. Should build-time zones take precedence over runtime zones, or vice versa?
2. Should we support multiple GBFS feeds with potentially overlapping zones?
3. Is there a need to invalidate/refresh build-time zones without full graph rebuild?
4. Should the feature include zone visualization in the debug UI?
