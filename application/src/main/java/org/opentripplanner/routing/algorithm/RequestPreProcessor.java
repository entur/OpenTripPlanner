package org.opentripplanner.routing.algorithm;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.opentripplanner.raptor.api.request.RaptorTuningParameters;
import org.opentripplanner.routing.algorithm.raptoradapter.router.AdditionalSearchDays;
import org.opentripplanner.routing.algorithm.raptoradapter.router.onboardaccess.StartOnBoardAccessResolver;
import org.opentripplanner.routing.api.request.RouteRequest;
import org.opentripplanner.transit.service.TransitService;
import org.opentripplanner.utils.time.ServiceDateUtils;

public class RequestPreProcessor {

  private final TransitService transitService;
  private final RaptorTuningParameters tuningParameters;
  private final ZoneId zoneId;

  public RequestPreProcessor(
    TransitService transitService,
    RaptorTuningParameters tuningParameters,
    ZoneId zoneId
  ) {
    this.transitService = transitService;
    this.tuningParameters = tuningParameters;
    this.zoneId = zoneId;
  }

  public RoutingWorkerRequest computeRequest(RouteRequest orginalRequest) {
    var request = orginalRequest.withPageCursor();
    request = amendOnBoardAccessRequestWithExactBoardingTime(request);

    var transitSearchTimeZero = ServiceDateUtils.asStartOfService(request.dateTime(), zoneId);
    AdditionalSearchDays additionalSearchDays = createAdditionalSearchDays(
      tuningParameters,
      zoneId,
      request
    );

    return new RoutingWorkerRequest(request, transitSearchTimeZero, additionalSearchDays);
  }

  /// For on-board access we need to amend the request to have an on-board access at the exact
  /// boarding time for the access. We do that here in the RoutingWorker because finding the exact
  /// boarding time requires using the transit service to look up timetable data.
  private RouteRequest amendOnBoardAccessRequestWithExactBoardingTime(RouteRequest request) {
    if (!request.isOnBoardAccessRequest()) {
      return request;
    }
    var fromLocation = request.from();
    if (fromLocation == null || fromLocation.tripLocation == null) {
      throw new IllegalArgumentException();
    }

    var boardingDateTime = new StartOnBoardAccessResolver(transitService).resolveBoardingDateTime(
      fromLocation.tripLocation,
      zoneId
    );
    var iterationStep = Duration.ofSeconds(tuningParameters.iterationDepartureStepInSeconds());
    return request.copyOf().withOnBoardAccessAt(boardingDateTime, iterationStep).buildRequest();
  }


  private static AdditionalSearchDays createAdditionalSearchDays(
    RaptorTuningParameters raptorTuningParameters,
    ZoneId zoneId,
    RouteRequest request
  ) {
    var searchDateTime = ZonedDateTime.ofInstant(request.dateTime(), zoneId);
    var maxWindow = raptorTuningParameters.dynamicSearchWindowCoefficients().maxWindow();

    return new AdditionalSearchDays(
      request.arriveBy(),
      searchDateTime,
      request.searchWindow(),
      maxWindow,
      request.preferences().system().maxJourneyDuration()
    );
  }
}
