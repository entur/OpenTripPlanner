package org.opentripplanner.routing.algorithm.raptoradapter.router.onboardaccess;

public record BoardingLocationInPatternReference(
  int stopIndex,
  int stopPositionInPattern,
  int boardingTimeSeconds
) {}
