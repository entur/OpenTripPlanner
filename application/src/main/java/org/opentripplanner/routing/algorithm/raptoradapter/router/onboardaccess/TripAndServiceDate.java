package org.opentripplanner.routing.algorithm.raptoradapter.router.onboardaccess;

import java.time.LocalDate;
import org.opentripplanner.transit.model.timetable.Trip;

public record TripAndServiceDate(Trip trip, LocalDate serviceDate) {}
