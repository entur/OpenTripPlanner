package org.opentripplanner.ext.carpooling.filter;

import java.time.Instant;
import java.util.Objects;
import org.opentripplanner.routing.algorithm.raptoradapter.router.street.AccessEgressType;
import org.opentripplanner.routing.api.request.RouteRequest;
import org.opentripplanner.street.geometry.WgsCoordinate;

public class CarpoolingRequest {

  private final AccessEgressType accessOrEgress;
  private final boolean isArriveByRequest;
  private final WgsCoordinate passengerPickup;
  private final WgsCoordinate passengerDropoff;
  private final Instant requestedDateTime;

  CarpoolingRequest(
    AccessEgressType accessOrEgress,
    boolean isArriveByRequest,
    WgsCoordinate passengerPickup,
    WgsCoordinate passengerDropoff,
    Instant requestedDateTime
  ) {
    this.accessOrEgress = accessOrEgress;
    this.isArriveByRequest = isArriveByRequest;
    this.passengerPickup = passengerPickup;
    this.passengerDropoff = passengerDropoff;
    this.requestedDateTime = requestedDateTime;
  }

  public static CarpoolingRequest of(RouteRequest request) {
    return new CarpoolingRequestBuilder(request).build();
  }

  public static CarpoolingRequest of(RouteRequest request, AccessEgressType accessOrEgress) {
    return new CarpoolingRequestBuilder(request).withAccessOrEgress(accessOrEgress).build();
  }

  public AccessEgressType getAccessOrEgress() {
    return accessOrEgress;
  }

  public boolean isArriveByRequest() {
    return isArriveByRequest;
  }

  public WgsCoordinate getPassengerPickup() {
    return passengerPickup;
  }

  public WgsCoordinate getPassengerDropoff() {
    return passengerDropoff;
  }

  public Instant getRequestedDateTime() {
    return requestedDateTime;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
      accessOrEgress,
      isArriveByRequest,
      passengerPickup,
      passengerDropoff,
      requestedDateTime
    );
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    CarpoolingRequest that = (CarpoolingRequest) o;
    return (
      isArriveByRequest == that.isArriveByRequest &&
      accessOrEgress == that.accessOrEgress &&
      Objects.equals(passengerPickup, that.passengerPickup) &&
      Objects.equals(passengerDropoff, that.passengerDropoff) &&
      Objects.equals(requestedDateTime, that.requestedDateTime)
    );
  }

  @Override
  public String toString() {
    return (
      "CarpoolingRequest{" +
      "accessOrEgress=" +
      accessOrEgress +
      ", isArriveByRequest=" +
      isArriveByRequest +
      ", passengerPickup=" +
      passengerPickup +
      ", passengerDropoff=" +
      passengerDropoff +
      ", requestedDateTime=" +
      requestedDateTime +
      '}'
    );
  }
}
