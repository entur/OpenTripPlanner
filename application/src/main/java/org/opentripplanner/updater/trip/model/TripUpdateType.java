package org.opentripplanner.updater.trip.model;

/**
 * Internal classification of trip update types used by parsers to determine which concrete
 * {@link ParsedTripUpdate} subtype to construct. This enum is not exposed in the parsed model -
 * the type hierarchy itself encodes update semantics.
 */
public enum TripUpdateType {
  UPDATE_EXISTING,
  CANCEL_TRIP,
  DELETE_TRIP,
  ADD_NEW_TRIP,
  MODIFY_TRIP,
}
