package org.opentripplanner.street.model.elevation;

import static com.google.common.truth.Truth.assertThat;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.impl.PackedCoordinateSequence;

class ElevationProfileOutlierFilterTest {

  private static final double EPS = 1e-9;

  @Test
  void noopWhenThresholdZero() {
    var input = profile(0, 100, 25, 110, 50, 105, 75, 120, 100, 130);
    assertProfilesEqual(input, new ElevationProfileOutlierFilter(0).filtered(input));
  }

  @Test
  void noopWhenFewerThanThreePoints() {
    var input = profile(0, 10, 50, 20);
    assertProfilesEqual(input, new ElevationProfileOutlierFilter(5).filtered(input));
  }

  @Test
  void smoothProfileIsUntouched() {
    var ramp = profile(0, 0, 25, 5, 50, 10, 75, 15, 100, 20);
    assertProfilesEqual(ramp, new ElevationProfileOutlierFilter(3).filtered(ramp));
  }

  @Test
  void interiorSpikeIsReplaced() {
    var input = profile(0, 100, 25, 100, 50, 110, 75, 100, 100, 100);
    var filtered = new ElevationProfileOutlierFilter(3).filtered(input);
    assertThat(filtered.getOrdinate(2, 1)).isWithin(EPS).of(100.0);
  }

  @Test
  void spikeBelowThresholdSurvives() {
    var input = profile(0, 100, 25, 100, 50, 102, 75, 100, 100, 100);
    assertProfilesEqual(input, new ElevationProfileOutlierFilter(3).filtered(input));
  }

  @Test
  void startEndpointOutlierIsReplaced() {
    var input = profile(0, 9.13, 25, 18.34, 50, 20.68, 75, 23.48);
    var filtered = new ElevationProfileOutlierFilter(3).filtered(input);
    assertThat(filtered.getOrdinate(0, 1)).isNotEqualTo(9.13);
    double slope01 = (filtered.getOrdinate(1, 1) - filtered.getOrdinate(0, 1)) / 25;
    assertThat(Math.abs(slope01)).isLessThan(0.35);
  }

  @Test
  void endEndpointOutlierIsReplaced() {
    var input = profile(0, 100, 25, 102, 50, 103, 75, 104, 100, 130);
    var filtered = new ElevationProfileOutlierFilter(3).filtered(input);
    assertThat(filtered.getOrdinate(4, 1)).isNotEqualTo(130.0);
  }

  /** max(|slope|) cannot exceed the input maximum at any threshold. */
  @Test
  void maxSlopeNeverIncreases() {
    var input = profile(
      0,
      100,
      25,
      105,
      50,
      95,
      75,
      108,
      100,
      102,
      125,
      100,
      150,
      96,
      175,
      110,
      200,
      100
    );
    double maxBefore = maxAbsSlope(input);
    for (double t : new double[] { 0.5, 1, 2, 3, 5, 10 }) {
      var filtered = new ElevationProfileOutlierFilter(t).filtered(input);
      assertThat(maxAbsSlope(filtered)).isAtMost(maxBefore + EPS);
    }
  }

  private static PackedCoordinateSequence profile(double... distAndEle) {
    int n = distAndEle.length / 2;
    Coordinate[] coords = new Coordinate[n];
    for (int i = 0; i < n; i++) {
      coords[i] = new Coordinate(distAndEle[2 * i], distAndEle[2 * i + 1]);
    }
    return new PackedCoordinateSequence.Double(coords);
  }

  private static double maxAbsSlope(PackedCoordinateSequence profile) {
    double max = 0;
    for (int i = 1; i < profile.size(); i++) {
      double run = profile.getOrdinate(i, 0) - profile.getOrdinate(i - 1, 0);
      double rise = profile.getOrdinate(i, 1) - profile.getOrdinate(i - 1, 1);
      double slope = Math.abs(rise / run);
      if (slope > max) {
        max = slope;
      }
    }
    return max;
  }

  private static void assertProfilesEqual(PackedCoordinateSequence a, PackedCoordinateSequence b) {
    assertThat(b.size()).isEqualTo(a.size());
    for (int i = 0; i < a.size(); i++) {
      assertThat(b.getOrdinate(i, 0)).isWithin(EPS).of(a.getOrdinate(i, 0));
      assertThat(b.getOrdinate(i, 1)).isWithin(EPS).of(a.getOrdinate(i, 1));
    }
  }
}
