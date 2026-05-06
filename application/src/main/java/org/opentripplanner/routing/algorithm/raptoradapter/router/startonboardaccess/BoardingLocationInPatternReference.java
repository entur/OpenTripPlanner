package org.opentripplanner.routing.algorithm.raptoradapter.router.startonboardaccess;

public record BoardingLocationInPatternReference(
  int stopIndex,
  int stopPositionInPattern,
  int boardingTimeSeconds
) {}
