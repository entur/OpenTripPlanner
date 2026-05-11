package org.opentripplanner.routing.algorithm.raptoradapter.router.startonboardaccess;

record LocationInTripPatternReference(
  int stopIndex,
  int stopPositionInPattern,
  int boardingTimeSeconds
) {}
