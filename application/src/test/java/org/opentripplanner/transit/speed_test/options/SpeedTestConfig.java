package org.opentripplanner.transit.speed_test.options;

import java.io.File;
import java.net.URI;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;
import org.opentripplanner.standalone.config.framework.file.ConfigFileLoader;
import org.opentripplanner.standalone.config.framework.json.NodeAdapter;
import org.opentripplanner.utils.tostring.ToStringBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SpeedTestConfig {

  private static final Logger LOG = LoggerFactory.getLogger(SpeedTestConfig.class);
  private static final String FILE_NAME = "speed-test-config.json";
  private static final SpeedTestConfig DEFAULT = new SpeedTestConfig();

  /**
   * The test date is the date used for all test cases. The default value is today.
   */
  public final LocalDate testDate;

  /** The speed test run all its test on an existing pre-build graph. */
  public final URI graph;
  public final String feedId;
  public final boolean ignoreStreetResults;

  public SpeedTestConfig() {
    this.testDate = null;
    this.graph = URI.create("graph.obj");
    this.feedId = "F";
    this.ignoreStreetResults = true;
  }

  public SpeedTestConfig(Builder builder) {
    this.testDate = Objects.requireNonNull(builder.testDate);
    this.graph = Objects.requireNonNull(builder.graph);
    this.feedId = Objects.requireNonNull(builder.feedId);
    this.ignoreStreetResults = Objects.requireNonNull(builder.ignoreStreetResults);
  }

  public static SpeedTestConfig.Builder of() {
    return new Builder(DEFAULT);
  }

  public static SpeedTestConfig config(File dir) {
    var fileLoader = ConfigFileLoader.of().withConfigDir(dir);
    var json = fileLoader.loadFromFile(FILE_NAME);
    return SpeedTestConfig.createFromConfig(new NodeAdapter(json, FILE_NAME));
  }

  public SpeedTestConfig.Builder copyOf() {
    return new Builder(this);
  }

  /**
   * Load SpeedTest configuration form the given JSON Adaptor. If a routerConfig is provided
   * that config is used, if not relevant router config nodes are loaded from the   the config is loaded from the
   */
  public static SpeedTestConfig createFromConfig(NodeAdapter adapter) {
    var builder = of()
      .withFeedId(adapter.of("feedId").asString())
      .withTestDate(adapter.of("testDate").asDateOrRelativePeriod("PT0D", ZoneId.of("UTC")))
      .withGraph(adapter.of("graph").asUri(null))
      .withIgnoreStreetResults(adapter.of("ignoreStreetResults").asBoolean(false));

    adapter.logAllWarnings(LOG::warn);

    return builder.build();
  }

  @Override
  public String toString() {
    return ToStringBuilder.of(getClass())
      .addDate("testDate", testDate)
      .addObj("graph", graph)
      .addStr("feedId", feedId)
      .addBoolIfTrue("ignoreStreetResults", ignoreStreetResults)
      .toString();
  }

  public static class Builder {

    public LocalDate testDate;
    public URI graph;
    public String feedId;
    public boolean ignoreStreetResults;

    Builder(SpeedTestConfig original) {
      if (original != null) {
        this.testDate = original.testDate;
        this.graph = original.graph;
        this.feedId = original.feedId;
        this.ignoreStreetResults = original.ignoreStreetResults;
      }
    }

    public Builder withTestDate(LocalDate testDate) {
      this.testDate = testDate;
      return this;
    }

    public Builder withGraph(URI graph) {
      this.graph = graph;
      return this;
    }

    public Builder withFeedId(String feedId) {
      this.feedId = feedId;
      return this;
    }

    public Builder withIgnoreStreetResults(boolean ignoreStreetResults) {
      this.ignoreStreetResults = ignoreStreetResults;
      return this;
    }

    public SpeedTestConfig build() {
      return new SpeedTestConfig(this);
    }
  }
}
