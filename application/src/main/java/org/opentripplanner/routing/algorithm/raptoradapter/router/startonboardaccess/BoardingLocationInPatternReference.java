package org.opentripplanner.routing.algorithm.raptoradapter.router.startonboardaccess;

record BoardingLocationInPatternReference(
  int stopIndex,
  int stopPositionInPattern,
  int boardingTimeSeconds
) {}
