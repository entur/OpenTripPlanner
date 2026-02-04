---
date: 2025-12-03T10:07:24+01:00
researcher: Claude
git_commit: b1b7752367612afcb9147dcd342d43991b0f0e96
branch: poc/sandbox-geofencing-zones-in-graph-builder
repository: OpenTripPlanner
topic: "GBFS Geofencing Build-Time Sandbox Feature - Phase 3 Ready"
tags: [implementation, sandbox, geofencing, gbfs, graph-builder, vehicle-rental, dagger]
status: complete
last_updated: 2025-12-03
last_updated_by: Claude
type: implementation_strategy
---

# Handoff: GBFS Geofencing Build-Time - Phase 2 Complete, Ready for Phase 3

## Task(s)

| Task | Status |
|------|--------|
| Phase 1: OTPFeature flag + configuration infrastructure | ✅ Completed (previous session) |
| Phase 2: Repository interface, implementation, and Dagger module | ✅ Completed (this session) |
| Phase 3: Graph builder module implementation | 🔲 Ready to start |
| Phase 4: Serialization and application startup wiring | 🔲 Planned |
| Phase 5: Tests | 🔲 Planned |
| Phase 6: Documentation | 🔲 Planned |

**Current Phase**: Phase 2 just completed. Phase 3 is next.

## Critical References

1. **Implementation Plan**: `thoughts/shared/plans/2025-12-02-gbfs-geofencing-zones-graph-build-sandbox.md` - Contains detailed step-by-step implementation for all phases
2. **Research Document**: `thoughts/shared/research/2025-12-01-gbfs-geofencing-zones-graph-build-sandbox.md` - Background research on existing patterns

## Recent changes

Files created in this session:
- `application/src/ext/java/org/opentripplanner/ext/gbfsgeofencing/GbfsGeofencingRepository.java` - Public interface extending Serializable
- `application/src/ext/java/org/opentripplanner/ext/gbfsgeofencing/internal/DefaultGbfsGeofencingRepository.java` - Implementation with zone storage and edge count tracking
- `application/src/ext/java/org/opentripplanner/ext/gbfsgeofencing/configure/GbfsGeofencingRepositoryModule.java` - Dagger @Module returning null when feature disabled

Files modified:
- `application/src/main/java/org/opentripplanner/standalone/configure/LoadApplicationFactory.java:15-16` - Added imports for GbfsGeofencingRepository and module
- `application/src/main/java/org/opentripplanner/standalone/configure/LoadApplicationFactory.java:46` - Added GbfsGeofencingRepositoryModule.class to modules list
- `application/src/main/java/org/opentripplanner/standalone/configure/LoadApplicationFactory.java:79-81` - Added gbfsGeofencingRepository() interface method

## Learnings

1. **Dagger Module Pattern**: The `EmissionRepositoryModule` at `ext/emission/configure/EmissionRepositoryModule.java` is the template. Uses `jakarta.inject.Singleton`, not `javax.inject`. Repository modules return `null` when feature is disabled (see `EmpiricalDelayRepositoryModule` pattern).

2. **Feature Flag Check**: Use `OTPFeature.GbfsGeofencingBuildTime.isOn()` to conditionally create repository.

3. **LoadApplicationFactory Registration**: Modules go in `@Component(modules={...})` list, interface methods expose the provided objects. Nullable repositories use `@Nullable` annotation.

4. **Imports auto-sorted**: The linter/prettier reorders imports alphabetically, so don't worry about placement.

## Artifacts

- `application/src/ext/java/org/opentripplanner/ext/gbfsgeofencing/GbfsGeofencingRepository.java` (new)
- `application/src/ext/java/org/opentripplanner/ext/gbfsgeofencing/internal/DefaultGbfsGeofencingRepository.java` (new)
- `application/src/ext/java/org/opentripplanner/ext/gbfsgeofencing/configure/GbfsGeofencingRepositoryModule.java` (new)
- `application/src/main/java/org/opentripplanner/standalone/configure/LoadApplicationFactory.java` (modified)

Files from Phase 1 (already exist):
- `application/src/main/java/org/opentripplanner/framework/application/OTPFeature.java:156-160` - Feature flag
- `application/src/ext/java/org/opentripplanner/ext/gbfsgeofencing/parameters/GbfsGeofencingFeedParameters.java`
- `application/src/ext/java/org/opentripplanner/ext/gbfsgeofencing/parameters/GbfsGeofencingParameters.java`
- `application/src/ext/java/org/opentripplanner/ext/gbfsgeofencing/config/GbfsGeofencingConfig.java`
- `application/src/main/java/org/opentripplanner/standalone/config/BuildConfig.java:31-32,181,605` - Config integration

## Action Items & Next Steps

### Phase 3: Create Graph Builder Module

1. **Create `GbfsGeofencingGraphBuilder`** at `application/src/ext/java/org/opentripplanner/ext/gbfsgeofencing/internal/graphbuilder/GbfsGeofencingGraphBuilder.java`
   - Implement `GraphBuilderModule` interface
   - Use `GbfsFeedLoaderAndMapper` to load GBFS feeds
   - Use `GeofencingVertexUpdater` to apply zones to edges
   - See implementation plan lines 454-563 for full code

2. **Make `GeofencingVertexUpdater` public** at `application/src/main/java/org/opentripplanner/updater/vehicle_rental/GeofencingVertexUpdater.java:35`
   - Change from package-private to `public class`

3. **Create `GbfsGeofencingGraphBuilderModule`** at `application/src/ext/java/org/opentripplanner/ext/gbfsgeofencing/configure/GbfsGeofencingGraphBuilderModule.java`
   - Dagger @Module providing the graph builder
   - Returns null if repository is null or no feeds configured

4. **Register in `GraphBuilderFactory`** at `application/src/main/java/org/opentripplanner/graph_builder/module/configure/GraphBuilderFactory.java`
   - Add module to @Component
   - Add interface method for graph builder
   - Add @BindsInstance for repository in Builder

5. **Wire into `GraphBuilder.create()`** at `application/src/main/java/org/opentripplanner/graph_builder/GraphBuilder.java`
   - Add repository parameter
   - Pass to builder chain
   - Register module with `addModuleOptional()`

## Other Notes

### Key Classes to Reference

- **Existing zone application logic**: `updater/vehicle_rental/GeofencingVertexUpdater.java:47-91` - The `applyGeofencingZones()` method
- **GBFS loading**: `updater/vehicle_rental/datasources/gbfs/GbfsFeedLoaderAndMapper.java` - Use `.update()` then `.getGeofencingZones()`
- **Graph builder pattern**: `ext/emission/internal/graphbuilder/EmissionGraphBuilder.java` - Template for sandbox graph builders
- **Module wiring**: `graph_builder/GraphBuilder.java:176` - Where sandbox modules are added with `addModuleOptional()`

### Build Verification Commands

```bash
mvn compile -DskipTests    # Full compile
mvn prettier:check -pl application   # Check formatting
```

Both passed after Phase 2 completion.
