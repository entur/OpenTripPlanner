package org.opentripplanner.updater.trip;

import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.timetable.Trip;

/**
 * A matched trip and its associated pattern.
 * Used as the result of trip resolution, including fuzzy matching.
 */
public record TripAndPattern(Trip trip, TripPattern tripPattern) {}
