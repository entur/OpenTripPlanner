---
date: 2025-10-08T18:36:35+0000
researcher: testower
git_commit: 9bac5bb2fce2f266a3089af836138904a69c7f1a
branch: feature/vehicle_rental_service_directory_updater
repository: OpenTripPlanner
topic: "Dynamic Updater Management for Vehicle Rental Service Directory"
tags: [research, codebase, updater, vehicle-rental, service-directory, graph-updater-manager]
status: complete
last_updated: 2025-10-08
last_updated_by: testower
last_updated_note: "Added Option 2B with concrete UpdaterRegistry service and listener pattern"
---

# Research: Dynamic Updater Management for Vehicle Rental Service Directory

**Date**: 2025-10-08T18:36:35+0000
**Researcher**: testower
**Git Commit**: 9bac5bb2fce2f266a3089af836138904a69c7f1a
**Branch**: feature/vehicle_rental_service_directory_updater
**Repository**: OpenTripPlanner

## Research Question

How can we implement a service directory updater that can dynamically add and remove VehicleRentalUpdater instances from the GraphUpdaterManager by polling the GBFS v3 manifest.json file?

## Summary

The GraphUpdaterManager currently does not support adding or removing updaters at runtime. All updaters are created during initialization and the manager maintains them in a final, immutable list. To implement a dynamic service directory updater that can add/remove VehicleRentalUpdater instances, we would need to:

1. **Extend GraphUpdaterManager** to support runtime updater management (add/remove methods)
2. **Implement locking mechanisms** for thread-safe updater list modifications
3. **Create a ServiceDirectoryUpdater** that polls the manifest.json and diffs against current updaters
4. **Handle thread pool lifecycle** for dynamically added/removed updaters
5. **Manage cleanup** using the existing `teardown()` mechanism

This is a non-trivial architectural change as the GraphUpdaterManager was designed with a fixed set of updaters in mind.

## Detailed Findings

### Current Architecture: Fixed Updater Set

The GraphUpdaterManager (`application/src/main/java/org/opentripplanner/updater/GraphUpdaterManager.java:74-90`) receives all updaters in its constructor and stores them in an immutable list:

```java
private final List<GraphUpdater> updaterList = new ArrayList<>();

public GraphUpdaterManager(RealTimeUpdateContext context, List<GraphUpdater> updaters) {
  // ... create thread pools ...
  for (GraphUpdater updater : updaters) {
    updaterList.add(updater);
    updater.setup(this);  // Provide WriteToGraphCallback
  }
}
```

**Key Limitations**:
- `updaterList` is private with no add/remove methods (`GraphUpdaterManager.java:63`)
- Thread pools sized at construction based on CPU count (`GraphUpdaterManager.java:80-83`)
- No synchronization mechanisms for concurrent list modifications
- Updaters scheduled immediately after construction in `startUpdaters()` (`GraphUpdaterManager.java:96-123`)

### Manifest.json Structure (GBFS v3)

The manifest file (`application/src/ext-test/resources/org/opentripplanner/ext/vehiclerentalservicedirectory/manifest.json:1-40`) contains:

```json
{
  "last_updated": "2025-09-23T06:37:23.273+00:00",
  "ttl": 3600,
  "version": "3.0",
  "data": {
    "datasets": [
      {
        "system_id": "oslo-by-sykkel",
        "versions": [
          {
            "version": "2.3",
            "url": "https://api.example.com/gbfs/v2/oslo-by-sykkel/gbfs"
          },
          {
            "version": "3.0",
            "url": "https://api.example.com/gbfs/v3/oslo-by-sykkel/gbfs"
          }
        ]
      }
    ]
  }
}
```

Each dataset represents a separate vehicle rental system that would require its own VehicleRentalUpdater instance.

### Existing Teardown Mechanism

VehicleRentalUpdater already implements cleanup (`application/src/main/java/org/opentripplanner/updater/vehicle_rental/VehicleRentalUpdater.java:138-145`):

```java
@Override
public void teardown() {
  VehicleRentalGraphWriterRunnable graphWriterRunnable = new VehicleRentalGraphWriterRunnable(
    Collections.emptyList(),
    Collections.emptyList()
  );
  updateGraph(graphWriterRunnable);
}
```

This removes all stations by submitting an empty update, leveraging the existing add/remove diff logic in the GraphWriterRunnable (`VehicleRentalUpdater.java:219-232`).

### VehicleRentalUpdater Construction Requirements

To construct a VehicleRentalUpdater, the following dependencies are required (`application/src/main/java/org/opentripplanner/updater/vehicle_rental/VehicleRentalUpdater.java:65-70`):

1. **VehicleRentalUpdaterParameters** - Contains frequency and source config
2. **VehicleRentalDataSource** - Fetches rental station data (created by VehicleRentalDataSourceFactory)
3. **VertexLinker** - Links rental vertices to street graph
4. **VehicleRentalRepository** - Stores and retrieves rental stations

These dependencies are available during initial setup in UpdaterConfigurator (`application/src/main/java/org/opentripplanner/updater/configure/UpdaterConfigurator.java:165-174`).

### Thread Pool Management

GraphUpdaterManager uses three executor services (`application/src/main/java/org/opentripplanner/updater/GraphUpdaterManager.java:50-58`):

1. **scheduler** - Single-threaded for sequential graph writes
2. **pollingUpdaterPool** - Scheduled pool for polling updaters (size = max(6, CPU_count))
3. **nonPollingUpdaterPool** - Cached pool for non-polling updaters (unbounded)

Polling updaters are scheduled with fixed delay (`GraphUpdaterManager.java:110-115`):

```java
pollingUpdaterPool.scheduleWithFixedDelay(
  runUpdater,
  0,
  pollingGraphUpdater.pollingPeriod().toSeconds(),
  TimeUnit.SECONDS
);
```

The `scheduleWithFixedDelay()` method returns a `ScheduledFuture<?>` that can be cancelled to stop the updater.

### Existing Pattern: VehicleRentalServiceDirectoryFetcher

There's already a pattern for creating multiple updaters from a manifest (`application/src/ext/java/org/opentripplanner/ext/vehiclerentalservicedirectory/VehicleRentalServiceDirectoryFetcher.java:60-85`):

```java
public static List<GraphUpdater> createUpdatersFromEndpoint(
  VehicleRentalServiceDirectoryFetcherParameters parameters,
  VertexLinker vertexLinker,
  VehicleRentalRepository repository
) {
  var manifest = loadManifest(parameters);
  // ... parse manifest ...
  return serviceDirectory.createUpdatersFromManifest(parameters, manifest);
}
```

However, this is called only once during initialization in UpdaterConfigurator (`application/src/main/java/org/opentripplanner/updater/configure/UpdaterConfigurator.java:104-109`).

## Architecture Considerations

### Challenge 1: GraphUpdaterManager Immutability

**Current Design**:
- The `updaterList` is populated in the constructor and never modified
- No public API exists for adding or removing updaters
- The `stop()` method (`GraphUpdaterManager.java:130-181`) tears down ALL updaters, not individual ones

**Required Changes**:
- Add `synchronized` methods: `addUpdater(GraphUpdater)` and `removeUpdater(GraphUpdater)`
- Track `ScheduledFuture<?>` handles for each polling updater to enable individual cancellation
- Implement proper locking to prevent concurrent modification during iteration

### Challenge 2: Thread Pool Sizing

**Current Design**:
- Polling updater pool sized once at construction: `max(6, CPU_count)` (`GraphUpdaterManager.java:80-83`)
- Fixed size doesn't account for dynamically added updaters

**Potential Solutions**:
1. Keep fixed pool size - should handle dynamic updaters since it's already sized reasonably
2. Dynamically resize pool - complex, may require creating new executor service
3. Use separate pool for service-directory-managed updaters

### Challenge 3: Updater Lifecycle During Add/Remove

**Adding an Updater**:
1. Construct VehicleRentalUpdater with dependencies
2. Call `updater.setup(this)` to provide WriteToGraphCallback
3. Schedule with appropriate executor service based on type
4. Add to tracking structures (list, future map)

**Removing an Updater**:
1. Cancel the ScheduledFuture to stop polling
2. Call `updater.teardown()` to clean up resources
3. Remove from tracking structures
4. Wait for in-flight graph writes to complete

### Challenge 4: Service Directory Updater Design

A hypothetical ServiceDirectoryUpdater would need:

```java
public class VehicleRentalServiceDirectoryUpdater extends PollingGraphUpdater {
  private final GraphUpdaterManager manager;  // Need reference to manager!
  private final Map<String, VehicleRentalUpdater> activeUpdaters = new ConcurrentHashMap<>();
  private final VertexLinker linker;
  private final VehicleRentalRepository repository;
  private final OtpHttpClientFactory httpClientFactory;

  @Override
  protected void runPolling() {
    Manifest manifest = fetchManifest();
    Set<String> currentSystemIds = manifest.getData().getDatasets().stream()
      .map(Dataset::getSystemId)
      .collect(Collectors.toSet());

    // Remove updaters for systems no longer in manifest
    for (Map.Entry<String, VehicleRentalUpdater> entry : activeUpdaters.entrySet()) {
      if (!currentSystemIds.contains(entry.getKey())) {
        manager.removeUpdater(entry.getValue());
        activeUpdaters.remove(entry.getKey());
      }
    }

    // Add updaters for new systems
    for (Dataset dataset : manifest.getData().getDatasets()) {
      if (!activeUpdaters.containsKey(dataset.getSystemId())) {
        VehicleRentalUpdater updater = createUpdater(dataset);
        manager.addUpdater(updater);
        activeUpdaters.put(dataset.getSystemId(), updater);
      }
    }
  }
}
```

**Problem**: This requires GraphUpdaterManager to expose `addUpdater()` and `removeUpdater()` methods, which don't currently exist.

### Challenge 5: Circular Dependency

**Issue**: The ServiceDirectoryUpdater needs a reference to GraphUpdaterManager to add/remove updaters, but GraphUpdaterManager creates and manages updaters.

**Potential Solutions**:
1. **Pass manager reference after construction** - Add `setManager()` method to ServiceDirectoryUpdater
2. **Use a registry pattern** - Create an UpdaterRegistry that both can reference
3. **Inversion of control** - Manager polls ServiceDirectoryUpdater for changes instead of ServiceDirectoryUpdater pushing changes to manager
4. **Event bus pattern** - ServiceDirectoryUpdater publishes events, manager subscribes

## Code References

### Key Files

- `application/src/main/java/org/opentripplanner/updater/GraphUpdaterManager.java:63` - Updater list storage
- `application/src/main/java/org/opentripplanner/updater/GraphUpdaterManager.java:74-90` - Constructor and initialization
- `application/src/main/java/org/opentripplanner/updater/GraphUpdaterManager.java:96-123` - startUpdaters() scheduling logic
- `application/src/main/java/org/opentripplanner/updater/GraphUpdaterManager.java:130-181` - stop() method
- `application/src/main/java/org/opentripplanner/updater/spi/GraphUpdater.java:23-64` - GraphUpdater interface with lifecycle methods
- `application/src/main/java/org/opentripplanner/updater/vehicle_rental/VehicleRentalUpdater.java:138-145` - teardown() implementation
- `application/src/main/java/org/opentripplanner/updater/vehicle_rental/VehicleRentalUpdater.java:147-256` - GraphWriterRunnable with add/remove logic
- `application/src/ext/java/org/opentripplanner/ext/vehiclerentalservicedirectory/VehicleRentalServiceDirectoryFetcher.java:60-85` - Existing manifest parsing pattern
- `application/src/main/java/org/opentripplanner/updater/configure/UpdaterConfigurator.java:99-129` - Updater creation and manager initialization

### Relevant Patterns

**Dynamic Component Management**:
- `application/src/main/java/org/opentripplanner/updater/vehicle_rental/VehicleRentalUpdater.java:219-232` - Station removal pattern
- `application/src/main/java/org/opentripplanner/updater/vehicle_parking/VehicleParkingUpdater.java:95-157` - Add/update/remove diff pattern
- `application/src/main/java/org/opentripplanner/routing/linking/DisposableEdgeCollection.java` - Resource cleanup pattern

**Thread Management**:
- `application/src/main/java/org/opentripplanner/updater/GraphUpdaterManager.java:50-58` - Three executor services
- `application/src/main/java/org/opentripplanner/updater/GraphUpdaterManager.java:105-116` - Conditional scheduling based on updater type

## Implementation Approaches

### Option 1: Extend GraphUpdaterManager (Recommended)

**Changes Required**:

1. **Add dynamic updater management to GraphUpdaterManager**:
```java
private final List<GraphUpdater> updaterList = new ArrayList<>();
private final Map<GraphUpdater, ScheduledFuture<?>> scheduledUpdaters = new ConcurrentHashMap<>();

public synchronized void addUpdater(GraphUpdater updater) {
  updaterList.add(updater);
  updater.setup(this);

  // Schedule the updater
  Runnable runUpdater = () -> {
    try {
      updater.run();
    } catch (Exception e) {
      LOG.error("Error while running updater {}:", updater.getClass().getName(), e);
    }
  };

  if (updater instanceof PollingGraphUpdater pollingGraphUpdater) {
    ScheduledFuture<?> future;
    if (pollingGraphUpdater.runOnlyOnce()) {
      future = pollingUpdaterPool.schedule(runUpdater, 0, TimeUnit.SECONDS);
    } else {
      future = pollingUpdaterPool.scheduleWithFixedDelay(
        runUpdater,
        0,
        pollingGraphUpdater.pollingPeriod().toSeconds(),
        TimeUnit.SECONDS
      );
    }
    scheduledUpdaters.put(updater, future);
  } else {
    nonPollingUpdaterPool.execute(runUpdater);
  }
}

public synchronized void removeUpdater(GraphUpdater updater) {
  // Cancel scheduled execution
  ScheduledFuture<?> future = scheduledUpdaters.remove(updater);
  if (future != null) {
    future.cancel(false);  // Don't interrupt if running
  }

  // Wait for any in-flight graph writes from this updater to complete
  // (This is tricky - may need to track per-updater write futures)

  // Teardown the updater
  updater.teardown();

  // Remove from list
  updaterList.remove(updater);
}
```

2. **Create VehicleRentalServiceDirectoryUpdater**:
```java
public class VehicleRentalServiceDirectoryUpdater extends PollingGraphUpdater {
  private final Map<String, VehicleRentalUpdater> activeUpdaters = new ConcurrentHashMap<>();
  private final VertexLinker linker;
  private final VehicleRentalRepository repository;
  private final OtpHttpClientFactory httpClientFactory;
  private final String manifestUrl;
  private GraphUpdaterManager manager;

  // Called by GraphUpdaterManager after construction
  public void setManager(GraphUpdaterManager manager) {
    this.manager = manager;
  }

  @Override
  protected void runPolling() {
    // Fetch and parse manifest
    // Diff against activeUpdaters
    // Call manager.addUpdater() and manager.removeUpdater()
  }
}
```

3. **Modify UpdaterConfigurator** to set manager reference:
```java
// After creating GraphUpdaterManager
for (GraphUpdater updater : updaters) {
  if (updater instanceof VehicleRentalServiceDirectoryUpdater dirUpdater) {
    dirUpdater.setManager(updaterManager);
  }
}
```

**Pros**:
- Minimal API surface changes
- Reuses existing thread pools
- Leverages existing teardown mechanism
- Straightforward implementation

**Cons**:
- Requires modifying GraphUpdaterManager (core infrastructure)
- Circular dependency (updater needs manager reference)
- Synchronization complexity
- Need to track ScheduledFuture handles

### Option 2A: Registry Interface Pattern

**Changes Required**:

1. **Create UpdaterRegistry**:
```java
public interface UpdaterRegistry {
  void registerUpdater(GraphUpdater updater);
  void unregisterUpdater(GraphUpdater updater);
}
```

2. **GraphUpdaterManager implements UpdaterRegistry**:
```java
public class GraphUpdaterManager implements WriteToGraphCallback, GraphUpdaterStatus, UpdaterRegistry {
  @Override
  public void registerUpdater(GraphUpdater updater) { /* ... */ }

  @Override
  public void unregisterUpdater(GraphUpdater updater) { /* ... */ }
}
```

3. **ServiceDirectoryUpdater receives UpdaterRegistry**:
```java
public VehicleRentalServiceDirectoryUpdater(
  // ... other params ...
  UpdaterRegistry registry
) {
  this.registry = registry;
}
```

**Pros**:
- Cleaner interface segregation
- Less coupling (depends on interface, not concrete class)
- More testable

**Cons**:
- Still requires GraphUpdaterManager changes
- More interfaces to maintain
- Circular dependency still exists (just hidden behind interface)

---

### Option 2B: Concrete UpdaterRegistry Service with Listener Pattern (NEW)

This approach creates a concrete `UpdaterRegistry` service that acts as a mediator between updater lifecycle requests and the `GraphUpdaterManager`. The manager subscribes to registry events via a listener interface.

**Architecture**:
```
VehicleRentalServiceDirectoryUpdater
           ↓ (registers/unregisters)
    UpdaterRegistry (concrete service)
           ↓ (notifies via listener)
    GraphUpdaterManager (implements UpdaterRegistryListener)
```

**Changes Required**:

1. **Create UpdaterRegistryListener Interface**:
```java
/**
 * Listener interface for updater lifecycle events. Implementations are notified
 * when updaters are registered or unregistered in the UpdaterRegistry.
 *
 * Following OTP patterns: listener interface with default empty implementations,
 * similar to ParetoSetEventListener.
 */
public interface UpdaterRegistryListener {

  /**
   * Called when a new updater has been registered and should be started.
   * The listener is responsible for calling setup() and scheduling the updater.
   *
   * @param updater The updater to start
   */
  default void onUpdaterRegistered(GraphUpdater updater) {}

  /**
   * Called when an updater has been unregistered and should be stopped.
   * The listener is responsible for cancelling scheduled execution,
   * calling teardown(), and cleaning up resources.
   *
   * @param updater The updater to stop
   */
  default void onUpdaterUnregistered(GraphUpdater updater) {}

  /**
   * Called when the registry has been closed and no more registrations
   * will be accepted. Allows listener to perform cleanup.
   */
  default void onRegistryClosed() {}
}
```

2. **Create Concrete UpdaterRegistry Service**:
```java
/**
 * Service that manages dynamic updater registration and unregistration.
 * Acts as a mediator between updaters that want to add/remove other updaters
 * (like VehicleRentalServiceDirectoryUpdater) and the GraphUpdaterManager
 * that performs the actual lifecycle management.
 *
 * This service maintains the registry of active dynamically-registered updaters
 * and notifies registered listeners of lifecycle events.
 *
 * Follows OTP patterns: lifecycle subscriptions similar to LifeCycleSubscriptions
 * in Raptor, with separate subscription and notification phases.
 */
public class UpdaterRegistry {

  private static final Logger LOG = LoggerFactory.getLogger(UpdaterRegistry.class);

  private final Map<String, GraphUpdater> registeredUpdaters = new ConcurrentHashMap<>();
  private final List<UpdaterRegistryListener> listeners = new CopyOnWriteArrayList<>();
  private volatile boolean closed = false;

  /**
   * Register a listener to receive updater lifecycle events.
   *
   * @param listener The listener to register
   * @throws IllegalStateException if registry is already closed
   */
  public void addListener(UpdaterRegistryListener listener) {
    if (closed) {
      throw new IllegalStateException("Cannot add listener: registry is closed");
    }
    if (listener != null) {
      listeners.add(listener);
      LOG.debug("Registered UpdaterRegistryListener: {}", listener.getClass().getName());
    }
  }

  /**
   * Register a new updater. The updater will be stored in the registry
   * and all registered listeners will be notified via onUpdaterRegistered().
   *
   * @param key Unique identifier for this updater (e.g., system_id from GBFS)
   * @param updater The updater to register
   * @throws IllegalStateException if registry is closed
   * @throws IllegalArgumentException if key already exists
   */
  public void registerUpdater(String key, GraphUpdater updater) {
    if (closed) {
      throw new IllegalStateException("Cannot register updater: registry is closed");
    }

    if (registeredUpdaters.containsKey(key)) {
      LOG.warn("Updater with key '{}' already registered, skipping", key);
      return;
    }

    LOG.info("Registering updater: {} ({})", key, updater.getClass().getSimpleName());
    registeredUpdaters.put(key, updater);

    // Notify all listeners
    for (UpdaterRegistryListener listener : listeners) {
      try {
        listener.onUpdaterRegistered(updater);
      } catch (Exception e) {
        LOG.error("Error notifying listener {} of updater registration",
                  listener.getClass().getName(), e);
      }
    }
  }

  /**
   * Unregister an existing updater. The updater will be removed from the registry
   * and all registered listeners will be notified via onUpdaterUnregistered().
   *
   * @param key Unique identifier of the updater to remove
   * @return true if updater was found and removed, false if not found
   */
  public boolean unregisterUpdater(String key) {
    GraphUpdater updater = registeredUpdaters.remove(key);

    if (updater == null) {
      LOG.debug("Updater with key '{}' not found in registry", key);
      return false;
    }

    LOG.info("Unregistering updater: {} ({})", key, updater.getClass().getSimpleName());

    // Notify all listeners
    for (UpdaterRegistryListener listener : listeners) {
      try {
        listener.onUpdaterUnregistered(updater);
      } catch (Exception e) {
        LOG.error("Error notifying listener {} of updater unregistration",
                  listener.getClass().getName(), e);
      }
    }

    return true;
  }

  /**
   * Get an updater by its key.
   *
   * @param key The updater's unique identifier
   * @return The updater, or null if not found
   */
  @Nullable
  public GraphUpdater getUpdater(String key) {
    return registeredUpdaters.get(key);
  }

  /**
   * Get all currently registered updater keys.
   *
   * @return Unmodifiable set of keys
   */
  public Set<String> getRegisteredKeys() {
    return Set.copyOf(registeredUpdaters.keySet());
  }

  /**
   * Get the number of currently registered updaters.
   */
  public int size() {
    return registeredUpdaters.size();
  }

  /**
   * Check if an updater with the given key is registered.
   */
  public boolean contains(String key) {
    return registeredUpdaters.containsKey(key);
  }

  /**
   * Close the registry. No more updaters can be registered after this.
   * Notifies all listeners that the registry is closed.
   */
  public void close() {
    if (closed) {
      return;
    }

    LOG.info("Closing UpdaterRegistry with {} registered updaters", registeredUpdaters.size());
    closed = true;

    // Notify all listeners
    for (UpdaterRegistryListener listener : listeners) {
      try {
        listener.onRegistryClosed();
      } catch (Exception e) {
        LOG.error("Error notifying listener {} of registry closure",
                  listener.getClass().getName(), e);
      }
    }
  }

  /**
   * Check if the registry is closed.
   */
  public boolean isClosed() {
    return closed;
  }
}
```

3. **GraphUpdaterManager Implements UpdaterRegistryListener** (revised to remove updaterList):
```java
public class GraphUpdaterManager implements WriteToGraphCallback, GraphUpdaterStatus, UpdaterRegistryListener {

  // REMOVED: private final List<GraphUpdater> updaterList = new ArrayList<>();
  // Instead, track ALL updaters (static + dynamic) in this map:
  private final Map<GraphUpdater, ScheduledFuture<?>> allUpdaters = new ConcurrentHashMap<>();

  /**
   * Constructor now only sets up thread pools, doesn't track updaters in a list.
   */
  public GraphUpdaterManager(RealTimeUpdateContext context, List<GraphUpdater> staticUpdaters) {
    this.realtimeUpdateContext = context;

    // Create thread pools
    var graphWriterThreadFactory = new ThreadFactoryBuilder().setNameFormat("graph-writer").build();
    this.scheduler = Executors.newSingleThreadScheduledExecutor(graphWriterThreadFactory);
    var updaterThreadFactory = new ThreadFactoryBuilder().setNameFormat("updater-%d").build();
    this.pollingUpdaterPool = Executors.newScheduledThreadPool(
      Math.max(MIN_POLLING_UPDATER_THREADS, Runtime.getRuntime().availableProcessors()),
      updaterThreadFactory
    );
    this.nonPollingUpdaterPool = Executors.newCachedThreadPool(updaterThreadFactory);

    // Setup static updaters (but don't start them yet)
    for (GraphUpdater updater : staticUpdaters) {
      updater.setup(this);
      allUpdaters.put(updater, null); // null future until started
    }
  }

  /**
   * Start all currently registered updaters (both static and dynamic).
   */
  public void startUpdaters() {
    for (GraphUpdater updater : allUpdaters.keySet()) {
      if (allUpdaters.get(updater) != null) {
        continue; // Already started
      }
      startUpdater(updater);
    }
    reportReadinessForUpdaters();
  }

  /**
   * Internal method to start a single updater and track its future.
   */
  private void startUpdater(GraphUpdater updater) {
    Runnable runUpdater = () -> {
      try {
        updater.run();
      } catch (Exception e) {
        LOG.error("Error while running updater {}:", updater.getClass().getName(), e);
      }
    };

    if (updater instanceof PollingGraphUpdater pollingGraphUpdater) {
      LOG.info("Scheduling polling updater {}", updater);
      ScheduledFuture<?> future;
      if (pollingGraphUpdater.runOnlyOnce()) {
        future = pollingUpdaterPool.schedule(runUpdater, 0, TimeUnit.SECONDS);
      } else {
        future = pollingUpdaterPool.scheduleWithFixedDelay(
          runUpdater,
          0,
          pollingGraphUpdater.pollingPeriod().toSeconds(),
          TimeUnit.SECONDS
        );
      }
      allUpdaters.put(updater, future);
    } else {
      LOG.info("Starting new thread for updater {}", updater);
      nonPollingUpdaterPool.execute(runUpdater);
      // Non-polling updaters don't have ScheduledFuture, but we use
      // a sentinel value to mark them as started
      allUpdaters.put(updater, STARTED_SENTINEL);
    }
  }

  // Sentinel value for non-polling updaters that don't have ScheduledFuture
  private static final ScheduledFuture<?> STARTED_SENTINEL = new CompletedFuture();

  /**
   * Dynamically add and start an updater that was registered via UpdaterRegistry.
   * This method is called by the registry when a new updater is registered.
   */
  @Override
  public void onUpdaterRegistered(GraphUpdater updater) {
    LOG.info("Starting dynamically registered updater: {}", updater.getClass().getSimpleName());

    // Setup the updater (provide WriteToGraphCallback)
    updater.setup(this);

    // Start the updater (adds to allUpdaters map)
    startUpdater(updater);
  }

  /**
   * Dynamically stop and remove an updater that was unregistered via UpdaterRegistry.
   * This method is called by the registry when an updater is unregistered.
   */
  @Override
  public void onUpdaterUnregistered(GraphUpdater updater) {
    LOG.info("Stopping dynamically registered updater: {}", updater.getClass().getSimpleName());

    // Cancel scheduled execution if this is a polling updater
    ScheduledFuture<?> future = allUpdaters.remove(updater);
    if (future != null && future != STARTED_SENTINEL) {
      boolean cancelled = future.cancel(false); // Don't interrupt if running
      LOG.debug("Cancelled scheduled updater: {}", cancelled);
    }

    // Wait a moment for any in-flight operations to complete
    // (This is a simple approach; a more sophisticated implementation
    // could track per-updater Future objects from execute() calls)
    try {
      Thread.sleep(100);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }

    // Teardown the updater (cleanup resources, remove stations/parking/etc.)
    try {
      updater.teardown();
      LOG.debug("Updater teardown completed");
    } catch (Exception e) {
      LOG.error("Error during updater teardown", e);
    }
  }

  @Override
  public void onRegistryClosed() {
    LOG.info("UpdaterRegistry closed, no more dynamic updaters will be registered");
  }

  /**
   * Shutdown all updaters (called during OTP shutdown).
   */
  public void stop(boolean cancelRunningTasks) {
    LOG.info("Stopping updater manager with {} updaters.", numberOfUpdaters());

    // Shutdown executor pools
    if (cancelRunningTasks) {
      pollingUpdaterPool.shutdownNow();
      nonPollingUpdaterPool.shutdownNow();
    } else {
      pollingUpdaterPool.shutdown();
      nonPollingUpdaterPool.shutdown();
    }

    // Wait for termination
    try {
      boolean ok =
        pollingUpdaterPool.awaitTermination(15, TimeUnit.SECONDS) &&
        nonPollingUpdaterPool.awaitTermination(15, TimeUnit.SECONDS);
      if (!ok) {
        LOG.warn("Timeout waiting for updaters to finish.");
      }
    } catch (InterruptedException e) {
      LOG.warn("Interrupted while waiting for updaters to finish.");
    }

    // Teardown all updaters
    for (GraphUpdater updater : allUpdaters.keySet()) {
      try {
        updater.teardown();
      } catch (Exception e) {
        LOG.error("Error during updater teardown: {}", updater.getClass().getName(), e);
      }
    }
    allUpdaters.clear();

    // Shutdown scheduler
    scheduler.shutdownNow();
    try {
      boolean ok = scheduler.awaitTermination(30, TimeUnit.SECONDS);
      if (!ok) {
        LOG.warn("Timeout waiting for scheduled task to finish.");
      }
    } catch (InterruptedException e) {
      LOG.warn("Interrupted while waiting for scheduled task to finish.");
    }
    LOG.info("Stopped updater manager");
  }

  // GraphUpdaterStatus interface implementation

  @Override
  public int numberOfUpdaters() {
    return allUpdaters.size();
  }

  @Override
  public List<String> listUnprimedUpdaters() {
    return allUpdaters.keySet()
      .stream()
      .filter(Predicate.not(GraphUpdater::isPrimed))
      .map(GraphUpdater::getConfigRef)
      .collect(Collectors.toList());
  }

  @Override
  public Map<Integer, String> getUpdaterDescriptions() {
    Map<Integer, String> ret = new TreeMap<>();
    int i = 0;
    for (GraphUpdater updater : allUpdaters.keySet()) {
      ret.put(i++, updater.toString());
    }
    return ret;
  }

  public GraphUpdater getUpdater(int id) {
    if (id >= allUpdaters.size()) {
      return null;
    }
    // Convert map keyset to list to access by index
    List<GraphUpdater> updaterList = new ArrayList<>(allUpdaters.keySet());
    return updaterList.get(id);
  }

  public Class<?> getUpdaterClass(int id) {
    GraphUpdater updater = getUpdater(id);
    return updater == null ? null : updater.getClass();
  }

  public List<GraphUpdater> getUpdaterList() {
    return new ArrayList<>(allUpdaters.keySet());
  }

  private void reportReadinessForUpdaters() {
    Executors.newSingleThreadExecutor(
      new ThreadFactoryBuilder().setNameFormat("updater-ready").build()
    ).submit(() -> {
      boolean otpIsShuttingDown = false;

      while (!otpIsShuttingDown) {
        try {
          if (allUpdaters.keySet().stream().allMatch(GraphUpdater::isPrimed)) {
            LOG.info(
              "OTP UPDATERS INITIALIZED ({} updaters) - OTP {} is ready for routing!",
              allUpdaters.size(),
              OtpProjectInfo.projectInfo().version
            );
            return;
          }
          Thread.sleep(1000);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          otpIsShuttingDown = true;
          LOG.info("OTP is shutting down, cancelling wait for updaters readiness.");
        } catch (Exception e) {
          LOG.error(e.getMessage(), e);
        }
      }
    });
  }

  // Helper class for sentinel value
  private static class CompletedFuture implements ScheduledFuture<Void> {
    @Override public boolean cancel(boolean mayInterruptIfRunning) { return false; }
    @Override public boolean isCancelled() { return false; }
    @Override public boolean isDone() { return true; }
    @Override public Void get() { return null; }
    @Override public Void get(long timeout, TimeUnit unit) { return null; }
    @Override public long getDelay(TimeUnit unit) { return 0; }
    @Override public int compareTo(Delayed o) { return 0; }
  }
}
```

4. **VehicleRentalServiceDirectoryUpdater Uses Registry**:
```java
/**
 * Polls a GBFS v3 manifest.json file and dynamically registers/unregisters
 * VehicleRentalUpdater instances based on the datasets in the manifest.
 */
public class VehicleRentalServiceDirectoryUpdater extends PollingGraphUpdater {

  private final UpdaterRegistry registry;
  private final VertexLinker linker;
  private final VehicleRentalRepository repository;
  private final OtpHttpClientFactory httpClientFactory;
  private final String manifestUrl;

  public VehicleRentalServiceDirectoryUpdater(
    VehicleRentalServiceDirectoryUpdaterParameters parameters,
    UpdaterRegistry registry,
    VertexLinker linker,
    VehicleRentalRepository repository,
    OtpHttpClientFactory httpClientFactory
  ) {
    super(parameters);
    this.registry = registry;
    this.linker = linker;
    this.repository = repository;
    this.httpClientFactory = httpClientFactory;
    this.manifestUrl = parameters.url();
  }

  @Override
  protected void runPolling() throws Exception {
    LOG.debug("Fetching manifest from {}", manifestUrl);

    // Fetch and parse manifest
    Manifest manifest = fetchManifest(manifestUrl);

    if (manifest == null || manifest.getData() == null) {
      LOG.warn("No data in manifest from {}", manifestUrl);
      return;
    }

    // Get current system IDs from manifest
    Set<String> currentSystemIds = manifest.getData().getDatasets().stream()
      .map(Dataset::getSystemId)
      .collect(Collectors.toSet());

    // Get registered system IDs
    Set<String> registeredSystemIds = registry.getRegisteredKeys();

    // Find systems to remove (in registry but not in manifest)
    for (String systemId : registeredSystemIds) {
      if (!currentSystemIds.contains(systemId)) {
        LOG.info("System '{}' no longer in manifest, unregistering", systemId);
        registry.unregisterUpdater(systemId);
      }
    }

    // Find systems to add (in manifest but not in registry)
    for (Dataset dataset : manifest.getData().getDatasets()) {
      String systemId = dataset.getSystemId();

      if (!registeredSystemIds.contains(systemId)) {
        LOG.info("New system '{}' found in manifest, registering", systemId);

        try {
          VehicleRentalUpdater updater = createUpdater(dataset);
          registry.registerUpdater(systemId, updater);
        } catch (Exception e) {
          LOG.error("Failed to create updater for system '{}'", systemId, e);
        }
      }
    }
  }

  private VehicleRentalUpdater createUpdater(Dataset dataset) {
    // Select preferred GBFS version from dataset
    String gbfsUrl = selectGbfsUrl(dataset);

    // Create parameters
    GbfsVehicleRentalDataSourceParameters sourceParams =
      new GbfsVehicleRentalDataSourceParameters(
        gbfsUrl,
        null, // language
        false, // allowKeepingRentedVehicleAtDestination
        HttpHeaders.empty(),
        dataset.getSystemId(), // network
        true, // geofencingZones
        false, // overloadingAllowed
        List.of() // rentalPickupTypes (use defaults)
      );

    VehicleRentalUpdaterParameters params = new VehicleRentalUpdaterParameters(
      "service-directory:" + dataset.getSystemId(),
      Duration.ofMinutes(1), // polling frequency
      sourceParams
    );

    // Create data source
    VehicleRentalDataSource dataSource = VehicleRentalDataSourceFactory.create(
      sourceParams,
      httpClientFactory
    );

    // Create and return updater
    return new VehicleRentalUpdater(params, dataSource, linker, repository);
  }

  private String selectGbfsUrl(Dataset dataset) {
    // Prefer GBFS v3, fall back to v2
    return dataset.getVersions().stream()
      .filter(v -> v.getVersion().startsWith("3."))
      .map(Version::getUrl)
      .findFirst()
      .or(() -> dataset.getVersions().stream()
        .map(Version::getUrl)
        .findFirst())
      .orElseThrow(() -> new IllegalArgumentException(
        "No GBFS URL found for system: " + dataset.getSystemId()
      ));
  }

  @Override
  public void teardown() {
    // Unregister all systems we registered
    for (String systemId : registry.getRegisteredKeys()) {
      registry.unregisterUpdater(systemId);
    }
  }
}
```

5. **Update UpdaterConfigurator to Wire Everything Together**:
```java
public class UpdaterConfigurator {

  private void configure() {
    // Create the UpdaterRegistry
    UpdaterRegistry updaterRegistry = new UpdaterRegistry();

    // Create static updaters from config
    List<GraphUpdater> updaters = new ArrayList<>();
    updaters.addAll(createUpdatersFromConfig());

    // Create service directory updater if configured
    if (updatersParameters.getVehicleRentalServiceDirectoryFetcherParameters() != null) {
      var dirParams = updatersParameters.getVehicleRentalServiceDirectoryFetcherParameters();
      var dirUpdater = new VehicleRentalServiceDirectoryUpdater(
        dirParams,
        updaterRegistry, // Pass the registry
        linker,
        vehicleRentalRepository,
        otpHttpClientFactory
      );
      updaters.add(dirUpdater);
    }

    // Create GraphUpdaterManager
    TimetableSnapshot timetableSnapshotBuffer = snapshotManager.getTimetableSnapshotBuffer();
    GraphUpdaterManager updaterManager = new GraphUpdaterManager(
      new DefaultRealTimeUpdateContext(graph, timetableRepository, timetableSnapshotBuffer),
      updaters
    );

    // Register the manager as a listener to the registry
    updaterRegistry.addListener(updaterManager);

    // Configure timetable snapshot flushing
    configureTimetableSnapshotFlush(updaterManager, snapshotManager);

    // Start all updaters (including service directory updater)
    updaterManager.startUpdaters();

    // Attach to repository
    if (updaterManager.numberOfUpdaters() > 0) {
      timetableRepository.setUpdaterManager(updaterManager);
      timetableRepository.setUpdaterRegistry(updaterRegistry); // Also store registry
    } else {
      updaterManager.stop();
    }
  }
}
```

### Architecture Diagrams

#### 1. Component Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                     UpdaterConfigurator                          │
│  (Creates and wires components together during initialization)  │
└───────────┬─────────────────────────────────┬───────────────────┘
            │                                 │
            │ creates                         │ creates
            ▼                                 ▼
┌──────────────────────┐           ┌─────────────────────────┐
│  UpdaterRegistry     │           │  GraphUpdaterManager    │
│  (Concrete Service)  │           │                         │
├──────────────────────┤           ├─────────────────────────┤
│ - registeredUpdaters │           │ - allUpdaters           │
│   ConcurrentHashMap  │           │   ConcurrentHashMap     │
│   <String, Updater>  │           │   <Updater, Future>     │
│                      │           │                         │
│ - listeners          │           │ - scheduler             │
│   CopyOnWriteArrayList│          │ - pollingUpdaterPool    │
│   <Listener>         │           │ - nonPollingUpdaterPool │
│                      │           │                         │
│ + registerUpdater()  │           │ + onUpdaterRegistered() │
│ + unregisterUpdater()│           │ + onUpdaterUnregistered()│
│ + addListener()      │           │ + execute()             │
└──────────┬───────────┘           └─────────▲───────────────┘
           │                                 │
           │                                 │ implements
           │ notifies via                    │ UpdaterRegistryListener
           └─────────────────────────────────┘


┌─────────────────────────────────────────────────────────────────┐
│       VehicleRentalServiceDirectoryUpdater                      │
│       (Polls manifest.json, registers/unregisters updaters)     │
├─────────────────────────────────────────────────────────────────┤
│  - registry: UpdaterRegistry                                    │
│  - linker: VertexLinker                                         │
│  - repository: VehicleRentalRepository                          │
│  - manifestUrl: String                                          │
│                                                                 │
│  + runPolling()     // Fetches manifest, diffs, registers       │
│  + teardown()       // Unregisters all systems                  │
└────────────────────┬────────────────────────────────────────────┘
                     │
                     │ registers/unregisters via
                     ▼
         ┌──────────────────────┐
         │  UpdaterRegistry     │
         └──────────────────────┘


┌──────────────────────────────────────────────────────────────────┐
│                   UpdaterRegistryListener                        │
│                   (Interface with default methods)               │
├──────────────────────────────────────────────────────────────────┤
│  + onUpdaterRegistered(updater)      // Notification: start     │
│  + onUpdaterUnregistered(updater)    // Notification: stop      │
│  + onRegistryClosed()                // Notification: cleanup    │
└──────────────────────────────────────────────────────────────────┘
```

#### 2. Sequence Diagram: Adding a New System

```
ServiceDirectoryUpdater    UpdaterRegistry    GraphUpdaterManager (Listener)
         |                       |                       |
         |  runPolling()         |                       |
         |  (detects new system) |                       |
         |                       |                       |
         |  registerUpdater(     |                       |
         |    "oslo-by-sykkel",  |                       |
         |    updater)           |                       |
         |---------------------->|                       |
         |                       |                       |
         |                       | Store in map:         |
         |                       | registeredUpdaters    |
         |                       | .put("oslo-by-        |
         |                       |   sykkel", updater)   |
         |                       |                       |
         |                       | Notify listeners:     |
         |                       | onUpdaterRegistered   |
         |                       |   (updater)           |
         |                       |---------------------->|
         |                       |                       |
         |                       |                       | updater.setup(this)
         |                       |                       | (provide callback)
         |                       |                       |
         |                       |                       | startUpdater(updater)
         |                       |                       | - Create Runnable
         |                       |                       | - Schedule with pool
         |                       |                       | - Store Future
         |                       |                       |
         |                       |                       | allUpdaters.put(
         |                       |                       |   updater, future)
         |                       |                       |
         |<---------------------------------------Success|
         |                       |                       |
         |                       |                       | Updater now running
         |                       |                       | in pollingUpdaterPool
         |                       |                       |
```

#### 3. Sequence Diagram: Removing a System

```
ServiceDirectoryUpdater    UpdaterRegistry    GraphUpdaterManager (Listener)
         |                       |                       |
         |  runPolling()         |                       |
         |  (detects removed     |                       |
         |   system)             |                       |
         |                       |                       |
         |  unregisterUpdater(   |                       |
         |    "oslo-by-sykkel")  |                       |
         |---------------------->|                       |
         |                       |                       |
         |                       | Remove from map:      |
         |                       | registeredUpdaters    |
         |                       | .remove("oslo-by-     |
         |                       |   sykkel")            |
         |                       | → returns updater     |
         |                       |                       |
         |                       | Notify listeners:     |
         |                       | onUpdaterUnregistered |
         |                       |   (updater)           |
         |                       |---------------------->|
         |                       |                       |
         |                       |                       | future = allUpdaters
         |                       |                       |   .remove(updater)
         |                       |                       |
         |                       |                       | future.cancel(false)
         |                       |                       | (stop scheduling)
         |                       |                       |
         |                       |                       | Thread.sleep(100)
         |                       |                       | (wait for in-flight)
         |                       |                       |
         |                       |                       | updater.teardown()
         |                       |                       | - Submit empty update
         |                       |                       | - Remove all stations
         |                       |                       | - Cleanup resources
         |                       |                       |
         |<---------------------------------------Success|
         |                       |                       |
         |                       |                       | Updater stopped,
         |                       |                       | stations removed
         |                       |                       |
```

#### 4. Data Structure: allUpdaters Map

```
GraphUpdaterManager
│
└─── allUpdaters: ConcurrentHashMap<GraphUpdater, ScheduledFuture<?>>
     │
     ├─── Key: Static GTFS-RT Updater      → Value: ScheduledFuture<?> (polling)
     │
     ├─── Key: Static Vehicle Parking      → Value: CompletedFuture (sentinel, non-polling)
     │
     ├─── Key: ServiceDirectoryUpdater     → Value: ScheduledFuture<?> (polling)
     │    │
     │    └─── Manages dynamic updaters below ───┐
     │                                            │
     ├─── Key: Oslo Rental Updater (dynamic) ◄───┘ Value: ScheduledFuture<?> (polling)
     │
     ├─── Key: Bergen Rental Updater (dynamic) ◄─┘ Value: ScheduledFuture<?> (polling)
     │
     └─── Key: Voi Trondheim Updater (dynamic) ◄─┘ Value: ScheduledFuture<?> (polling)


Benefits of Single Map (NO separate updaterList):
─────────────────────────────────────────────────
✓ Single source of truth for all updaters
✓ Natural add/remove operations (map.put/map.remove)
✓ Thread-safe via ConcurrentHashMap
✓ No duplication between list and map
✓ No state divergence possible
✓ Future tracking built-in for cancellation
```

#### 5. Initialization Sequence

```
UpdaterConfigurator         UpdaterRegistry    GraphUpdaterManager
        |                         |                    |
        | Create registry         |                    |
        |------------------------>|                    |
        |                         |                    |
        | Create static updaters  |                    |
        | (GTFS-RT, parking, etc.)|                    |
        |                         |                    |
        | Create ServiceDirectory |                    |
        | updater with registry   |                    |
        |                         |                    |
        | Create manager with     |                    |
        | all updaters            |                    |
        |-------------------------------------------->|
        |                         |                    |
        |                         |                    | Store static updaters
        |                         |                    | in allUpdaters map
        |                         |                    | (not started yet)
        |                         |                    |
        | addListener(manager)    |                    |
        |------------------------>|                    |
        |                         |                    |
        |                         | Store listener     |
        |                         | in listeners list  |
        |                         |                    |
        | startUpdaters()         |                    |
        |-------------------------------------------->|
        |                         |                    |
        |                         |                    | For each updater in
        |                         |                    | allUpdaters.keySet():
        |                         |                    |   - startUpdater()
        |                         |                    |   - Schedule/execute
        |                         |                    |   - Store Future
        |                         |                    |
        |                         |                    | ServiceDirectory updater
        |                         |                    | starts polling manifest
        |                         |                    |
        |<------------------------------------Ready   |
        |                         |                    |
```

**Pros**:
- **Eliminates circular dependency**: ServiceDirectoryUpdater depends on UpdaterRegistry, GraphUpdaterManager depends on UpdaterRegistry (via listener interface). No circular reference.
- **Clean separation of concerns**:
  - UpdaterRegistry manages registration state
  - GraphUpdaterManager manages execution lifecycle
  - ServiceDirectoryUpdater manages manifest polling
- **Follows OTP patterns**: Uses listener pattern similar to ParetoSetEventListener, LifeCycleSubscriptions
- **More testable**: Can test each component independently with mock listeners
- **Extensible**: Other updaters could also use the registry for dynamic management
- **Thread-safe**: ConcurrentHashMap + CopyOnWriteArrayList handle concurrent access
- **Better encapsulation**: Manager doesn't need to expose add/remove methods publicly
- **Observable**: Can add multiple listeners (e.g., for metrics, logging, debugging)

**Cons**:
- **More components**: Introduces a new service class and listener interface
- **Slight indirection**: Events flow through registry rather than direct method calls
- **Complexity**: More classes to maintain and understand
- **~~Memory overhead~~**: **ELIMINATED** - By removing `updaterList` and using `allUpdaters` map, there's no duplication
- **~~Potential inconsistency~~**: **MITIGATED** - Single source of truth in `allUpdaters` map eliminates state divergence

**Key Improvement in Revised Design**:
The original Option 2B maintained both `updaterList` (ArrayList) and `scheduledUpdaters` (Map), creating duplication. **The revised design removes `updaterList` entirely** and uses a single `allUpdaters` ConcurrentHashMap where:
- **Keys**: GraphUpdater instances (both static and dynamic)
- **Values**: ScheduledFuture<?> for polling updaters, or sentinel value for non-polling updaters

This change:
1. **Eliminates duplication**: One data structure tracks all updaters
2. **Simplifies lifecycle**: Map naturally supports add/remove operations
3. **Maintains thread safety**: ConcurrentHashMap provides built-in concurrency
4. **Reduces memory**: No redundant list storage
5. **Single source of truth**: The map IS the authoritative updater registry for the manager

**Implementation Notes**:
- Uses `CopyOnWriteArrayList` for listeners to allow safe iteration during notification
- Uses `ConcurrentHashMap` for registered updaters to handle concurrent access
- Listener methods include try-catch to prevent one failing listener from affecting others
- Registry can be closed to prevent further registrations (cleanup on shutdown)
- Follows OTP logging conventions with appropriate log levels
- Compatible with existing updater lifecycle (setup → run → teardown)

### Option 3: Poll-Based Coordination (Inversion of Control)

**Changes Required**:

1. **ServiceDirectoryUpdater maintains desired state**:
```java
public class VehicleRentalServiceDirectoryUpdater extends PollingGraphUpdater {
  private volatile Set<VehicleRentalUpdaterParameters> desiredUpdaters = Set.of();

  @Override
  protected void runPolling() {
    Manifest manifest = fetchManifest();
    desiredUpdaters = convertManifestToParameters(manifest);
  }

  public Set<VehicleRentalUpdaterParameters> getDesiredUpdaters() {
    return desiredUpdaters;
  }
}
```

2. **GraphUpdaterManager polls for changes**:
```java
public void reconcileServiceDirectory(VehicleRentalServiceDirectoryUpdater dirUpdater) {
  Set<VehicleRentalUpdaterParameters> desired = dirUpdater.getDesiredUpdaters();
  // Diff against current vehicle rental updaters
  // Add/remove as needed
}
```

3. **Schedule reconciliation periodically**:
```java
scheduler.scheduleWithFixedDelay(
  () -> reconcileServiceDirectory(dirUpdater),
  0,
  60,  // Every 60 seconds
  TimeUnit.SECONDS
);
```

**Pros**:
- No circular dependency
- ServiceDirectoryUpdater is simpler (just maintains state)
- Manager has full control over lifecycle

**Cons**:
- More complex reconciliation logic in manager
- Two polling loops (service directory polls manifest, manager polls service directory)
- Delayed reaction to changes (up to reconciliation period)

### Option 4: Event Bus Pattern

**Changes Required**:

1. **Create event classes**:
```java
public sealed interface UpdaterEvent {
  record UpdaterAdded(VehicleRentalUpdaterParameters params) implements UpdaterEvent {}
  record UpdaterRemoved(String systemId) implements UpdaterEvent {}
}
```

2. **ServiceDirectoryUpdater publishes events**:
```java
public class VehicleRentalServiceDirectoryUpdater extends PollingGraphUpdater {
  private final BlockingQueue<UpdaterEvent> eventQueue;

  @Override
  protected void runPolling() {
    // ... diff logic ...
    for (String removedSystemId : systemsToRemove) {
      eventQueue.offer(new UpdaterEvent.UpdaterRemoved(removedSystemId));
    }
    for (VehicleRentalUpdaterParameters newParams : systemsToAdd) {
      eventQueue.offer(new UpdaterEvent.UpdaterAdded(newParams));
    }
  }
}
```

3. **GraphUpdaterManager consumes events**:
```java
scheduler.scheduleWithFixedDelay(
  () -> processUpdaterEvents(),
  0,
  1,
  TimeUnit.SECONDS
);

private void processUpdaterEvents() {
  UpdaterEvent event;
  while ((event = eventQueue.poll()) != null) {
    switch (event) {
      case UpdaterEvent.UpdaterAdded(var params) -> addVehicleRentalUpdater(params);
      case UpdaterEvent.UpdaterRemoved(var systemId) -> removeVehicleRentalUpdater(systemId);
    }
  }
}
```

**Pros**:
- Decoupled communication
- No circular dependency
- Can be tested independently
- Extensible to other dynamic updater types

**Cons**:
- More complex architecture
- Need to manage event queue
- Asynchronous processing (events processed on delay)

## Comparison of Approaches

### Summary Table

| Aspect | Option 1: Extend Manager | Option 2A: Registry Interface | Option 2B: Registry Service + Listener | Option 3: Poll-Based | Option 4: Event Bus |
|--------|-------------------------|-------------------------------|----------------------------------------|----------------------|---------------------|
| **Circular Dependency** | Yes (ServiceDirectoryUpdater → Manager) | Yes (hidden by interface) | **No** (both depend on registry) | **No** (manager polls updater) | **No** (queue mediates) |
| **Separation of Concerns** | Poor (manager does everything) | Fair (interface abstraction) | **Excellent** (registry, listener, manager separate) | Good (updater just maintains state) | Good (events decouple) |
| **Follows OTP Patterns** | Moderate (direct method calls) | Moderate (interface segregation) | **Excellent** (listener pattern like Raptor) | Fair (custom pattern) | Fair (event queue pattern) |
| **Testability** | Fair (need to mock manager) | Good (can mock interface) | **Excellent** (mock listener or registry) | Good (mock state) | Good (mock queue) |
| **Complexity** | **Low** (fewest components) | Low-Medium (one interface) | Medium-High (3 new components) | Medium (reconciliation logic) | Medium-High (event system) |
| **Thread Safety** | Manual synchronization needed | Manual synchronization needed | **Built-in** (ConcurrentHashMap, CopyOnWriteArrayList) | Manual synchronization needed | Queue handles concurrency |
| **Extensibility** | Limited (manager-specific) | Good (interface can evolve) | **Excellent** (multiple listeners, other updaters can use) | Limited (ServiceDirectory-specific) | Good (other event types) |
| **Performance** | **Best** (direct calls) | **Best** (direct calls via interface) | Good (listener iteration overhead) | Good (polling delay) | Fair (queue + polling overhead) |
| **Observable** | No (direct method calls) | No (direct method calls) | **Yes** (multiple listeners for metrics, logging, debugging) | Limited (state queries) | Yes (event inspection) |
| **Memory Overhead** | **Minimal** (only manager state) | **Minimal** (only manager state) | **Low** (registry map + listener list, NO manager list duplication) | Low (desired state set) | Medium (event queue + state) |
| **Delay in Updates** | **None** (immediate) | **None** (immediate) | **None** (immediate via notification) | Up to reconciliation period | Up to event processing period |

### Detailed Comparison

#### Code Changes Required

**Option 1**: Minimal changes to existing classes
- Modify `GraphUpdaterManager` (~50 lines)
- Create `VehicleRentalServiceDirectoryUpdater` (~150 lines)
- Modify `UpdaterConfigurator` (~10 lines)
- **Total: ~210 lines, 3 files modified**

**Option 2A**: Similar to Option 1 with interface
- Create `UpdaterRegistry` interface (~10 lines)
- Modify `GraphUpdaterManager` to implement interface (~50 lines)
- Create `VehicleRentalServiceDirectoryUpdater` (~150 lines)
- Modify `UpdaterConfigurator` (~10 lines)
- **Total: ~220 lines, 3 files modified, 1 new interface**

**Option 2B**: Most new code but cleanest architecture
- Create `UpdaterRegistryListener` interface (~20 lines)
- Create `UpdaterRegistry` service class (~150 lines)
- Modify `GraphUpdaterManager` to implement listener (~100 lines)
- Create `VehicleRentalServiceDirectoryUpdater` (~150 lines)
- Modify `UpdaterConfigurator` (~20 lines)
- **Total: ~440 lines, 3 files modified, 2 new classes**

**Option 3**: Moderate changes with custom pattern
- Create state holder in `VehicleRentalServiceDirectoryUpdater` (~150 lines)
- Add reconciliation logic to `GraphUpdaterManager` (~100 lines)
- Add reconciliation scheduling to `UpdaterConfigurator` (~20 lines)
- **Total: ~270 lines, 3 files modified**

**Option 4**: Complex event system
- Create event classes (~50 lines)
- Create event queue and processing (~100 lines)
- Create `VehicleRentalServiceDirectoryUpdater` (~150 lines)
- Modify `UpdaterConfigurator` (~30 lines)
- **Total: ~330 lines, 3 files modified, new event classes**

#### Alignment with OTP Patterns

**Option 2B** most closely follows existing OTP patterns:
- **Listener pattern**: Like `ParetoSetEventListener` in Raptor
- **Lifecycle subscriptions**: Similar to `LifeCycleSubscriptions`/`LifeCycleEventPublisher` pattern
- **Concurrent collections**: `CopyOnWriteArrayList` for listeners (like Raptor), `ConcurrentHashMap` for state
- **Null-safe listeners**: Default method implementations allow partial implementation
- **Error handling**: Try-catch around listener notifications to prevent cascade failures
- **Separation of subscription and notification**: Registry collects registrations, listeners get notified

**Option 1** follows simpler OTP patterns:
- Direct method calls like `WriteToGraphCallback.execute()`
- Synchronized blocks for thread safety (common in manager classes)
- Dependency injection via constructor or setter

#### Testing Considerations

**Option 2B** is most testable:
```java
// Test registry independently
UpdaterRegistry registry = new UpdaterRegistry();
registry.registerUpdater("test", mockUpdater);
assertEquals(1, registry.size());

// Test listener independently with mock
UpdaterRegistryListener mockListener = mock(UpdaterRegistryListener.class);
registry.addListener(mockListener);
registry.registerUpdater("test", mockUpdater);
verify(mockListener).onUpdaterRegistered(mockUpdater);

// Test ServiceDirectoryUpdater with mock registry
UpdaterRegistry mockRegistry = mock(UpdaterRegistry.class);
ServiceDirectoryUpdater updater = new ServiceDirectoryUpdater(..., mockRegistry, ...);
updater.runPolling();
verify(mockRegistry).registerUpdater(eq("system-id"), any());
```

**Option 1** requires more complex mocking:
```java
// Must mock or partially implement GraphUpdaterManager
GraphUpdaterManager mockManager = mock(GraphUpdaterManager.class);
ServiceDirectoryUpdater updater = new ServiceDirectoryUpdater(...);
updater.setManager(mockManager);
updater.runPolling();
verify(mockManager).addUpdater(any());
```

#### Real-World Usage Scenarios

**Scenario 1: Small deployment (1-5 systems)**
- All options perform similarly
- Option 1 simplest to understand and maintain
- Memory/performance differences negligible

**Scenario 2: Medium deployment (10-50 systems)**
- Option 2B's observability becomes valuable (track additions/removals)
- Option 1's direct approach still works well
- Thread safety more important (Option 2B handles this)

**Scenario 3: Large deployment (100+ systems)**
- Option 2B's extensibility allows multiple listeners for metrics
- Pool exhaustion concerns with Option 1 (fixed size)
- Memory overhead of Option 2B justified by operational visibility

**Scenario 4: Multiple dynamic updater types (future)**
- Option 2B easily extends to vehicle parking, SIRI, etc.
- Option 1 requires duplicating add/remove logic
- Registry becomes central point for all dynamic management

## Recommended Approach

### Primary Recommendation: **Option 2B (Registry Service with Listener Pattern)**

Despite being the most complex option, Option 2B is recommended for production use because:

1. **Eliminates architectural smell**: No circular dependencies between updater and manager
2. **Best alignment with OTP patterns**: Follows established listener/lifecycle patterns from Raptor
3. **Excellent testability**: Each component can be tested independently with simple mocks
4. **Built-in thread safety**: Uses proven concurrent collections from Java stdlib
5. **Future-proof**: Easily extends to other dynamic updater types (parking, SIRI, etc.)
6. **Observability**: Multiple listeners enable metrics, logging, debugging without modifying core logic
7. **Clean separation**: Registry manages registration state, manager manages execution, updater manages polling

**When to use Option 2B**:
- Production deployments with operational requirements
- Multiple dynamic updater types planned
- Need for metrics/monitoring of dynamic registration
- Long-term maintainability is priority

### Alternative Recommendation: **Option 1 (Extend GraphUpdaterManager)**

For quicker implementation or simpler deployments, Option 1 is acceptable:

1. **Fastest to implement**: Fewest lines of code, minimal new components
2. **Easier to understand**: Direct method calls, no indirection
3. **Good enough**: Works fine for small-medium deployments
4. **Proven pattern**: Similar to existing OTP code patterns

**When to use Option 1**:
- Prototype or proof-of-concept
- Small deployments (< 20 systems)
- Short-term solution or experimental feature
- Development time is critical

### Implementation Roadmap

If choosing Option 2B (recommended), implement in phases:

**Phase 1: Core Infrastructure**
1. Create `UpdaterRegistryListener` interface
2. Create `UpdaterRegistry` service class
3. Add unit tests for registry

**Phase 2: Manager Integration**
1. Implement `UpdaterRegistryListener` in `GraphUpdaterManager`
2. Add tracking of `ScheduledFuture` handles
3. Test dynamic add/remove with mock updaters

**Phase 3: Service Directory Updater**
1. Create `VehicleRentalServiceDirectoryUpdater`
2. Wire components together in `UpdaterConfigurator`
3. Integration tests with manifest parsing

**Phase 4: Production Readiness**
1. Add metrics listener for monitoring
2. Handle edge cases (failures, retries, rate limiting)
3. Documentation and configuration examples
4. Performance testing with large manifests

## Open Questions

1. **Thread safety**: How do we ensure thread-safe iteration over `updaterList` when it can be modified? Should we use `CopyOnWriteArrayList` or explicit locking?

2. **In-flight operations**: When removing an updater, how do we wait for in-flight graph writes to complete? The current `Future<?>` from `execute()` could be tracked per-updater.

3. **Configuration**: Should the ServiceDirectoryUpdater have its own configuration for polling frequency, or should it use the manifest's `ttl` field?

4. **Error handling**: What happens if adding an updater fails (e.g., network unreachable)? Should we retry? How many times?

5. **Metrics**: Should we track how many updaters are dynamically added/removed for monitoring?

6. **Pool exhaustion**: Can the fixed-size polling updater pool handle a large number of dynamically added updaters? Should we impose a maximum limit?

7. **Startup order**: Should the ServiceDirectoryUpdater run once before other updaters start to ensure all systems are available from the beginning?

8. **Compatibility**: Does this change affect serialization or backward compatibility with existing router-config.json files?

## Related Documentation

- GBFS v3 specification: https://github.com/MobilityData/gbfs/blob/v3.0/gbfs.md
- OTP Architecture docs: `ARCHITECTURE.md` in repository root
- Updater documentation: `doc/user/` directory

