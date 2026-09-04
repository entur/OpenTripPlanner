package org.opentripplanner.netex.mapping;

import static com.google.common.truth.Truth.assertThat;

import java.util.Set;
import org.entur.netex2.model.GroupOfLinesRefStructure;
import org.entur.netex2.model.Line;
import org.entur.netex2.model.Network;
import org.entur.netex2.model.ObjectFactory;
import org.entur.netex2.model.VehiclePooling;
import org.entur.netex2.model.VehiclePoolingModeOfOperationRefStructure;
import org.entur.netex2.model.VehiclePoolingTypeEnumeration;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.opentripplanner.netex.index.NetexEntityIndex;
import org.opentripplanner.transit.model.basic.TransitMode;

/**
 * Carpooling is not a NeTEx transport mode. It arrives as a Part 5 ModeOfOperation on the Network
 * the line belongs to, which makes the resolution a three step lookup - line to network, network to
 * mode of operation, mode of operation to pooling type. Every step can come back empty, and none of
 * them may be allowed to override the line's own transport mode when it does.
 */
class ModeOfOperationMapperTest {

  private static final ObjectFactory OBJECT_FACTORY = new ObjectFactory();

  private static final String NETWORK_ID = "NBG:Network:1";
  private static final String POOLING_ID = "NBG:VehiclePooling:1";

  /** The values of {@link VehiclePoolingTypeEnumeration} that describe carpooling. */
  private static final Set<String> CARPOOL_VALUES = Set.of(
    "commuterCarPooling",
    "longDistanceCarPooling",
    "dynamicCarPooling"
  );

  /**
   * The remaining pooling types are taxi variants which already have a covering TransportMode.
   * Mapping those to CARPOOL would silently reclassify ordinary taxi services.
   */
  @ParameterizedTest
  @EnumSource(VehiclePoolingTypeEnumeration.class)
  void onlyCarpoolTypesMapToCarpool(VehiclePoolingTypeEnumeration type) {
    var result = ModeOfOperationMapper.map(new VehiclePooling().withVehiclePoolingType(type));

    if (CARPOOL_VALUES.contains(type.value())) {
      assertThat(result).isNotNull();
      assertThat(result.mainMode()).isEqualTo(TransitMode.CARPOOL);
      assertThat(result.subMode()).isEqualTo(type.value());
    } else {
      assertThat(result).isNull();
    }
  }

  @Test
  void poolingWithoutTypeDoesNotMap() {
    assertThat(ModeOfOperationMapper.map(new VehiclePooling())).isNull();
  }

  @Test
  void resolvesCarpoolThroughTheNetwork() {
    var mapper = mapper(
      network(POOLING_ID),
      pooling(VehiclePoolingTypeEnumeration.COMMUTER_CAR_POOLING)
    );

    var result = mapper.mapLine(line(NETWORK_ID));

    assertThat(result).isNotNull();
    assertThat(result.mainMode()).isEqualTo(TransitMode.CARPOOL);
    assertThat(result.subMode()).isEqualTo("commuterCarPooling");
  }

  @Test
  void lineWithoutGroupRefKeepsItsOwnMode() {
    var mapper = mapper(
      network(POOLING_ID),
      pooling(VehiclePoolingTypeEnumeration.COMMUTER_CAR_POOLING)
    );

    assertThat(mapper.mapLine(line(null))).isNull();
  }

  @Test
  void unknownNetworkKeepsItsOwnMode() {
    var mapper = mapper(
      network(POOLING_ID),
      pooling(VehiclePoolingTypeEnumeration.COMMUTER_CAR_POOLING)
    );

    assertThat(mapper.mapLine(line("NBG:Network:does-not-exist"))).isNull();
  }

  @Test
  void networkWithoutModeOfOperationKeepsItsOwnMode() {
    var mapper = mapper(network(null), pooling(VehiclePoolingTypeEnumeration.COMMUTER_CAR_POOLING));

    assertThat(mapper.mapLine(line(NETWORK_ID))).isNull();
  }

  /**
   * A dangling reference must not be treated as "no carpooling" silently mapping to something else
   * - it maps to nothing, and the line keeps the mode it declared itself.
   */
  @Test
  void danglingPoolingReferenceKeepsItsOwnMode() {
    var mapper = mapper(network("NBG:VehiclePooling:does-not-exist"), null);

    assertThat(mapper.mapLine(line(NETWORK_ID))).isNull();
  }

  /* helpers */

  private static ModeOfOperationMapper mapper(Network network, @Nullable VehiclePooling pooling) {
    var index = new NetexEntityIndex();
    index.networkById.add(network);
    if (pooling != null) {
      index.vehiclePoolingById.add(pooling);
    }
    return new ModeOfOperationMapper(index.readOnlyView());
  }

  private static Network network(@Nullable String poolingRef) {
    var network = new Network().withId(NETWORK_ID).withVersion("1");
    if (poolingRef != null) {
      network.setModeOfOperationRef(
        OBJECT_FACTORY.createVehiclePoolingRef(
          new VehiclePoolingModeOfOperationRefStructure().withRef(poolingRef)
        )
      );
    }
    return network;
  }

  private static VehiclePooling pooling(VehiclePoolingTypeEnumeration type) {
    return new VehiclePooling().withId(POOLING_ID).withVersion("1").withVehiclePoolingType(type);
  }

  private static Line line(@Nullable String groupRef) {
    var line = new Line().withId("NBG:Line:1").withVersion("1");
    return groupRef == null
      ? line
      : line.withRepresentedByGroupRef(new GroupOfLinesRefStructure().withRef(groupRef));
  }
}
