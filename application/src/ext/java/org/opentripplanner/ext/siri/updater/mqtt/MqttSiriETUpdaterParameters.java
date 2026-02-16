package org.opentripplanner.ext.siri.updater.mqtt;

import java.nio.file.Path;
import java.time.Duration;
import javax.annotation.Nullable;
import org.opentripplanner.updater.trip.UrlUpdaterParameters;

public class MqttSiriETUpdaterParameters implements UrlUpdaterParameters {

  private final String configRef;
  private final String feedId;
  private final String host;
  private final int port;
  private final String user;
  private final String password;
  private final String topic;
  private final int qos;
  private final boolean fuzzyTripMatching;
  private final int numberOfPrimingWorkers;
  private final Duration maxPrimingIdleTime;
  private final boolean useNewUpdaterImplementation;
  private final boolean shadowComparison;

  @Nullable
  private final Path shadowComparisonReportDirectory;

  public MqttSiriETUpdaterParameters(
    String configRef,
    String feedId,
    String host,
    int port,
    String user,
    String password,
    String topic,
    int qos,
    boolean fuzzyTripMatching,
    int numberOfPrimingWorkers,
    Duration maxPrimingIdleTime,
    boolean useNewUpdaterImplementation,
    boolean shadowComparison,
    @Nullable Path shadowComparisonReportDirectory
  ) {
    this.configRef = configRef;
    this.feedId = feedId;
    this.host = host;
    this.port = port;
    this.user = user;
    this.password = password;
    this.topic = topic;
    this.qos = qos;
    this.fuzzyTripMatching = fuzzyTripMatching;
    this.numberOfPrimingWorkers = numberOfPrimingWorkers;
    this.maxPrimingIdleTime = maxPrimingIdleTime;
    this.useNewUpdaterImplementation = useNewUpdaterImplementation;
    this.shadowComparison = shadowComparison;
    this.shadowComparisonReportDirectory = shadowComparisonReportDirectory;
  }

  @Override
  public String url() {
    return host + ":" + port;
  }

  @Override
  public String configRef() {
    return configRef;
  }

  @Override
  public String feedId() {
    return feedId;
  }

  public String topic() {
    return topic;
  }

  public int qos() {
    return qos;
  }

  public boolean fuzzyTripMatching() {
    return fuzzyTripMatching;
  }

  public String user() {
    return user;
  }

  public String password() {
    return password;
  }

  public int port() {
    return port;
  }

  public String host() {
    return host;
  }

  public int numberOfPrimingWorkers() {
    return numberOfPrimingWorkers;
  }

  public Duration maxPrimingIdleTime() {
    return maxPrimingIdleTime;
  }

  public boolean useNewUpdaterImplementation() {
    return useNewUpdaterImplementation;
  }

  public boolean shadowComparison() {
    return shadowComparison;
  }

  @Nullable
  public Path shadowComparisonReportDirectory() {
    return shadowComparisonReportDirectory;
  }
}
