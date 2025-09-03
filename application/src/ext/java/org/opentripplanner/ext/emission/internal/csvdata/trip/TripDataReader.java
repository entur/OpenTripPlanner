package org.opentripplanner.ext.emission.internal.csvdata.trip;

import java.util.ArrayList;
import java.util.List;
import org.opentripplanner.datastore.api.DataSource;
import org.opentripplanner.framework.csv.parser.OtpCsvReader;
import org.opentripplanner.graph_builder.issue.api.DataImportIssueStore;
import org.slf4j.Logger;

/**
 * This class handles reading the CO₂ emissions data from the files in the GTFS package
 * and saving it in a map.
 */
public class TripDataReader {

  private final DataSource emissionDataSource;
  private final DataImportIssueStore issueStore;
  private boolean dataProcessed = false;

  public TripDataReader(DataSource emissionDataSource, DataImportIssueStore issueStore) {
    this.emissionDataSource = emissionDataSource;
    this.issueStore = issueStore;
  }

  public List<TripHopsRow> read(Logger logger) {
    var emissionData = new ArrayList<TripHopsRow>();
    OtpCsvReader.<TripHopsRow>of()
      .withLogger(logger)
      .withDataSource(emissionDataSource)
      .withParserFactory(r -> new TripHopsCsvParser(issueStore, r))
      .withRowHandler(row -> {
        emissionData.add(row);
        dataProcessed = true;
      })
      .read();
    return emissionData;
  }

  public boolean isDataProcessed() {
    return dataProcessed;
  }
}
