# GBFS Geofencing Zones at Graph Build Time - Implementation Plan

## Overview

This plan describes how to create a POC sandbox feature for loading GBFS geofencing zones at graph build time instead of runtime. The feature will allow geofencing zone restrictions to be baked into the graph during the build process, avoiding external dependencies at runtime and improving startup performance for deployments where geofencing zones are relatively static.

## Current State Analysis

### Current Implementation (Runtime)

Geofencing zones are currently loaded at runtime via the `VehicleRentalUpdater`:

1. **Data Loading**: `GbfsVehicleRentalDataSource` → `GbfsFeedLoaderAndMapper` → `GbfsFeedMapper` → `GbfsGeofencingZoneMapper`
   - Location: `application/src/main/java/org/opentripplanner/updater/vehicle_rental/datasources/gbfs/`

2. **Zone Application**: `VehicleRentalUpdater.VehicleRentalGraphWriterRunnable` creates a `GeofencingVertexUpdater` that applies `RentalRestrictionExtension` to intersecting street edges
   - Location: `application/src/main/java/org/opentripplanner/updater/vehicle_rental/VehicleRentalUpdater.java:227-245`

3. **Routing Effect**: `Vertex.rentalTraversalBanned(state)` and `Vertex.rentalDropOffBanned(state)` check restrictions during routing
   - Location: `application/src/main/java/org/opentripplanner/street/model/vertex/Vertex.java:251-265`

### Key Existing Classes to Reuse

| Class | Location | Purpose |
|-------|----------|---------|
| `GeofencingZone` | `service/vehiclerental/model/GeofencingZone.java:12` | Zone model record |
| `GeofencingVertexUpdater` | `updater/vehicle_rental/GeofencingVertexUpdater.java:35` | Applies zones to edges |
| `GbfsFeedLoaderAndMapper` | `updater/vehicle_rental/datasources/gbfs/GbfsFeedLoaderAndMapper.java:20` | Loads and maps GBFS feeds |
| `GbfsGeofencingZoneMapper` | `updater/vehicle_rental/datasources/gbfs/v*/GbfsGeofencingZoneMapper.java` | Maps GBFS to internal model |
| `GeofencingZoneExtension` | `service/vehiclerental/street/GeofencingZoneExtension.java:17` | Extension for restricted zones |
| `BusinessAreaBorder` | `service/vehiclerental/street/BusinessAreaBorder.java:12` | Extension for business area borders |

### Key Discoveries

- `GeofencingVertexUpdater.applyGeofencingZones()` at line 47-91 handles all the zone application logic
- Extensions are applied to `StreetEdge.fromv` vertex via `streetEdge.addRentalRestriction(ext)` at line 127
- `RentalRestrictionExtension` objects are NOT serializable by default, but `Vertex.rentalRestrictions` field is `transient`-like in effect since extensions are applied by the updater
- The emission sandbox feature at `application/src/ext/java/org/opentripplanner/ext/emission/` provides the template pattern

## Desired End State

After implementation:

1. A new OTPFeature flag `GbfsGeofencingBuildTime` enables the sandbox feature
2. GBFS geofencing feeds can be configured in `build-config.json` under a `gbfsGeofencing` section
3. During graph build, geofencing zones are fetched from configured GBFS feeds and applied to street edges
4. The applied restrictions persist with the graph serialization (via the `Vertex.rentalRestrictions` field)
5. At runtime, routing respects the baked-in geofencing restrictions without needing runtime updaters

### Verification

- Build a graph with geofencing zones configured
- Verify zones are applied during build (check log output)
- Load the serialized graph and verify restrictions work during routing
- Run routing tests that cross geofencing zone boundaries

## What We're NOT Doing

1. **Not replacing runtime updaters** - The existing `VehicleRentalUpdater` geofencing functionality remains unchanged
2. **Not handling conflicts** - If both build-time and runtime geofencing are enabled for the same network, behavior is undefined (zones will be applied twice)
3. **Not adding visualization** - No debug UI changes for viewing build-time zones
4. **Not supporting zone refresh** - Build-time zones are static until graph rebuild
5. **Not adding vehicle rental station loading** - This POC only loads geofencing zones, not rental stations

## Implementation Approach

Follow the established sandbox feature pattern from the Emission module:

1. Create OTPFeature flag
2. Create configuration classes (parameters + config mapper)
3. Create repository interface and implementation (for tracking applied zones)
4. Create Dagger modules (repository + graph builder)
5. Create GraphBuilderModule implementation
6. Wire into GraphBuilder and serialization
7. Add tests

---

## Phase 1: Create OTPFeature Flag and Configuration

### Overview

Add the feature flag and configuration parsing infrastructure.

### Changes Required:

#### 1. Add OTPFeature Flag
**File**: `application/src/main/java/org/opentripplanner/framework/application/OTPFeature.java`
**Location**: After line 154 (after `TransferAnalyzer`)

```java
GbfsGeofencingBuildTime(
  false,
  true,
  "Load GBFS geofencing zones at graph build time instead of runtime."
),
```

#### 2. Create Parameters Record
**File**: `application/src/ext/java/org/opentripplanner/ext/gbfsgeofencing/parameters/GbfsGeofencingFeedParameters.java` (new file)

```java
package org.opentripplanner.ext.gbfsgeofencing.parameters;

import java.util.Map;
import javax.annotation.Nullable;

/**
 * Parameters for a single GBFS geofencing feed.
 */
public record GbfsGeofencingFeedParameters(
  String url,
  @Nullable String network,
  @Nullable String language,
  Map<String, String> httpHeaders
) {
  public GbfsGeofencingFeedParameters {
    if (url == null || url.isBlank()) {
      throw new IllegalArgumentException("GBFS feed URL is required");
    }
    if (httpHeaders == null) {
      httpHeaders = Map.of();
    }
  }
}
```

#### 3. Create Main Parameters Class
**File**: `application/src/ext/java/org/opentripplanner/ext/gbfsgeofencing/parameters/GbfsGeofencingParameters.java` (new file)

```java
package org.opentripplanner.ext.gbfsgeofencing.parameters;

import java.util.List;

/**
 * Configuration parameters for build-time GBFS geofencing zone loading.
 */
public record GbfsGeofencingParameters(List<GbfsGeofencingFeedParameters> feeds) {
  public GbfsGeofencingParameters {
    if (feeds == null) {
      feeds = List.of();
    }
  }

  public boolean hasFeeds() {
    return !feeds.isEmpty();
  }
}
```

#### 4. Create Config Mapper
**File**: `application/src/ext/java/org/opentripplanner/ext/gbfsgeofencing/config/GbfsGeofencingConfig.java` (new file)

```java
package org.opentripplanner.ext.gbfsgeofencing.config;

import static org.opentripplanner.standalone.config.framework.json.OtpVersion.V2_8;

import java.util.List;
import java.util.Map;
import org.opentripplanner.ext.gbfsgeofencing.parameters.GbfsGeofencingFeedParameters;
import org.opentripplanner.ext.gbfsgeofencing.parameters.GbfsGeofencingParameters;
import org.opentripplanner.standalone.config.framework.json.NodeAdapter;

public class GbfsGeofencingConfig {

  public static GbfsGeofencingParameters mapConfig(String parameterName, NodeAdapter root) {
    var c = root
      .of(parameterName)
      .since(V2_8)
      .summary("Load GBFS geofencing zones at graph build time.")
      .description(
        """
        This sandbox feature allows loading GBFS geofencing zones during graph build instead of
        at runtime. This is useful when geofencing zones are relatively static and you want to
        avoid external dependencies at runtime.

        Note: If both build-time and runtime geofencing are enabled for the same network,
        zones will be applied twice.
        """
      )
      .asObject();

    if (c.isEmpty()) {
      return new GbfsGeofencingParameters(List.of());
    }

    return new GbfsGeofencingParameters(mapFeeds(c));
  }

  private static List<GbfsGeofencingFeedParameters> mapFeeds(NodeAdapter config) {
    return config
      .of("feeds")
      .since(V2_8)
      .summary("List of GBFS feeds to load geofencing zones from.")
      .asObjects(GbfsGeofencingConfig::mapFeed);
  }

  private static GbfsGeofencingFeedParameters mapFeed(NodeAdapter node) {
    return new GbfsGeofencingFeedParameters(
      node.of("url").since(V2_8).summary("URL of the GBFS feed (gbfs.json endpoint).").asString(),
      node
        .of("network")
        .since(V2_8)
        .summary("Network identifier. If not provided, extracted from GBFS system_id.")
        .asString(null),
      node
        .of("language")
        .since(V2_8)
        .summary("Preferred language for GBFS feed (BCP-47 code).")
        .asString(null),
      node
        .of("httpHeaders")
        .since(V2_8)
        .summary("HTTP headers to include in GBFS requests.")
        .asStringMap()
    );
  }
}
```

#### 5. Add Configuration to BuildConfig
**File**: `application/src/main/java/org/opentripplanner/standalone/config/BuildConfig.java`

Add import:
```java
import org.opentripplanner.ext.gbfsgeofencing.config.GbfsGeofencingConfig;
import org.opentripplanner.ext.gbfsgeofencing.parameters.GbfsGeofencingParameters;
```

Add field (after line 177 `emission` field):
```java
public final GbfsGeofencingParameters gbfsGeofencing;
```

Add parsing in constructor (after line 600 `emission` parsing):
```java
gbfsGeofencing = GbfsGeofencingConfig.mapConfig("gbfsGeofencing", root);
```

### Success Criteria:

#### Automated Verification:
- [x] Project compiles: `mvn compile -DskipTests`
- [x] No linting errors: `mvn prettier:check`

#### Manual Verification:
- [ ] Configuration doc test generates documentation for new config section

---

## Phase 2: Create Repository and Dagger Module

### Overview

Create the repository interface and implementation for tracking geofencing zone data, plus the Dagger module for dependency injection.

### Changes Required:

#### 1. Create Repository Interface
**File**: `application/src/ext/java/org/opentripplanner/ext/gbfsgeofencing/GbfsGeofencingRepository.java` (new file)

```java
package org.opentripplanner.ext.gbfsgeofencing;

import java.io.Serializable;
import java.util.Collection;
import org.opentripplanner.service.vehiclerental.model.GeofencingZone;

/**
 * Repository for tracking GBFS geofencing zones loaded at build time.
 * This is primarily for observability - the actual restrictions are stored
 * on the vertices themselves.
 */
public interface GbfsGeofencingRepository extends Serializable {

  /**
   * Add geofencing zones to the repository.
   */
  void addGeofencingZones(Collection<GeofencingZone> zones);

  /**
   * Get all geofencing zones.
   */
  Collection<GeofencingZone> getGeofencingZones();

  /**
   * Get the number of street edges modified by geofencing zones.
   */
  int getModifiedEdgeCount();

  /**
   * Set the count of modified edges (for logging/metrics).
   */
  void setModifiedEdgeCount(int count);

  /**
   * Check if any geofencing zones were loaded.
   */
  default boolean hasGeofencingZones() {
    return !getGeofencingZones().isEmpty();
  }
}
```

#### 2. Create Repository Implementation
**File**: `application/src/ext/java/org/opentripplanner/ext/gbfsgeofencing/internal/DefaultGbfsGeofencingRepository.java` (new file)

```java
package org.opentripplanner.ext.gbfsgeofencing.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.opentripplanner.ext.gbfsgeofencing.GbfsGeofencingRepository;
import org.opentripplanner.service.vehiclerental.model.GeofencingZone;

public class DefaultGbfsGeofencingRepository implements GbfsGeofencingRepository {

  private final List<GeofencingZone> geofencingZones = new ArrayList<>();
  private int modifiedEdgeCount = 0;

  @Override
  public void addGeofencingZones(Collection<GeofencingZone> zones) {
    geofencingZones.addAll(zones);
  }

  @Override
  public Collection<GeofencingZone> getGeofencingZones() {
    return Collections.unmodifiableCollection(geofencingZones);
  }

  @Override
  public int getModifiedEdgeCount() {
    return modifiedEdgeCount;
  }

  @Override
  public void setModifiedEdgeCount(int count) {
    this.modifiedEdgeCount = count;
  }
}
```

#### 3. Create Repository Dagger Module
**File**: `application/src/ext/java/org/opentripplanner/ext/gbfsgeofencing/configure/GbfsGeofencingRepositoryModule.java` (new file)

```java
package org.opentripplanner.ext.gbfsgeofencing.configure;

import dagger.Module;
import dagger.Provides;
import javax.inject.Singleton;
import org.opentripplanner.ext.gbfsgeofencing.GbfsGeofencingRepository;
import org.opentripplanner.ext.gbfsgeofencing.internal.DefaultGbfsGeofencingRepository;
import org.opentripplanner.framework.application.OTPFeature;

@Module
public class GbfsGeofencingRepositoryModule {

  @Provides
  @Singleton
  static GbfsGeofencingRepository provideGbfsGeofencingRepository() {
    if (OTPFeature.GbfsGeofencingBuildTime.isOn()) {
      return new DefaultGbfsGeofencingRepository();
    }
    return null;
  }
}
```

#### 4. Register Repository Module in LoadApplicationFactory
**File**: `application/src/main/java/org/opentripplanner/standalone/configure/LoadApplicationFactory.java`

Add import:
```java
import org.opentripplanner.ext.gbfsgeofencing.GbfsGeofencingRepository;
import org.opentripplanner.ext.gbfsgeofencing.configure.GbfsGeofencingRepositoryModule;
```

Add module to @Component annotation (after `EmissionRepositoryModule.class`):
```java
GbfsGeofencingRepositoryModule.class,
```

Add method to interface:
```java
@Singleton
@Nullable
GbfsGeofencingRepository gbfsGeofencingRepository();
```

### Success Criteria:

#### Automated Verification:
- [ ] Project compiles: `mvn compile -DskipTests`
- [ ] Dagger generates code without errors

#### Manual Verification:
- [ ] Repository is correctly injected when feature is enabled

---

## Phase 3: Create Graph Builder Module

### Overview

Create the GraphBuilderModule that loads GBFS geofencing zones and applies them to street edges during graph build.

### Changes Required:

#### 1. Create Graph Builder Dagger Module
**File**: `application/src/ext/java/org/opentripplanner/ext/gbfsgeofencing/configure/GbfsGeofencingGraphBuilderModule.java` (new file)

```java
package org.opentripplanner.ext.gbfsgeofencing.configure;

import dagger.Module;
import dagger.Provides;
import javax.annotation.Nullable;
import javax.inject.Singleton;
import org.opentripplanner.ext.gbfsgeofencing.GbfsGeofencingRepository;
import org.opentripplanner.ext.gbfsgeofencing.internal.graphbuilder.GbfsGeofencingGraphBuilder;
import org.opentripplanner.routing.graph.Graph;
import org.opentripplanner.standalone.config.BuildConfig;

@Module
public class GbfsGeofencingGraphBuilderModule {

  @Provides
  @Singleton
  @Nullable
  static GbfsGeofencingGraphBuilder provideGbfsGeofencingGraphBuilder(
    BuildConfig config,
    @Nullable GbfsGeofencingRepository repository,
    Graph graph
  ) {
    if (repository == null || !config.gbfsGeofencing.hasFeeds()) {
      return null;
    }

    return new GbfsGeofencingGraphBuilder(
      config.gbfsGeofencing,
      repository,
      graph
    );
  }
}
```

#### 2. Create Graph Builder Implementation
**File**: `application/src/ext/java/org/opentripplanner/ext/gbfsgeofencing/internal/graphbuilder/GbfsGeofencingGraphBuilder.java` (new file)

```java
package org.opentripplanner.ext.gbfsgeofencing.internal.graphbuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.opentripplanner.ext.gbfsgeofencing.GbfsGeofencingRepository;
import org.opentripplanner.ext.gbfsgeofencing.parameters.GbfsGeofencingFeedParameters;
import org.opentripplanner.ext.gbfsgeofencing.parameters.GbfsGeofencingParameters;
import org.opentripplanner.graph_builder.model.GraphBuilderModule;
import org.opentripplanner.routing.graph.Graph;
import org.opentripplanner.service.vehiclerental.model.GeofencingZone;
import org.opentripplanner.service.vehiclerental.street.GeofencingVertexUpdater;
import org.opentripplanner.updater.vehicle_rental.datasources.gbfs.GbfsFeedLoaderAndMapper;
import org.opentripplanner.updater.vehicle_rental.datasources.params.GbfsVehicleRentalDataSourceParameters;
import org.opentripplanner.utils.http.HttpUtils;
import org.opentripplanner.utils.http.OtpHttpClientFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Graph builder module that loads GBFS geofencing zones at build time and applies
 * them to street edges.
 */
public class GbfsGeofencingGraphBuilder implements GraphBuilderModule {

  private static final Logger LOG = LoggerFactory.getLogger(GbfsGeofencingGraphBuilder.class);

  private final GbfsGeofencingParameters parameters;
  private final GbfsGeofencingRepository repository;
  private final Graph graph;
  private final OtpHttpClientFactory httpClientFactory;

  public GbfsGeofencingGraphBuilder(
    GbfsGeofencingParameters parameters,
    GbfsGeofencingRepository repository,
    Graph graph
  ) {
    this.parameters = parameters;
    this.repository = repository;
    this.graph = graph;
    this.httpClientFactory = new OtpHttpClientFactory();
  }

  @Override
  public void buildGraph() {
    LOG.info("Loading GBFS geofencing zones at build time from {} feed(s)", parameters.feeds().size());

    List<GeofencingZone> allZones = new ArrayList<>();

    for (var feedParams : parameters.feeds()) {
      try {
        var zones = loadGeofencingZonesFromFeed(feedParams);
        allZones.addAll(zones);
        LOG.info(
          "Loaded {} geofencing zones from GBFS feed: {}",
          zones.size(),
          feedParams.url()
        );
      } catch (Exception e) {
        LOG.error(
          "Failed to load geofencing zones from GBFS feed: {}. Error: {}",
          feedParams.url(),
          e.getMessage()
        );
      }
    }

    if (allZones.isEmpty()) {
      LOG.info("No geofencing zones loaded from any GBFS feeds");
      return;
    }

    // Apply zones to street edges using the existing GeofencingVertexUpdater
    var updater = new GeofencingVertexUpdater(graph::findEdges);
    var modifiedEdges = updater.applyGeofencingZones(allZones);

    repository.addGeofencingZones(allZones);
    repository.setModifiedEdgeCount(modifiedEdges.size());

    LOG.info(
      "Applied {} geofencing zones to {} street edges at build time",
      allZones.size(),
      modifiedEdges.size()
    );
  }

  private List<GeofencingZone> loadGeofencingZonesFromFeed(GbfsGeofencingFeedParameters feedParams) {
    // Create parameters compatible with existing GBFS infrastructure
    var gbfsParams = new GbfsVehicleRentalDataSourceParameters(
      URI.create(feedParams.url()),
      feedParams.language(),
      false, // allowKeepingRentedVehicleAtDestination
      HttpUtils.transformToLowerCaseKeys(feedParams.httpHeaders()),
      feedParams.network(),
      true,  // geofencingZones enabled
      false  // overloadingAllowed
    );

    var loaderAndMapper = new GbfsFeedLoaderAndMapper(gbfsParams, httpClientFactory);

    // Load the feed
    loaderAndMapper.update();

    return loaderAndMapper.getGeofencingZones();
  }
}
```

#### 3. Make GeofencingVertexUpdater Package-Accessible
**File**: `application/src/main/java/org/opentripplanner/updater/vehicle_rental/GeofencingVertexUpdater.java`

Change class visibility from package-private to public (line 35):
```java
public class GeofencingVertexUpdater {
```

#### 4. Register Graph Builder Module in GraphBuilderFactory
**File**: `application/src/main/java/org/opentripplanner/graph_builder/module/configure/GraphBuilderFactory.java`

Add import:
```java
import org.opentripplanner.ext.gbfsgeofencing.GbfsGeofencingRepository;
import org.opentripplanner.ext.gbfsgeofencing.configure.GbfsGeofencingGraphBuilderModule;
import org.opentripplanner.ext.gbfsgeofencing.internal.graphbuilder.GbfsGeofencingGraphBuilder;
```

Add module to @Component annotation:
```java
GbfsGeofencingGraphBuilderModule.class,
```

Add method to interface:
```java
@Nullable
GbfsGeofencingGraphBuilder gbfsGeofencingGraphBuilder();
```

Add @BindsInstance to Builder interface:
```java
Builder gbfsGeofencingRepository(@Nullable GbfsGeofencingRepository gbfsGeofencingRepository);
```

#### 5. Wire into GraphBuilder.create()
**File**: `application/src/main/java/org/opentripplanner/graph_builder/GraphBuilder.java`

Add import:
```java
import org.opentripplanner.ext.gbfsgeofencing.GbfsGeofencingRepository;
```

Add parameter to `create()` method signature (after `stopConsolidationRepository`):
```java
@Nullable GbfsGeofencingRepository gbfsGeofencingRepository,
```

Add to builder chain (after `emissionRepository`):
```java
.gbfsGeofencingRepository(gbfsGeofencingRepository)
```

Add module registration (after line 176, inside `if (hasTransitData)` block or after it):
```java
// GBFS geofencing zones at build time - works with or without transit data
graphBuilder.addModuleOptional(factory.gbfsGeofencingGraphBuilder(), OTPFeature.GbfsGeofencingBuildTime);
```

Note: Place this OUTSIDE the `if (hasTransitData)` block since geofencing zones apply to streets, not transit.

### Success Criteria:

#### Automated Verification:
- [ ] Project compiles: `mvn compile -DskipTests`
- [ ] Unit tests pass: `mvn test -Dtest=GbfsGeofencing*`

#### Manual Verification:
- [ ] Graph builder logs show geofencing zone loading when configured
- [ ] Zones are applied to street edges during build

---

## Phase 4: Wire Serialization and Application Startup

### Overview

Ensure the geofencing repository is serialized with the graph and properly loaded at application startup.

### Changes Required:

#### 1. Add Repository to SerializedGraphObject
**File**: `application/src/main/java/org/opentripplanner/routing/graph/SerializedGraphObject.java`

Add import:
```java
import org.opentripplanner.ext.gbfsgeofencing.GbfsGeofencingRepository;
```

Add field (after line 89 `empiricalDelayRepository`):
```java
public final @Nullable GbfsGeofencingRepository gbfsGeofencingRepository;
```

Add constructor parameter (after `empiricalDelayRepository` parameter):
```java
@Nullable GbfsGeofencingRepository gbfsGeofencingRepository,
```

Add assignment in constructor body:
```java
this.gbfsGeofencingRepository = gbfsGeofencingRepository;
```

#### 2. Update OTPMain to Pass Repository
**File**: `application/src/main/java/org/opentripplanner/standalone/OTPMain.java`

Add call to get repository and pass to SerializedGraphObject constructor (after `app.stopConsolidationRepository()`):
```java
app.gbfsGeofencingRepository(),
```

#### 3. Update LoadApplication
**File**: `application/src/main/java/org/opentripplanner/standalone/configure/LoadApplication.java`

Update the `appConstruction()` method that takes `SerializedGraphObject` to pass the repository (after `stopConsolidationRepository`):
```java
obj.gbfsGeofencingRepository,
```

Also update the method signature for `createAppConstruction()` to accept the repository.

#### 4. Wire Repository through ConstructApplication
**File**: `application/src/main/java/org/opentripplanner/standalone/configure/ConstructApplication.java`

Add field and getter for the repository, following the pattern of `emissionRepository`.

#### 5. Wire Repository through ConstructApplicationFactory
**File**: `application/src/main/java/org/opentripplanner/standalone/configure/ConstructApplicationFactory.java`

Add the repository to the Dagger component following the pattern of `EmissionRepository`.

### Success Criteria:

#### Automated Verification:
- [ ] Project compiles: `mvn compile -DskipTests`
- [ ] Serialization roundtrip works: build graph, save, load

#### Manual Verification:
- [ ] After loading a saved graph, geofencing zones are active
- [ ] Routing across zone boundaries respects restrictions

---

## Phase 5: Add Tests

### Overview

Add unit and integration tests for the new functionality.

### Changes Required:

#### 1. Create Unit Test for GbfsGeofencingGraphBuilder
**File**: `application/src/ext-test/java/org/opentripplanner/ext/gbfsgeofencing/internal/graphbuilder/GbfsGeofencingGraphBuilderTest.java` (new file)

```java
package org.opentripplanner.ext.gbfsgeofencing.internal.graphbuilder;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.opentripplanner.ext.gbfsgeofencing.internal.DefaultGbfsGeofencingRepository;
import org.opentripplanner.ext.gbfsgeofencing.parameters.GbfsGeofencingFeedParameters;
import org.opentripplanner.ext.gbfsgeofencing.parameters.GbfsGeofencingParameters;
import org.opentripplanner.routing.graph.Graph;
import org.opentripplanner.street.model._data.StreetModelForTest;

class GbfsGeofencingGraphBuilderTest {

  @Test
  void testEmptyFeedsDoesNothing() {
    var graph = new Graph();
    var repository = new DefaultGbfsGeofencingRepository();
    var params = new GbfsGeofencingParameters(List.of());

    var builder = new GbfsGeofencingGraphBuilder(params, repository, graph);
    builder.buildGraph();

    assertFalse(repository.hasGeofencingZones());
    assertEquals(0, repository.getModifiedEdgeCount());
  }
}
```

#### 2. Create Test for Parameters
**File**: `application/src/ext-test/java/org/opentripplanner/ext/gbfsgeofencing/parameters/GbfsGeofencingParametersTest.java` (new file)

```java
package org.opentripplanner.ext.gbfsgeofencing.parameters;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GbfsGeofencingParametersTest {

  @Test
  void testHasFeedsWithEmptyList() {
    var params = new GbfsGeofencingParameters(List.of());
    assertFalse(params.hasFeeds());
  }

  @Test
  void testHasFeedsWithFeeds() {
    var feed = new GbfsGeofencingFeedParameters(
      "https://example.com/gbfs/gbfs.json",
      "test-network",
      "en",
      Map.of()
    );
    var params = new GbfsGeofencingParameters(List.of(feed));
    assertTrue(params.hasFeeds());
  }

  @Test
  void testFeedParametersRequiresUrl() {
    assertThrows(
      IllegalArgumentException.class,
      () -> new GbfsGeofencingFeedParameters(null, null, null, null)
    );
    assertThrows(
      IllegalArgumentException.class,
      () -> new GbfsGeofencingFeedParameters("", null, null, null)
    );
  }
}
```

#### 3. Add Configuration Doc Test
**File**: `application/src/ext-test/java/org/opentripplanner/ext/gbfsgeofencing/doc/GbfsGeofencingConfigurationDocTest.java` (new file)

Follow the pattern from `EmissionConfigurationDocTest.java`.

### Success Criteria:

#### Automated Verification:
- [ ] All tests pass: `mvn test`
- [ ] Code coverage for new classes is adequate

#### Manual Verification:
- [ ] Integration test with real GBFS feed works (if available)

---

## Phase 6: Documentation

### Overview

Add package documentation and update user documentation.

### Changes Required:

#### 1. Create Package Documentation
**File**: `application/src/ext/java/org/opentripplanner/ext/gbfsgeofencing/package.md` (new file)

```markdown
# GBFS Geofencing Zones at Build Time

This sandbox feature allows loading GBFS geofencing zones during graph build instead of at runtime.

## When to Use

- When geofencing zones are relatively static and don't need real-time updates
- When you want to avoid external dependencies at runtime
- When graph rebuild cadence matches zone update frequency

## Configuration

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

## How It Works

1. During graph build, the configured GBFS feeds are fetched
2. Geofencing zones are extracted from the feeds
3. Zones are applied to street edges using `GeofencingVertexUpdater`
4. The restrictions persist with the serialized graph

## Limitations

- Zones are static until graph rebuild
- If both build-time and runtime geofencing are enabled for the same network, behavior is undefined
```

### Success Criteria:

#### Automated Verification:
- [ ] Documentation builds without errors

#### Manual Verification:
- [ ] Documentation is clear and accurate

---

## Testing Strategy

### Unit Tests

- `GbfsGeofencingParametersTest` - Validate parameter records
- `GbfsGeofencingGraphBuilderTest` - Test graph builder with mock data
- `DefaultGbfsGeofencingRepositoryTest` - Test repository implementation
- `GbfsGeofencingConfigTest` - Test configuration parsing

### Integration Tests

- Test with mock GBFS server using existing `GbfsFeedLoaderTest` patterns
- Test serialization roundtrip (build → save → load → verify zones active)
- Test routing across geofencing zone boundaries

### Manual Testing Steps

1. Configure a GBFS feed with geofencing zones in `build-config.json`
2. Enable `GbfsGeofencingBuildTime` feature in `otp-config.json`
3. Build graph and verify log output shows zone loading
4. Save and reload graph
5. Test routing requests that cross geofencing zone boundaries
6. Verify drop-off and traversal restrictions are enforced

---

## Performance Considerations

1. **Build Time Impact**: Loading GBFS feeds adds network latency to graph build. Consider timeout settings.
2. **Memory**: Geofencing zones are stored twice (in repository for observability, on vertices for routing). The vertex storage is the primary use.
3. **Large Zone Count**: If many zones intersect many edges, the `applyGeofencingZones` operation can be slow. The existing optimization for business area borders helps.

---

## Migration Notes

This is a new feature with no migration requirements. Existing deployments using runtime geofencing updaters will continue to work unchanged.

---

## References

- Research document: `thoughts/shared/research/2025-12-01-gbfs-geofencing-zones-graph-build-sandbox.md`
- Emission sandbox feature: `application/src/ext/java/org/opentripplanner/ext/emission/`
- Runtime geofencing: `application/src/main/java/org/opentripplanner/updater/vehicle_rental/VehicleRentalUpdater.java`
- GeofencingVertexUpdater: `application/src/main/java/org/opentripplanner/updater/vehicle_rental/GeofencingVertexUpdater.java`
- OTPFeature flags: `application/src/main/java/org/opentripplanner/framework/application/OTPFeature.java`
