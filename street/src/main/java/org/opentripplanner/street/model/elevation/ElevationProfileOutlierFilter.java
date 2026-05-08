package org.opentripplanner.street.model.elevation;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.impl.PackedCoordinateSequence;

/**
 * Removes single-sample DEM glitches from an elevation profile by iteratively replacing
 * the worst outlier with a prediction based on its neighbors. A sample is considered an
 * outlier when its elevation deviates from a linear interpolation of its immediate
 * neighbors by more than {@code maxSpikeHeight}. Endpoints are checked against an
 * extrapolation from the two nearest interior samples.
 * <p>
 * Targeted at the failure mode where the road centreline brushes a cliff, retaining wall,
 * rooftop or NoData cell and the resulting per-sample elevation deviates by 5-15 m from
 * the surrounding road surface. Such samples are rejected and replaced with the smooth
 * trend predicted by their neighbors.
 * <p>
 * Algorithm: at each iteration, consider every interior sample plus the two endpoints as
 * outlier candidates and find the one with the largest deviation. If it exceeds the
 * threshold, replace that single sample with its prediction and re-evaluate. Stop when no
 * candidate exceeds the threshold. This greedy strategy correctly resolves chained
 * outliers (e.g. an endpoint glitch that makes its neighbor look anomalous) by always
 * fixing the largest deviation first.
 * <p>
 * Properties:
 * <ul>
 *   <li>Real linear or near-linear segments (sustained climbs, descents, flats) are
 *       untouched: every interior point already lies on its neighbor-to-neighbor line, so
 *       the deviation is zero.</li>
 *   <li>Endpoint outliers are handled. Endpoint checks are skipped when the endpoint is
 *       more than 2× farther from its neighbor than the interior spacing — extrapolating
 *       across a long gap from a short-segment slope is unreliable.</li>
 *   <li>The filter cannot increase {@code max(|slope|)} beyond the input — replacements
 *       are linear interpolations of existing samples, which by construction produce
 *       slopes no larger than the maximum input slope on that span.</li>
 * </ul>
 * <p>
 * Instances are immutable and thread-safe.
 */
public final class ElevationProfileOutlierFilter {

  /**
   * Endpoint extrapolation is skipped when the gap from the last-but-one sample to the
   * endpoint exceeds this multiple of the interior sample spacing. This prevents false
   * positives on edges where the final sample falls at a distance significantly larger
   * than the regular sample interval.
   */
  private static final double ENDPOINT_GAP_RATIO_LIMIT = 2.0;

  private final double maxSpikeHeight;

  /**
   * @param maxSpikeHeight the deviation threshold in metres. Samples deviating from their
   *                       neighbor-based prediction by more than this are rewritten. Values
   *                       {@code <= 0} disable the filter (it becomes a pass-through).
   */
  public ElevationProfileOutlierFilter(double maxSpikeHeight) {
    this.maxSpikeHeight = maxSpikeHeight;
  }

  /**
   * Filter the profile in place. Distances ({@code x}) are unchanged; elevations
   * ({@code y}) of samples whose neighbor-predicted elevation differs by more than
   * {@code maxSpikeHeight} are replaced with that prediction.
   */
  public void filter(PackedCoordinateSequence profile) {
    if (maxSpikeHeight <= 0) {
      return;
    }
    int n = profile.size();
    if (n < 3) {
      return;
    }

    for (int iter = 0; iter < n; iter++) {
      int worstIdx = -1;
      double worstDev = maxSpikeHeight;
      double worstReplacement = 0;

      for (int i = 1; i < n - 1; i++) {
        double xPrev = profile.getOrdinate(i - 1, 0);
        double xCur = profile.getOrdinate(i, 0);
        double xNext = profile.getOrdinate(i + 1, 0);
        double span = xNext - xPrev;
        if (span <= 0) {
          continue;
        }
        double yPrev = profile.getOrdinate(i - 1, 1);
        double yNext = profile.getOrdinate(i + 1, 1);
        double predicted = yPrev + ((xCur - xPrev) / span) * (yNext - yPrev);
        double dev = Math.abs(profile.getOrdinate(i, 1) - predicted);
        if (dev > worstDev) {
          worstDev = dev;
          worstIdx = i;
          worstReplacement = predicted;
        }
      }

      double startGap = profile.getOrdinate(1, 0) - profile.getOrdinate(0, 0);
      double startInteriorGap = profile.getOrdinate(2, 0) - profile.getOrdinate(1, 0);
      if (startInteriorGap > 0 && startGap <= ENDPOINT_GAP_RATIO_LIMIT * startInteriorGap) {
        double slope = (profile.getOrdinate(2, 1) - profile.getOrdinate(1, 1)) / startInteriorGap;
        double predicted = profile.getOrdinate(1, 1) - slope * startGap;
        double dev = Math.abs(profile.getOrdinate(0, 1) - predicted);
        if (dev > worstDev) {
          worstDev = dev;
          worstIdx = 0;
          worstReplacement = predicted;
        }
      }

      double endGap = profile.getOrdinate(n - 1, 0) - profile.getOrdinate(n - 2, 0);
      double endInteriorGap = profile.getOrdinate(n - 2, 0) - profile.getOrdinate(n - 3, 0);
      if (endInteriorGap > 0 && endGap <= ENDPOINT_GAP_RATIO_LIMIT * endInteriorGap) {
        double slope =
          (profile.getOrdinate(n - 2, 1) - profile.getOrdinate(n - 3, 1)) / endInteriorGap;
        double predicted = profile.getOrdinate(n - 2, 1) + slope * endGap;
        double dev = Math.abs(profile.getOrdinate(n - 1, 1) - predicted);
        if (dev > worstDev) {
          worstDev = dev;
          worstIdx = n - 1;
          worstReplacement = predicted;
        }
      }

      if (worstIdx < 0) {
        return;
      }
      profile.setOrdinate(worstIdx, 1, worstReplacement);
    }
  }

  /**
   * Returns a filtered copy of {@code profile}, or {@code profile} itself unchanged when
   * the filter is disabled (threshold {@code <= 0}) or the profile has fewer than three
   * samples. The input is never mutated.
   */
  public PackedCoordinateSequence filtered(PackedCoordinateSequence profile) {
    if (maxSpikeHeight <= 0 || profile.size() < 3) {
      return profile;
    }
    int size = profile.size();
    Coordinate[] copy = new Coordinate[size];
    for (int i = 0; i < size; i++) {
      copy[i] = new Coordinate(profile.getOrdinate(i, 0), profile.getOrdinate(i, 1));
    }
    PackedCoordinateSequence result = new PackedCoordinateSequence.Double(copy);
    filter(result);
    return result;
  }
}
