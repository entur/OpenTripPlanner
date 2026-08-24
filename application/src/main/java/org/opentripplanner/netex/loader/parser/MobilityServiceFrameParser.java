package org.opentripplanner.netex.loader.parser;

import jakarta.xml.bind.JAXBElement;
import java.util.ArrayList;
import java.util.Collection;
import org.entur.netex2.model.MobilityService_VersionFrameStructure;
import org.entur.netex2.model.ModesOfOperationRelStructure;
import org.entur.netex2.model.VehicleMeetingPoint;
import org.entur.netex2.model.VehicleMeetingPointsInFrame_RelStructure;
import org.entur.netex2.model.VehiclePooling;
import org.jspecify.annotations.Nullable;
import org.opentripplanner.netex.index.NetexEntityIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parses the NeTEx Part 5 "New Modes" MobilityServiceFrame.
 * <p>
 * Only the two elements OTP has a use for are read:
 * <ul>
 *   <li>{@code modesOfOperation} - the {@link VehiclePooling} entries. A VehiclePooling is
 *       referenced from a {@code Network} and classifies its lines as carpooling. NeTEx has no
 *       carpool value in {@code AllVehicleModesOfTransportEnumeration}; the mode of operation is
 *       a separate classification axis, and this is where carpooling lives.</li>
 *   <li>{@code vehicleMeetingPoints} - stop places given as nothing but a name and a coordinate.
 *       Carpool meeting points are created by the service provider, are short-lived and exist in
 *       large numbers, so they deliberately have no StopPlace, no Quay and no entry in a national
 *       stop register.</li>
 * </ul>
 * Only those two are read. The frame carries a lot more - mobility services, online services,
 * fleets, pools of vehicles, meeting places, constraint zones - but all of it describes commerce
 * and vehicle inventory rather than routing, so it is deliberately passed over in silence.
 */
class MobilityServiceFrameParser extends NetexParser<MobilityService_VersionFrameStructure> {

  private static final Logger LOG = LoggerFactory.getLogger(MobilityServiceFrameParser.class);

  private final Collection<VehiclePooling> vehiclePoolings = new ArrayList<>();
  private final Collection<VehicleMeetingPoint> vehicleMeetingPoints = new ArrayList<>();

  @Override
  void parse(MobilityService_VersionFrameStructure frame) {
    parseModesOfOperation(frame.getModesOfOperation());
    parseVehicleMeetingPoints(frame.getVehicleMeetingPoints());

    // The rest of the frame - mobility services, online services, fleets, pools of vehicles,
    // meeting places and constraint zones - is out of scope, and is passed over without a warning.
    // Unlike the Part 1 frames there is no expectation that OTP reads a MobilityServiceFrame in
    // full: most of it describes commerce and vehicle inventory, not routing.

    verifyCommonUnusedPropertiesIsNotSet(LOG, frame);
  }

  @Override
  void setResultOnIndex(NetexEntityIndex netexIndex) {
    netexIndex.vehiclePoolingById.addAll(vehiclePoolings);
    netexIndex.vehicleMeetingPointById.addAll(vehicleMeetingPoints);
  }

  /* private methods */

  private void parseModesOfOperation(@Nullable ModesOfOperationRelStructure elements) {
    if (elements == null) {
      return;
    }
    for (JAXBElement<?> e : elements.getModeOfOperation()) {
      if (e.getValue() instanceof VehiclePooling vehiclePooling) {
        vehiclePoolings.add(vehiclePooling);
      } else {
        // VehicleSharing, VehicleRental and the conventional/personal modes are valid NeTEx, but
        // OTP has no consumer for them yet.
        warnOnMissingMapping(LOG, e.getValue());
      }
    }
  }

  private void parseVehicleMeetingPoints(
    @Nullable VehicleMeetingPointsInFrame_RelStructure elements
  ) {
    if (elements == null) {
      return;
    }
    vehicleMeetingPoints.addAll(elements.getVehicleMeetingPoint());
  }
}
