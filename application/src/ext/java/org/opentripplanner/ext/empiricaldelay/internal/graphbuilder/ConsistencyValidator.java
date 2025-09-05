package org.opentripplanner.ext.empiricaldelay.internal.graphbuilder;

import java.util.List;
import org.opentripplanner.ext.empiricaldelay.internal.model.DelayAtStopDto;
import org.opentripplanner.ext.empiricaldelay.model.calendar.EmpiricalDelayCalendar;
import org.opentripplanner.graph_builder.issue.api.DataImportIssueStore;
import org.opentripplanner.transit.model.framework.FeedScopedId;
import org.opentripplanner.transit.service.TimetableRepository;

class ConsistencyValidator {

  private final TimetableRepository timetableRepository;
  private final DataImportIssueStore issueStore;

  public ConsistencyValidator(
    TimetableRepository timetableRepository,
    DataImportIssueStore issueStore
  ) {
    this.timetableRepository = timetableRepository;
    this.issueStore = issueStore;
  }

  boolean validate(
    EmpiricalDelayCalendar cal,
    String serviceId,
    FeedScopedId tripId,
    List<DelayAtStopDto> delayAtStops
  ) {
    // TODO - Empirical Delay
    //        Validate trip exist and each stop id match
    return true;
  }
}
