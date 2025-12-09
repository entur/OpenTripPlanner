package org.opentripplanner.routing.api.request.preference;

import java.io.Serializable;
import java.util.Objects;
import java.util.function.Consumer;
import org.opentripplanner.framework.model.Units;
import org.opentripplanner.utils.tostring.ToStringBuilder;

/**
 *  TODO: how long does it /really/ take to  an elevator?
 * <p>
 * THIS CLASS IS IMMUTABLE AND THREAD-SAFE.
 */
public final class ElevatorPreferences implements Serializable {

  public static final ElevatorPreferences DEFAULT = new ElevatorPreferences();

  private final int boardTime;
  private final int hopTime;
  private final double reluctance;

  private ElevatorPreferences() {
    this.boardTime = 90;
    this.hopTime = 20;
    this.reluctance = 2.0;
  }

  private ElevatorPreferences(Builder builder) {
    this.boardTime = Units.duration(builder.boardTime);
    this.hopTime = Units.duration(builder.hopTime);
    this.reluctance = Units.reluctance(builder.reluctance);
  }

  public static Builder of() {
    return DEFAULT.copyOf();
  }

  public Builder copyOf() {
    return new Builder(this);
  }

  /**
   * What is the cost of boarding an elevator?
   */
  @Deprecated
  public int boardCost() {
    return (int) (boardTime * reluctance);
  }

  /**
   * How long does it take to board an elevator, on average (actually, it probably should be a bit *more*
   * than average, to prevent optimistic trips)? Setting it to "seems like forever," while accurate,
   * will probably prevent OTP from working correctly.
   */
  public int boardTime() {
    return boardTime;
  }

  /**
   * What is the cost of travelling one floor on an elevator?
   */
  @Deprecated
  public int hopCost() {
    return (int) (hopTime * reluctance);
  }

  /** How long does it take to travel one floor on an elevator? */
  public int hopTime() {
    return hopTime;
  }

  public double reluctance() {
    return reluctance;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ElevatorPreferences that = (ElevatorPreferences) o;
    return (
      boardTime == that.boardTime && hopTime == that.hopTime && reluctance == that.reluctance
    );
  }

  @Override
  public int hashCode() {
    return Objects.hash(boardTime, hopTime, reluctance);
  }

  @Override
  public String toString() {
    return ToStringBuilder.of(ElevatorPreferences.class)
      .addDurationSec("boardTime", boardTime, DEFAULT.boardTime)
      .addDurationSec("hopTime", hopTime, DEFAULT.hopTime)
      .addNum("reluctance", reluctance, DEFAULT.reluctance)
      .toString();
  }

  public static class Builder {

    private final ElevatorPreferences original;
    private int boardTime;
    private int hopTime;
    private double reluctance;

    public Builder(ElevatorPreferences original) {
      this.original = original;
      this.boardTime = original.boardTime;
      this.hopTime = original.hopTime;
      this.reluctance = original.reluctance;
    }

    public ElevatorPreferences original() {
      return original;
    }

    public Builder withBoardTime(int boardTime) {
      this.boardTime = boardTime;
      return this;
    }

    public Builder withHopTime(int hopTime) {
      this.hopTime = hopTime;
      return this;
    }

    public Builder withReluctance(double reluctance) {
      this.reluctance = reluctance;
      return this;
    }

    public Builder apply(Consumer<Builder> body) {
      body.accept(this);
      return this;
    }

    ElevatorPreferences build() {
      var value = new ElevatorPreferences(this);
      return original.equals(value) ? original : value;
    }
  }
}
