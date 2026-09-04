package org.opentripplanner.netex.loader.parser;

import static com.google.common.truth.Truth.assertThat;

import org.entur.netex2.model.MobilityServiceFrame;
import org.entur.netex2.model.ModesOfOperationRelStructure;
import org.entur.netex2.model.ObjectFactory;
import org.entur.netex2.model.VehicleMeetingPoint;
import org.entur.netex2.model.VehicleMeetingPointsInFrame_RelStructure;
import org.entur.netex2.model.VehiclePooling;
import org.entur.netex2.model.VehiclePoolingTypeEnumeration;
import org.junit.jupiter.api.Test;
import org.opentripplanner.netex.index.NetexEntityIndex;

/**
 * The MobilityServiceFrame carries far more than OTP reads - mobility services, online services,
 * fleets, pools of vehicles, meeting places and constraint zones. The parser takes two elements and
 * passes over the rest without complaint, which is a deliberate departure from how the Part 1
 * frames are parsed. These tests hold that scope in place.
 */
class MobilityServiceFrameParserTest {

  private static final ObjectFactory OBJECT_FACTORY = new ObjectFactory();

  private static final String POOLING_ID = "NBG:VehiclePooling:1";
  private static final String MEETING_POINT_ID = "NBG:VehicleMeetingPoint:1";

  @Test
  void readsVehiclePooling() {
    var frame = OBJECT_FACTORY.createMobilityServiceFrame();
    var pooling = new VehiclePooling()
      .withId(POOLING_ID)
      .withVersion("1")
      .withVehiclePoolingType(VehiclePoolingTypeEnumeration.COMMUTER_CAR_POOLING);

    frame.setModesOfOperation(new ModesOfOperationRelStructure());
    frame
      .getModesOfOperation()
      .getModeOfOperation()
      .add(OBJECT_FACTORY.createVehiclePooling(pooling));

    var index = parse(frame);

    assertThat(index.vehiclePoolingById.lookup(POOLING_ID)).isNotNull();
    assertThat(index.vehiclePoolingById.lookup(POOLING_ID).getVehiclePoolingType()).isEqualTo(
      VehiclePoolingTypeEnumeration.COMMUTER_CAR_POOLING
    );
  }

  @Test
  void readsVehicleMeetingPoints() {
    var frame = OBJECT_FACTORY.createMobilityServiceFrame();
    frame.setVehicleMeetingPoints(
      new VehicleMeetingPointsInFrame_RelStructure().withVehicleMeetingPoint(
        new VehicleMeetingPoint().withId(MEETING_POINT_ID).withVersion("1")
      )
    );

    var index = parse(frame);

    assertThat(index.vehicleMeetingPointById.lookup(MEETING_POINT_ID)).isNotNull();
  }

  /**
   * A frame that carries none of the two elements OTP reads is legal NeTEx and must load without
   * complaint - the frame exists for other consumers too.
   */
  @Test
  void emptyFrameIsAccepted() {
    var index = parse(OBJECT_FACTORY.createMobilityServiceFrame());

    assertThat(index.vehiclePoolingById.localKeys()).isEmpty();
    assertThat(index.vehicleMeetingPointById.localKeys()).isEmpty();
  }

  private static NetexEntityIndex parse(MobilityServiceFrame frame) {
    var parser = new MobilityServiceFrameParser();
    var index = new NetexEntityIndex();

    parser.parse(frame);
    parser.setResultOnIndex(index);

    return index;
  }
}
