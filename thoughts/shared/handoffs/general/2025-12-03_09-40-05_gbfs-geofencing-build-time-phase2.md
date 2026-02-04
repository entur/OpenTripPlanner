---
date: 2025-12-03T09:40:05+0100
researcher: Claude
git_commit: c577aca043408a437ab06e600bcdc3a0c6b62183
branch: poc/sandbox-geofencing-zones-in-graph-builder
repository: OpenTripPlanner
topic: "GBFS Geofencing Zones at Graph Build Time - Phase 2 Preparation"
tags: [implementation, sandbox, gbfs, geofencing, graph-builder, vehicle-rental]
status: complete
last_updated: 2025-12-03
last_updated_by: Claude
type: implementation_strategy
---

# Handoff: GBFS Geofencing Build Time - Phase 1 Complete, Ready for Phase 2

## Task(s)

**Implementing a POC sandbox feature for loading GBFS geofencing zones at graph build time.**

| Phase | Status | Description |
|-------|--------|-------------|
| Phase 1 | ✅ Complete | Create OTPFeature flag and configuration |
| Phase 2 | 🔜 Next | Create Repository and Dagger Module |
| Phase 3 | Pending | Create Graph Builder Module |
| Phase 4 | Pending | Wire Serialization and Application Startup |
| Phase 5 | Pending | Add Tests |
| Phase 6 | Pending | Documentation |

## Critical References

1. **Implementation Plan**: `thoughts/shared/plans/2025-12-02-gbfs-geofencing-zones-graph-build-sandbox.md` - The detailed implementation plan with code snippets for each phase
2. **Research Document**: `thoughts/shared/research/2025-12-01-gbfs-geofencing-zones-graph-build-sandbox.md` - Background research on how to implement this feature
3. **Reference Pattern**: `application/src/ext/java/org/opentripplanner/ext/emission/` - The Emission sandbox feature provides the template pattern to follow

## Recent changes

Phase 1 implementation added the following:

- `application/src/main/java/org/opentripplanner/framework/application/OTPFeature.java:156-160` - Added `GbfsGeofencingBuildTime` sandbox feature flag
- `application/src/ext/java/org/opentripplanner/ext/gbfsgeofencing/parameters/GbfsGeofencingFeedParameters.java` - New record for GBFS feed parameters
- `application/src/ext/java/org/opentripplanner/ext/gbfsgeofencing/parameters/GbfsGeofencingParameters.java` - New record for the list of feeds
- `application/src/ext/java/org/opentripplanner/ext/gbfsgeofencing/config/GbfsGeofencingConfig.java` - Config mapper for build-config.json
- `application/src/main/java/org/opentripplanner/standalone/config/BuildConfig.java:31-32,181,605` - Imports, field, and parsing for gbfsGeofencing config

## Learnings

1. **Sandbox Feature Pattern**: OTP sandbox features follow a consistent pattern:
   - OTPFeature enum flag (disabled by default, `sandbox=true`)
   - Parameters classes in `ext/<feature>/parameters/`
   - Config mapper in `ext/<feature>/config/`
   - Repository module in `ext/<feature>/configure/`
   - Graph builder module in `ext/<feature>/internal/graphbuilder/`

2. **Config Parsing**: Use `NodeAdapter.asObjects(List.of(), mapper)` for optional lists (returns empty list if section missing), not `asObjects(mapper)` which requires the section to exist.

3. **Existing Infrastructure to Reuse**:
   - `GeofencingVertexUpdater` at `updater/vehicle_rental/GeofencingVertexUpdater.java:35` - needs to be made `public` (currently package-private)
   - `GbfsFeedLoaderAndMapper` for loading GBFS feeds
   - `GeofencingZone` model record already exists

4. **Serialization Note**: `RentalRestrictionExtension` objects applied to vertices ARE serialized with the graph automatically since they're stored on the `Vertex` objects.

## Artifacts

- `thoughts/shared/plans/2025-12-02-gbfs-geofencing-zones-graph-build-sandbox.md` - Implementation plan (Phase 1 success criteria now checked off)
- `application/src/ext/java/org/opentripplanner/ext/gbfsgeofencing/` - New package with Phase 1 classes

## Action Items & Next Steps

**Phase 2: Create Repository and Dagger Module** (see plan lines 253-403)

1. Create `GbfsGeofencingRepository` interface at `ext/gbfsgeofencing/GbfsGeofencingRepository.java`
2. Create `DefaultGbfsGeofencingRepository` implementation at `ext/gbfsgeofencing/internal/DefaultGbfsGeofencingRepository.java`
3. Create `GbfsGeofencingRepositoryModule` Dagger module at `ext/gbfsgeofencing/configure/GbfsGeofencingRepositoryModule.java`
4. Register module in `LoadApplicationFactory.java` and add interface method

**After Phase 2, continue with Phases 3-6 as described in the implementation plan.**

## Other Notes

- The plan was created based on the Emission sandbox feature pattern - refer to `ext/emission/` for guidance when uncertain
- Key files for Phase 3 wiring:
  - `graph_builder/module/configure/GraphBuilderFactory.java` - register graph builder module
  - `graph_builder/GraphBuilder.java` - add module to build sequence
- The `GeofencingVertexUpdater` class visibility change in Phase 3 is required because the ext module can't access package-private classes in the main module
