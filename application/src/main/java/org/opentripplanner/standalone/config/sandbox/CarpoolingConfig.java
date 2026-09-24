package org.opentripplanner.standalone.config.sandbox;

import static org.opentripplanner.standalone.config.framework.json.OtpVersion.V2_11;

import org.opentripplanner.ext.carpooling.CarpoolingParameters;
import org.opentripplanner.standalone.config.framework.json.NodeAdapter;

/** Maps the {@code carpooling} section of the router config to {@link CarpoolingParameters}. */
public class CarpoolingConfig {

  private static final CarpoolingParameters DEFAULT = CarpoolingParameters.DEFAULT;

  private CarpoolingConfig() {}

  public static CarpoolingParameters mapParameters(NodeAdapter root, String parameterName) {
    var c = root
      .of(parameterName)
      .since(V2_11)
      .summary("Configuration for carpooling.")
      .description(
        """
        The limits of the carpool routing. They bound the work a request may cause and the memory
        the held trips may take; the defaults suit a feed of a few thousand trips.
        """
      )
      .asObject();

    return new CarpoolingParameters(
      c
        .of("maxCandidateTripsPerRequest")
        .since(V2_11)
        .summary("The most driver trips a request evaluates.")
        .description(
          """
          Every candidate trip costs a request a fixed amount of work. When more trips pass the
          pre-filters than this, only the ones whose route passes closest to the passenger are
          evaluated. Applies per direction: access, egress and direct.
          """
        )
        .asInt(DEFAULT.maxCandidateTripsPerRequest()),
      c
        .of("maxCandidatesPerStop")
        .since(V2_11)
        .summary("The most carpool access/egress candidates a transit stop hands to Raptor.")
        .description(
          """
          A stop with at most this many candidate cars hands all of them to Raptor. A stop with
          more keeps the first and the last car of the search window and one car per slot in
          between, the slots cut from the window so that the total stays within this number. The
          waiting a passenger may lose to the cut is then at most one slot.
          """
        )
        .asInt(DEFAULT.maxCandidatesPerStop()),
      c
        .of("maxTrips")
        .since(V2_11)
        .summary("The most carpool trips an instance holds, over all feeds.")
        .description(
          """
          Each held trip costs about a hundred kilobytes of memory and a fraction of a second to
          resolve, so this bounds both the heap and the time an instance needs to catch up with the
          feeds after a restart. New trips arriving while the instance is full are dropped; updates
          and cancellations of held trips are always applied.
          """
        )
        .asInt(DEFAULT.maxTrips()),
      c
        .of("maxStopWalk")
        .since(V2_11)
        .summary(
          "The longest walk between a transit stop and the place where a car can stop for it."
        )
        .description(
          """
          A passenger is dropped off for a stop where a car can stop and walks the rest; stops
          farther than this from any drivable street cannot be served by carpool. The passenger's
          own maximum walk from the request is applied on top of it.
          """
        )
        .asDuration(DEFAULT.maxStopWalk()),
      c
        .of("boardCost")
        .since(V2_11)
        .summary("The cost of getting into the car, added once to every carpool leg.")
        .description(
          """
          Applied to direct legs and to access/egress legs alike, so a carpool ride costs the
          same whether it is the whole journey or the leg to a transit stop. It is what keeps
          very short carpool rides from beating walking: walking costs
          `duration x walkReluctance`, so a board cost of `10 x 60 x walkReluctance` means a
          carpool ride only wins when it saves the passenger roughly ten minutes of walking.
          The default assumes a `walkReluctance` of 4.0; lower it if your deployment uses a
          lower walk reluctance, otherwise short carpool rides are suppressed more than
          intended.
          """
        )
        .asInt(DEFAULT.boardCost())
    );
  }
}
