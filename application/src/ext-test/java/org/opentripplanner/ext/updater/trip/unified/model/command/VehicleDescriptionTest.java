package org.opentripplanner.ext.updater.trip.unified.model.command;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;
import org.opentripplanner.core.model.accessibility.Accessibility;

class VehicleDescriptionTest {

  @Test
  void unknownWhenTheMessageSaysNothing() {
    assertSame(VehicleDescription.unknown(), VehicleDescription.of(null, null));
    assertThat(VehicleDescription.unknown().vehicleId()).isNull();
    assertThat(VehicleDescription.unknown().wheelchairAccessibility()).isNull();
  }

  @Test
  void keepsWhatTheMessageStates() {
    var vehicle = VehicleDescription.of("BUS-42", Accessibility.POSSIBLE);

    assertThat(vehicle.vehicleId()).isEqualTo("BUS-42");
    assertThat(vehicle.wheelchairAccessibility()).isEqualTo(Accessibility.POSSIBLE);
  }
}
