package org.opentripplanner.ext.empiricaldelay.internal.graphbuilder;

import static java.util.Comparator.comparingInt;

import java.util.List;
import java.util.Objects;
import org.opentripplanner.ext.empiricaldelay.EmpiricalDelayRepository;
import org.opentripplanner.ext.empiricaldelay.internal.csvinput.EmpiricalDelayCsvDataReader;
import org.opentripplanner.ext.empiricaldelay.internal.model.DelayAtStopDto;
import org.opentripplanner.ext.empiricaldelay.internal.model.TripDelaysDto;
import org.opentripplanner.ext.empiricaldelay.model.EmpiricalDelay;
import org.opentripplanner.ext.empiricaldelay.parameters.EmpiricalDelayFeedParameters;
import org.opentripplanner.ext.empiricaldelay.parameters.EmpiricalDelayParameters;
import org.opentripplanner.graph_builder.issue.api.DataImportIssueStore;
import org.opentripplanner.graph_builder.model.ConfiguredCompositeDataSource;
import org.opentripplanner.graph_builder.model.GraphBuilderModule;
import org.opentripplanner.transit.model.framework.Deduplicator;
import org.opentripplanner.transit.service.TimetableRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class allows updating the graph with emissions data from external emissions data files.
 */
public class EmpiricalDelayGraphBuilder implements GraphBuilderModule {

  private static final Logger LOG = LoggerFactory.getLogger(EmpiricalDelayGraphBuilder.class);

  private final EmpiricalDelayParameters parameters;
  private final EmpiricalDelayRepository repository;
  private final Iterable<ConfiguredCompositeDataSource<EmpiricalDelayFeedParameters>> dataSources;
  private final ConsistencyValidator validator;

  private final DataImportIssueStore issueStore;
  private final Deduplicator deduplicator;

  public EmpiricalDelayGraphBuilder(
    Iterable<ConfiguredCompositeDataSource<EmpiricalDelayFeedParameters>> dataSources,
    EmpiricalDelayParameters parameters,
    EmpiricalDelayRepository repository,
    TimetableRepository timetableRepository,
    DataImportIssueStore issueStore,
    Deduplicator deduplicator
  ) {
    this.dataSources = Objects.requireNonNull(dataSources);
    this.parameters = Objects.requireNonNull(parameters);
    this.repository = Objects.requireNonNull(repository);
    this.validator = new ConsistencyValidator(timetableRepository, issueStore);
    this.issueStore = Objects.requireNonNull(issueStore);
    this.deduplicator = Objects.requireNonNull(deduplicator);
  }

  public void buildGraph() {
    if (parameters == null) {
      return;
    }

    for (var data : dataSources) {
      var reader = new EmpiricalDelayCsvDataReader(issueStore);
      reader.read(data.dataSource(), data.config().feedId());

      // Add calendar data
      var cal = reader.calendar();
      repository.addEmpiricalDelayServiceCalendar(data.config().feedId(), cal);

      // Validate and add trip data
      for (TripDelaysDto trip : reader.trips()) {
        for (String serviceId : trip.serviceIds()) {
          var delayAtStops = trip.delaysForServiceId(serviceId);
          boolean ok = validator.validate(cal, serviceId, trip.tripId(), delayAtStops);
          if (ok) {
            repository.addEmpiricalDelay(trip.tripId(), serviceId, map(delayAtStops));
          }
        }
      }
    }
    logEmpiricalDelaySummary();
  }

  private List<EmpiricalDelay> map(List<DelayAtStopDto> delayAtStops) {
    var list = delayAtStops
      .stream()
      .sorted(comparingInt(DelayAtStopDto::sequence))
      .map(DelayAtStopDto::empiricalDelay)
      .toList();
    return deduplicator.deduplicateImmutableList(EmpiricalDelay.class, list);
  }

  private void logEmpiricalDelaySummary() {
    LOG.info(repository.summary());
  }
}
