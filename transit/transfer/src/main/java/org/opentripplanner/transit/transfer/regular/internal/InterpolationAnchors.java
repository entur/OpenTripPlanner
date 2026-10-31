package org.opentripplanner.transit.transfer.regular.internal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Nullable;
import org.opentripplanner.core.model.basic.Reluctance;
import org.opentripplanner.core.model.basic.Speed;
import org.opentripplanner.raptor.data.transfers.regular.TransferValueFunction;
import org.opentripplanner.transit.transfer.regular.api.preferences.AbstractUserPreferences;
import org.opentripplanner.transit.transfer.regular.api.preferences.BicyclePreferences;
import org.opentripplanner.transit.transfer.regular.api.preferences.CarPreferences;
import org.opentripplanner.transit.transfer.regular.api.preferences.WalkPreferences;

/**
 * The user preferences the exact transfers are computed for, so the transfers of any preferences
 * between them are interpolated: the speed and the reluctance of a profile are varied, all other
 * preferences are the ones of the profile. The duration is exactly proportional to the inverse of
 * the speed, and the cost is linear in the reluctance and in the inverse of the speed, so the
 * interpolation is done in these. A car has no speed, and the speed of a bicycle is not varied (see
 * {@link #of}), only the reluctance is varied.
 * <p>
 * The payload of a transfer is the duration at the two speeds and the cost at the four
 * combinations (two for a car: the duration and the cost at the two reluctances).
 */
final class InterpolationAnchors {

  private final AbstractUserPreferences<?> profile;
  private final double[] speeds;
  private final double[] reluctances;
  private final List<AbstractUserPreferences<?>> anchors;

  private InterpolationAnchors(
    AbstractUserPreferences<?> profile,
    double[] speeds,
    double[] reluctances
  ) {
    this.profile = profile;
    this.speeds = speeds;
    this.reluctances = reluctances;
    var list = new ArrayList<AbstractUserPreferences<?>>();
    if (speeds == null) {
      for (double r : reluctances) {
        list.add(with(profile, null, r));
      }
    } else {
      for (double s : speeds) {
        for (double r : reluctances) {
          list.add(with(profile, s, r));
        }
      }
    }
    this.anchors = List.copyOf(list);
  }

  /** The anchors of the profile, or {@code null} if the type of the profile is not supported. */
  @Nullable
  static InterpolationAnchors of(AbstractUserPreferences<?> profile) {
    return switch (profile) {
      case WalkPreferences _ -> new InterpolationAnchors(
        profile,
        list("otp.transfers.anchors.walkSpeed", 0.4, 3.0),
        pair("otp.transfers.anchors.walkReluctance", 1.0, 10.0)
      );
      // The duration of a bicycle transfer is not a smooth function of 1/speed: every edge is capped
      // at its own car speed. No set of anchors is exact for all speeds (the 7 anchors fitted to the
      // car speeds of one graph failed at the default speed 5.0 and at 5.3), so the speed is not
      // interpolated. Only the reluctance is, for the speed of the profile.
      case BicyclePreferences _ -> new InterpolationAnchors(
        profile,
        null,
        pair("otp.transfers.anchors.bikeReluctance", 1.0, 8.0)
      );
      case CarPreferences _ -> new InterpolationAnchors(
        profile,
        null,
        pair("otp.transfers.anchors.carReluctance", 1.0, 8.0)
      );
      default -> null;
    };
  }

  private static double[] pair(String property, double lo, double hi) {
    return list(property, lo, hi);
  }

  /** The values of a system property (comma separated, ascending), for experiments. */
  private static double[] list(String property, double... defaults) {
    var value = System.getProperty(property);
    if (value == null) {
      return defaults;
    }
    return Arrays.stream(value.split(",")).mapToDouble(Double::parseDouble).toArray();
  }

  /** The preferences to compute exact transfers for, in the order of the payload. */
  List<AbstractUserPreferences<?>> anchors() {
    return anchors;
  }

  /** Create the payload of a path from its exact criteria at the anchors, in the anchor order. */
  float[] payload(int[] duration, int[] c1) {
    if (speeds == null) {
      return new float[] { duration[0], c1[0], c1[1] };
    }
    int n = speeds.length;
    var payload = new float[3 * n];
    for (int i = 0; i < n; ++i) {
      payload[i] = duration[2 * i];
      payload[n + 2 * i] = c1[2 * i];
      payload[n + 2 * i + 1] = c1[2 * i + 1];
    }
    return payload;
  }

  /**
   * The function that interpolates for {@code prefs}, {@code null} if it is outside the anchors, or
   * if another preference than the speed and the reluctance is not the one of the profile.
   */
  @Nullable
  TransferValueFunction function(AbstractUserPreferences<?> prefs) {
    if (prefs.getClass() != profile.getClass()) {
      return null;
    }
    double r = prefs.reluctance().value();
    double v = speedOf(prefs);
    if (speeds == null && Math.abs(v - speedOf(profile)) > 1e-9) {
      // A fixed speed (a car has none, a bicycle is not interpolated in speed)
      return null;
    }
    if (!with(prefs, speedOf(profile), profile.reluctance().value()).equals(profile)) {
      return null;
    }
    if (r < reluctances[0] || r > reluctances[1]) {
      return null;
    }
    double u = (r - reluctances[0]) / (reluctances[1] - reluctances[0]);
    if (speeds == null) {
      return (p, off, len) -> {
        double c = (1 - u) * p[off + 1] + u * p[off + 2];
        return TransferValueFunction.pack((int) p[off], roundCost(c));
      };
    }
    if (v < speeds[0] || v > speeds[speeds.length - 1]) {
      return null;
    }
    int n = speeds.length;
    int k = 0;
    while (k < n - 2 && v > speeds[k + 1]) {
      ++k;
    }
    final int seg = k;
    double inv0 = 1.0 / speeds[seg];
    double inv1 = 1.0 / speeds[seg + 1];
    double t = (1.0 / v - inv0) / (inv1 - inv0);
    return (p, off, len) -> {
      double dur = p[off + seg] + t * (p[off + seg + 1] - p[off + seg]);
      int c = off + n + 2 * seg;
      double c0 = (1 - u) * p[c] + u * p[c + 1];
      double c1 = (1 - u) * p[c + 2] + u * p[c + 3];
      return TransferValueFunction.pack((int) Math.round(dur), roundCost((1 - t) * c0 + t * c1));
    };
  }

  /** The cost is a whole number of seconds, in centi-seconds. */
  private static int roundCost(double c1) {
    return (int) (Math.round(c1 / 100.0) * 100);
  }

  private static double speedOf(AbstractUserPreferences<?> prefs) {
    return switch (prefs) {
      case WalkPreferences w -> w.speed().toMetersPerSecond();
      case BicyclePreferences b -> b.speed().toMetersPerSecond();
      default -> 0.0;
    };
  }

  /** The preferences with the speed ({@code null} keeps it, a car has none) and the reluctance replaced. */
  private static AbstractUserPreferences<?> with(
    AbstractUserPreferences<?> prefs,
    @Nullable Double speed,
    double reluctance
  ) {
    return switch (prefs) {
      case WalkPreferences w -> w.copyOf()
        .withSpeed(speed == null ? w.speed() : Speed.ofMetersPerSecond(speed))
        .withReluctance(Reluctance.of(reluctance))
        .build();
      case BicyclePreferences b -> b.copyOf()
        .withSpeed(speed == null ? b.speed() : Speed.ofMetersPerSecond(speed))
        .withReluctance(Reluctance.of(reluctance))
        .build();
      case CarPreferences c -> c.copyOf().withReluctance(Reluctance.of(reluctance)).build();
      default -> throw new IllegalArgumentException("Not supported: " + prefs);
    };
  }
}
