package org.opentripplanner.street.search.request;

import static org.opentripplanner.utils.lang.DoubleUtils.doubleEquals;
import static org.opentripplanner.utils.lang.DoubleUtils.roundTo2Decimals;

import java.util.Objects;
import org.opentripplanner.utils.tostring.ValueObjectToStringBuilder;

/**
 * Sets the (bicycle or scooter) triangle routing parameters -- the relative importance of safety,
 * flatness, and speed. These three fields should have values between 0 and 1, and should add up to
 * 1.
 * <p>
 * THIS CLASS IS IMMUTABLE AND THREAD-SAFE.
 */
public final class TimeSlopeSafetyTriangle {

  private static final double ZERO = 0.0;

  public static final TimeSlopeSafetyTriangle DEFAULT = new TimeSlopeSafetyTriangle(1, 1, 1);
  private final double time;
  private final double slope;
  private final double safety;

  /**
   * Sets the bicycle or scooter triangle routing parameters -- the relative importance of safety,
   * flatness, and speed. These three fields of the RouteRequest should have values between 0 and 1,
   * and should add up to 1. This setter function accepts any three numbers and will normalize them
   * to add up to 1.
   */
  private TimeSlopeSafetyTriangle(double time, double slope, double safety) {
    safety = positiveValueOrZero(safety);
    slope = positiveValueOrZero(slope);
    time = positiveValueOrZero(time);

    if (zeroVector(time, slope, safety)) {
      time = 1.0;
      slope = 1.0;
      safety = 1.0;
    }

    // Normalize
    double total = safety + slope + time;
    time /= total;
    slope /= total;

    // We round to closest 2 decimals
    this.time = roundTo2Decimals(time);
    this.slope = roundTo2Decimals(slope);
    this.safety = roundTo2Decimals(1.0 - (this.time + this.slope));
  }

  public Builder copyOf() {
    return new Builder(this);
  }

  /**
   * Creates a special builder, which used together with the
   * {@link Builder#buildOrDefault(TimeSlopeSafetyTriangle)} can return a new instance or the
   * default value if no values are set. This is useful in the APIs where we want to fall back to
   * the default {@link TimeSlopeSafetyTriangle}, if no values are set. If only one or two values
   * are set the new instance will contain only those values, and the none set values will be zero.
   */
  public static Builder of() {
    return new Builder();
  }

  private static double positiveValueOrZero(double value) {
    return Math.max(0, value);
  }

  private static boolean zeroVector(double a, double b, double c) {
    return a == ZERO && b == ZERO && c == ZERO;
  }

  public double time() {
    return time;
  }

  public double slope() {
    return slope;
  }

  public double safety() {
    return safety;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (obj == null || obj.getClass() != this.getClass()) {
      return false;
    }
    var that = (TimeSlopeSafetyTriangle) obj;
    return (
      Double.doubleToLongBits(this.time) == Double.doubleToLongBits(that.time) &&
      Double.doubleToLongBits(this.slope) == Double.doubleToLongBits(that.slope) &&
      Double.doubleToLongBits(this.safety) == Double.doubleToLongBits(that.safety)
    );
  }

  @Override
  public int hashCode() {
    return Objects.hash(time, slope, safety);
  }

  @Override
  public String toString() {
    return ValueObjectToStringBuilder.of()
      .addText("[time: ")
      .addNum(time)
      .addText(", slope: ")
      .addNum(slope)
      .addText(", safety: ")
      .addNum(safety)
      .addText("]")
      .toString();
  }

  public static class Builder {

    private double time;
    private double slope;
    private double safety;

    private Builder(TimeSlopeSafetyTriangle original) {
      this.time = original.time;
      this.slope = original.slope;
      this.safety = original.safety;
    }

    private Builder() {
      this.time = ZERO;
      this.slope = ZERO;
      this.safety = ZERO;
    }

    public double time() {
      return time;
    }

    public Builder withTime(double time) {
      this.time = time;
      return this;
    }

    public double slope() {
      return slope;
    }

    public Builder withSlope(double slope) {
      this.slope = slope;
      return this;
    }

    public double safety() {
      return safety;
    }

    public Builder withSafety(double safety) {
      this.safety = safety;
      return this;
    }

    /**
     * Returns true if none of the values are set (i.e. all values are zero).
     */
    public boolean isEmpty() {
      return doubleEquals(time, ZERO) && doubleEquals(slope, ZERO) && doubleEquals(safety, ZERO);
    }

    public TimeSlopeSafetyTriangle build() {
      return new TimeSlopeSafetyTriangle(time, slope, safety);
    }

    public TimeSlopeSafetyTriangle buildOrDefault(TimeSlopeSafetyTriangle defaultValue) {
      // If none of the fields are set, fallback to the default values given
      if (zeroVector(time, slope, safety)) {
        return defaultValue;
      }
      return new TimeSlopeSafetyTriangle(time, slope, safety);
    }
  }
}
