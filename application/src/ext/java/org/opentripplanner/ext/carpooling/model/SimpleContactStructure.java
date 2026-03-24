package org.opentripplanner.ext.carpooling.model;

import javax.annotation.Nullable;

/**
 * Booking information for a carpool trip
 *
 * @param phoneNumber the phone number, may be null
 * @param url the URL (booking link), may be null
 */
public record SimpleContactStructure(
  @Nullable String phoneNumber,
  @Nullable String url
) {}
