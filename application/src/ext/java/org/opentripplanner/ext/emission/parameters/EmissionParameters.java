package org.opentripplanner.ext.emission.parameters;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import org.opentripplanner.utils.tostring.ToStringBuilder;

/**
 * Input parameters for the emission module.
 */
public class EmissionParameters {

  public static final EmissionParameters DEFAULT = new EmissionParameters(
    EmissionViechleParameters.CAR_DEFAULTS,
    List.of()
  );

  private final EmissionViechleParameters car;
  private final List<EmissionFeedParameters> feeds;

  public EmissionParameters(EmissionViechleParameters car, List<EmissionFeedParameters> feeds) {
    this.car = car;
    this.feeds = List.copyOf(feeds);
  }

  public static EmissionParameters.Builder of() {
    return DEFAULT.copyOf();
  }

  private EmissionParameters.Builder copyOf() {
    return new Builder(DEFAULT);
  }

  public EmissionViechleParameters car() {
    return car;
  }

  public List<EmissionFeedParameters> feeds() {
    return feeds;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    var that = (EmissionParameters) o;
    return Objects.equals(car, that.car) && Objects.equals(feeds, that.feeds);
  }

  @Override
  public int hashCode() {
    return Objects.hash(car, feeds);
  }

  @Override
  public String toString() {
    return ToStringBuilder.of(EmissionParameters.class)
      .addObj("car", car, EmissionViechleParameters.CAR_DEFAULTS)
      .addCol("fedds", feeds)
      .toString();
  }

  public static class Builder {

    private EmissionParameters origin;
    private EmissionViechleParameters car;
    private List<EmissionFeedParameters> feeds = new ArrayList<>();

    public Builder(EmissionParameters origin) {
      this.origin = origin;
      this.car = origin.car;
    }

    public Builder addFeeds(Collection<EmissionFeedParameters> feeds) {
      this.feeds.addAll(feeds);
      return this;
    }

    public Builder withCar(EmissionViechleParameters car) {
      this.car = car;
      return this;
    }

    public EmissionParameters build() {
      var candidate = new EmissionParameters(car, feeds);
      return origin.equals(candidate) ? origin : candidate;
    }
  }
}
