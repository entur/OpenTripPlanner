package org.opentripplanner.street.model.elevation;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateSequence;

public class SlopeCostCalculator {

  /*
   * These numbers disagree with everything else I (David Turner) have read about the energy cost
   * of cycling but given that we are going to be fudging them anyway, they're not totally crazy
   */
  private static final double ENERGY_PER_METER_ON_FLAT = 1;

  private static final double ENERGY_SLOPE_FACTOR = 4000;

  /**
   * If the calculated factor is more than this constant, we ignore the calculated factor and use
   * this constant in stead. See ths table in {@link ToblersHikingFunction} for a mapping between
   * the factor and angels(degree and percentage). A factor of 3 with take effect for slopes with a
   * incline above 31.4% and a decline below 41.4%. The worlds steepest road ia about 35%, and the
   * steepest climes in Tour De France is usually in the range 8-12%. Some walking paths may be
   * quite steep, but a penalty of 3 is still a large penalty.
   */
  private static final double MAX_SLOPE_WALK_EFFECTIVE_LENGTH_FACTOR = 3;

  private static final ToblersHikingFunction TOBLER_WALKING_FUNCTION = new ToblersHikingFunction(
    MAX_SLOPE_WALK_EFFECTIVE_LENGTH_FACTOR
  );

  /// Maximum slope of 35% after which elevation data is considered unreliable. It is based
  /// on the steepest drivable road,
  /// [Baldwin Street in New Zealand](https://en.wikipedia.org/wiki/Baldwin_Street)
  private static final double MAX_SPLINE_SLOPE = 0.35;
  private static final double MIN_SPLINE_SLOPE = -MAX_SPLINE_SLOPE;


  /// Compute the slope costs for an elevation profile, taking into account that mixing raster elevation
  /// with OSM data often leads to glitches that cause very high slopes and in turn to negative costs.
  ///
  /// We have [analysed](https://github.com/opentripplanner/OpenTripPlanner/pull/7579#pullrequestreview-4226004340)
  /// if these glitches more commonly lead to edges that are too sloped when in reality they are
  /// flat or the other way around: the result is that it's more common for slopes to be artificially
  /// steep.
  ///
  /// For this reason we set the slope for an elevation segment to zero if it exceeds the limit
  /// of {@link #MAX_SPLINE_SLOPE} uphill or {@link #MIN_SPLINE_SLOPE} downhill.
  ///
  /// @param elev The elevation profile, where each (x, y) is (distance along edge, elevation)
  public static SlopeCosts getSlopeCosts(CoordinateSequence elev) {
    Coordinate[] coordinates = elev.toCoordinateArray();
    boolean flattened = false;
    double maxSlope = 0;
    double slopeSpeedEffectiveLength = 0;
    double slopeWorkCost = 0;
    double slopeSafetyCost = 0;
    double effectiveWalkLength = 0;
    double[] lengths = getLengthsFromElevation(elev);
    double trueLength = lengths[0];
    double flatLength = lengths[1];
    if (flatLength < 1e-3) {
      // Too small edge, returning neutral slope costs.
      return new SlopeCosts(1.0, 1.0, 0.0, 0.0, 1.0, false, 1.0);
    }
    double lengthMultiplier = trueLength / flatLength;
    for (int i = 0; i < coordinates.length - 1; ++i) {
      double run = coordinates[i + 1].x - coordinates[i].x;
      double rise = coordinates[i + 1].y - coordinates[i].y;
      if (run == 0) {
        continue;
      }
      double slope = rise / run;
      // We need _some_ sort of limit, because the energy
      // usage approximation breaks down at extreme slopes, and
      // gives negative weights
      if (slope > MAX_SPLINE_SLOPE || slope < MIN_SPLINE_SLOPE) {
        slope = 0;
        flattened = true;
      }
      if (maxSlope < Math.abs(slope)) {
        maxSlope = Math.abs(slope);
      }

      double slope_or_zero = Math.max(slope, 0);
      double hypotenuse = Math.sqrt(rise * rise + run * run);
      double energy =
        hypotenuse *
        (ENERGY_PER_METER_ON_FLAT +
          ENERGY_SLOPE_FACTOR * slope_or_zero * slope_or_zero * slope_or_zero);
      slopeWorkCost += energy;
      double slopeSpeedCoef = BicycleSlopeSpeedFunction.coefficient(slope, coordinates[i].y);
      slopeSpeedEffectiveLength += run / slopeSpeedCoef;
      // assume that speed and safety are inverses
      double safetyCost = hypotenuse * (slopeSpeedCoef - 1) * 0.25;
      if (safetyCost > 0) {
        slopeSafetyCost += safetyCost;
      }
      effectiveWalkLength += calculateEffectiveWalkLength(run, rise);
    }
    /*
     * Here we divide by the *flat length* as the slope/work cost factors are multipliers of the
     * length of the street edge which is the flat one.
     */
    return new SlopeCosts(
      slopeSpeedEffectiveLength / flatLength,
      slopeWorkCost / flatLength,
      slopeSafetyCost,
      maxSlope,
      lengthMultiplier,
      flattened,
      effectiveWalkLength / flatLength
    );
  }

  /**
   * <p>
   * We use the Tobler function {@link ToblersHikingFunction} to calculate this.
   * </p>
   * <p>
   * When testing this we get good results in general, but for some edges the elevation profile is
   * not accurate. A (serpentine) road is usually build with a constant slope, but the elevation
   * profile in OTP is not as smooth, resulting in an extra penalty for these roads.
   * </p>
   */
  static double calculateEffectiveWalkLength(double run, double rise) {
    return run * TOBLER_WALKING_FUNCTION.calculateHorizontalWalkingDistanceMultiplier(run, rise);
  }


  private static double[] getLengthsFromElevation(CoordinateSequence elev) {
    double trueLength = 0;
    double flatLength = 0;
    double lastX = elev.getX(0);
    double lastY = elev.getY(0);
    for (int i = 1; i < elev.size(); ++i) {
      Coordinate c = elev.getCoordinate(i);
      double x = c.x - lastX;
      double y = c.y - lastY;
      trueLength += Math.sqrt(x * x + y * y);
      flatLength += x;
      lastX = c.x;
      lastY = c.y;
    }
    return new double[] { trueLength, flatLength };
  }
}
