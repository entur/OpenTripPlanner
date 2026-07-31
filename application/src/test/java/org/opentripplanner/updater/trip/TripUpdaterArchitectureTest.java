package org.opentripplanner.updater.trip;

import static org.opentripplanner.OtpArchitectureModules.FRAMEWORK_UTILS;
import static org.opentripplanner.OtpArchitectureModules.OTP_ROOT;
import static org.opentripplanner.OtpArchitectureModules.TRANSIT;
import static org.opentripplanner.OtpArchitectureModules.TRANSIT_MODEL;

import org.junit.jupiter.api.Test;
import org.opentripplanner._support.arch.Package;

/**
 * Enforces the package structure of the unified trip updater. The packages follow the pipeline
 * stages of the ubiquitous language: a parser translates a message into a command
 * ({@code model.command}); the dispatcher executes it by having a factory ({@code factory})
 * resolve it against the transit model - each reference resolved into its entity by a resolver
 * ({@code resolver}) - into a validated change ({@code model.change}); the change applies itself,
 * and the domain service for its type ({@code service}) carries it out.
 * <p>
 * The model packages are the bottom layer: they depend on nothing in the component except the
 * policies, and no package below the component root sees the factories or the domain services.
 */
public class TripUpdaterArchitectureTest {

  private static final Package LEGACY_MODEL = OTP_ROOT.subPackage("model");
  private static final Package UPDATER_SPI = OTP_ROOT.subPackage("updater").subPackage("spi");
  private static final Package UPDATER_TRIP = OTP_ROOT.subPackage("updater").subPackage("trip");

  private static final Package MODEL_COMMAND = UPDATER_TRIP.subPackage("model").subPackage(
    "command"
  );
  private static final Package MODEL_CHANGE = UPDATER_TRIP.subPackage("model").subPackage("change");
  private static final Package POLICY = UPDATER_TRIP.subPackage("policy");
  private static final Package RESOLVER = UPDATER_TRIP.subPackage("resolver");
  private static final Package FACTORY = UPDATER_TRIP.subPackage("factory");
  private static final Package SERVICE = UPDATER_TRIP.subPackage("service");
  private static final Package PATTERN_CACHE = UPDATER_TRIP.subPackage("patterncache");
  private static final Package GTFS_INTERPOLATION = UPDATER_TRIP.subPackage("gtfs").subPackage(
    "interpolation"
  );

  private static final Package TRANSIT_MODEL_BASIC = TRANSIT_MODEL.subPackage("basic");
  private static final Package TRANSIT_MODEL_FRAMEWORK = TRANSIT_MODEL.subPackage("framework");
  private static final Package TRANSIT_MODEL_NETWORK = TRANSIT_MODEL.subPackage("network");
  private static final Package TRANSIT_MODEL_SITE = TRANSIT_MODEL.subPackage("site");
  private static final Package TRANSIT_MODEL_TIMETABLE = TRANSIT_MODEL.subPackage("timetable");
  private static final Package TRANSIT_MODEL_CALENDAR = TRANSIT_MODEL.subPackage("calendar");
  private static final Package TRANSIT_MODEL_ORGANIZATION = TRANSIT_MODEL.subPackage(
    "organization"
  );
  private static final Package TRANSIT_SERVICE = TRANSIT.subPackage("service");

  @Test
  void enforceCommandModelPackageDependencies() {
    MODEL_COMMAND.dependsOn(
      FRAMEWORK_UTILS,
      LEGACY_MODEL,
      TRANSIT_MODEL_BASIC,
      TRANSIT_MODEL_TIMETABLE,
      POLICY
    ).verify();
  }

  @Test
  void enforceChangeModelPackageDependencies() {
    MODEL_CHANGE.dependsOn(
      FRAMEWORK_UTILS,
      LEGACY_MODEL,
      TRANSIT_MODEL_BASIC,
      TRANSIT_MODEL_FRAMEWORK,
      TRANSIT_MODEL_NETWORK,
      TRANSIT_MODEL_SITE,
      TRANSIT_MODEL_TIMETABLE,
      UPDATER_SPI,
      MODEL_COMMAND,
      POLICY
    ).verify();
  }

  @Test
  void enforcePolicyPackageDependencies() {
    // TODO The policies are format-neutral parameters carried by the commands, so they should
    //  sit below the command model. Today StopMatchingPolicy and FormatPolicy reference both
    //  models (a policy <-> model cycle: commands carry a FormatPolicy while StopMatchingPolicy
    //  reads StopReference and ResolvedStopTimeUpdate), and DelayPropagationPolicy references
    //  the GTFS delay interpolation machinery.
    POLICY.dependsOn(
      FRAMEWORK_UTILS,
      LEGACY_MODEL,
      TRANSIT_MODEL_NETWORK,
      TRANSIT_MODEL_SITE,
      TRANSIT_MODEL_TIMETABLE,
      UPDATER_SPI,
      MODEL_COMMAND,
      MODEL_CHANGE,
      GTFS_INTERPOLATION
    ).verify();
  }

  @Test
  void enforceResolverPackageDependencies() {
    RESOLVER.dependsOn(
      FRAMEWORK_UTILS,
      TRANSIT_MODEL_NETWORK,
      TRANSIT_MODEL_SITE,
      TRANSIT_MODEL_TIMETABLE,
      TRANSIT_SERVICE,
      UPDATER_SPI,
      MODEL_COMMAND
    ).verify();
  }

  @Test
  void enforceFactoryPackageDependencies() {
    FACTORY.dependsOn(
      FRAMEWORK_UTILS,
      TRANSIT_MODEL_NETWORK,
      TRANSIT_MODEL_SITE,
      TRANSIT_MODEL_TIMETABLE,
      TRANSIT_MODEL_CALENDAR,
      TRANSIT_MODEL_ORGANIZATION,
      TRANSIT_SERVICE,
      UPDATER_SPI,
      MODEL_COMMAND,
      MODEL_CHANGE,
      RESOLVER
    ).verify();
  }

  @Test
  void enforceServicePackageDependencies() {
    SERVICE.dependsOn(
      FRAMEWORK_UTILS,
      TRANSIT_MODEL_FRAMEWORK,
      TRANSIT_MODEL_NETWORK,
      TRANSIT_MODEL_TIMETABLE,
      UPDATER_SPI,
      MODEL_CHANGE,
      PATTERN_CACHE
    ).verify();
  }
}
