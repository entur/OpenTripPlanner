package org.opentripplanner.routing.algorithm;

import java.time.ZonedDateTime;
import org.opentripplanner.routing.algorithm.raptoradapter.router.AdditionalSearchDays;
import org.opentripplanner.routing.api.request.RouteRequest;

public class RoutingWorkerRequest {
  private final RouteRequest request;
  private final ZonedDateTime transitSearchTimeZero;
  private final AdditionalSearchDays additionalSearchDays;

  public RoutingWorkerRequest(RouteRequest request, ZonedDateTime transitSearchTimeZero, AdditionalSearchDays additionalSearchDays) {
    this.request = request;
    this.transitSearchTimeZero = transitSearchTimeZero;
    this.additionalSearchDays = additionalSearchDays;
  }

  public RouteRequest request() {
    return request;
  }

  /**
   * The transit service time-zero normalized for the current search. All transit times are relative
   * to a "time-zero". This enables us to use an integer(small memory footprint). The times are
   * number for seconds past the {@code transitSearchTimeZero}. In the internal model all times are
   * stored relative to the {@link java.time.LocalDate}, but to be able
   * to compare trip times for different service days we normalize all times by calculating an
   * offset. Now all times for the selected trip patterns become relative to the {@code
   * transitSearchTimeZero}.
   */
  public ZonedDateTime transitSearchTimeZero() {
    return transitSearchTimeZero;
  }

  public AdditionalSearchDays additionalSearchDays() {
    return additionalSearchDays;
  }
}
