package org.opentripplanner;

import org.opentripplanner.ext.fares.impl.gtfs.DefaultFareServiceFactory;
import org.opentripplanner.routing.fares.FareServiceFactory;
import org.opentripplanner.routing.graph.Graph;
import org.opentripplanner.transfer.TransferRepository;
import org.opentripplanner.transfer.TransferServiceTestFactory;
import org.opentripplanner.transit.service.TimetableRepository;

public record TestOtpModel(
  Graph graph,
  TimetableRepository timetableRepository,
  TransferRepository transferRepository,
  FareServiceFactory fareServiceFactory
) {
  public TestOtpModel(Graph graph, TimetableRepository timetableRepository) {
    this(
      graph,
      timetableRepository,
      TransferServiceTestFactory.defaultTransferRepository(),
      new DefaultFareServiceFactory()
    );
  }

  public TestOtpModel index() {
    timetableRepository.index();
    transferRepository.index();
    graph.index();
    return this;
  }
}
