package org.opentripplanner.street.model.elevation;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.impl.PackedCoordinateSequence;

public class ElevationProfiles {

  /**
   * An elevation profile that is steep enough to cause negative cost values but
   * not steep enough to be considered erroneous and won't therefore be discarded.
   */
  public static final PackedCoordinateSequence.Double STEEP_ELEVATION_PROFILE =
    new PackedCoordinateSequence.Double(
      new Coordinate[] {
        new Coordinate(0, 6972),
        new Coordinate(25, 6985),
        new Coordinate(50, 6967),
      }
    );
}
