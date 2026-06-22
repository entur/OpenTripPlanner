package org.opentripplanner.updater.trip.gtfs.model;

import static com.google.common.truth.Truth.assertThat;

import com.google.transit.realtime.GtfsRealtime;
import java.time.LocalTime;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TripDescriptorTest {

  @ParameterizedTest
  @ValueSource(strings = {"12:00:00", "12:00"})
  void startTime(String time){
    var raw = GtfsRealtime.TripDescriptor.newBuilder().setStartTime(time).build();
    var descriptor = new TripDescriptor(raw);

    assertThat(descriptor.startTime()).hasValue(LocalTime.of(12, 0));
  }

}