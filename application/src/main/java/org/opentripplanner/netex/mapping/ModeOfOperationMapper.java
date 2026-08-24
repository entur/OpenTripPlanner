package org.opentripplanner.netex.mapping;

import java.util.EnumSet;
import java.util.Set;
import org.entur.netex2.model.Line_VersionStructure;
import org.entur.netex2.model.ModeOfOperationRefStructure;
import org.entur.netex2.model.Network;
import org.entur.netex2.model.VehiclePooling;
import org.entur.netex2.model.VehiclePoolingTypeEnumeration;
import org.jspecify.annotations.Nullable;
import org.opentripplanner.netex.index.api.NetexEntityIndexReadOnlyView;
import org.opentripplanner.netex.mapping.support.NetexMainAndSubMode;
import org.opentripplanner.transit.model.basic.TransitMode;

/**
 * Maps a NeTEx Part 5 {@code ModeOfOperation} to an OTP transit mode.
 * <p>
 * NeTEx has no carpool value in {@code AllVehicleModesOfTransportEnumeration}. Carpooling is
 * instead expressed on a separate classification axis: a {@code VehiclePooling} mode of operation,
 * which the schema defines as "an ALTERNATIVE MODE OF OPERATION of a privately-owned vehicle
 * consisting in sharing the vehicle for a trip between the driver who is at the same time
 * performing a trip and at least another traveller".
 * <p>
 * The reference cannot sit on the Line - {@code Line_VersionStructure} has no
 * {@code ModeOfOperationRef}. It sits on the {@code Network} (or {@code GroupOfLines}) the line
 * belongs to, which the line points at with {@code RepresentedByGroupRef}.
 */
class ModeOfOperationMapper {

  /**
   * The pooling types that describe carpooling. The remaining values of
   * {@link VehiclePoolingTypeEnumeration} are taxi variants which already have a covering
   * {@code TransportMode}, and must not be hijacked.
   */
  private static final Set<VehiclePoolingTypeEnumeration> CARPOOL_TYPES = EnumSet.of(
    VehiclePoolingTypeEnumeration.COMMUTER_CAR_POOLING,
    VehiclePoolingTypeEnumeration.LONG_DISTANCE_CAR_POOLING,
    VehiclePoolingTypeEnumeration.DYNAMIC_CAR_POOLING
  );

  private final NetexEntityIndexReadOnlyView netexIndex;

  ModeOfOperationMapper(NetexEntityIndexReadOnlyView netexIndex) {
    this.netexIndex = netexIndex;
  }

  /**
   * Resolve the mode of operation for a line, if it has one that overrides the transport mode.
   *
   * @return {@code null} when the line has no mode of operation, when it cannot be resolved, or
   * when it does not map to an OTP mode. The line then keeps its own {@code TransportMode}.
   */
  @Nullable
  NetexMainAndSubMode mapLine(Line_VersionStructure line) {
    VehiclePooling vehiclePooling = findVehiclePooling(line);
    return vehiclePooling == null ? null : map(vehiclePooling);
  }

  /**
   * Map a VehiclePooling to a mode. The pooling type is carried over as the OTP sub-mode so the
   * three flavours of carpooling stay distinguishable in the API.
   */
  @Nullable
  static NetexMainAndSubMode map(VehiclePooling vehiclePooling) {
    var type = vehiclePooling.getVehiclePoolingType();
    if (type == null || !CARPOOL_TYPES.contains(type)) {
      return null;
    }
    return new NetexMainAndSubMode(TransitMode.CARPOOL, type.value());
  }

  @Nullable
  private VehiclePooling findVehiclePooling(Line_VersionStructure line) {
    if (line.getRepresentedByGroupRef() == null) {
      return null;
    }
    Network network = netexIndex.lookupNetworkForLine(line.getRepresentedByGroupRef().getRef());
    if (network == null || network.getModeOfOperationRef() == null) {
      return null;
    }
    ModeOfOperationRefStructure ref = network.getModeOfOperationRef().getValue();
    return ref == null ? null : netexIndex.getVehiclePoolingById().lookup(ref.getRef());
  }
}
