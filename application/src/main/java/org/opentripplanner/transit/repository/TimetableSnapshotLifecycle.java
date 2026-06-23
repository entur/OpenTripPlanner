package org.opentripplanner.transit.repository;

import org.opentripplanner.framework.transaction.api.RepositoryLifecycle;

public class TimetableSnapshotLifecycle
  implements RepositoryLifecycle<ReadOnlyTimetableSnapshot, MutableTimetableSnapshot> {

  private final MutableTimetableSnapshot buffer;

  public TimetableSnapshotLifecycle(MutableTimetableSnapshot buffer) {
    this.buffer = buffer;
  }

  @Override
  public MutableTimetableSnapshot copyOnWrite(ReadOnlyTimetableSnapshot readOnlySnapshot) {
    return buffer;
  }

  @Override
  public ReadOnlyTimetableSnapshot freeze(MutableTimetableSnapshot mutableSnapshot) {
    return buffer.createReadOnlySnapshot();
  }
}
