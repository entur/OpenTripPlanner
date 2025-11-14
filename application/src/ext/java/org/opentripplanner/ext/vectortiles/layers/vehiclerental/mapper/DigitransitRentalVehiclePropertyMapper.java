package org.opentripplanner.ext.vectortiles.layers.vehiclerental.mapper;

import static org.opentripplanner.ext.vectortiles.layers.vehiclerental.mapper.DigitransitVehicleRentalStationPropertyMapper.getFeedScopedIdAndNetwork;
import static org.opentripplanner.inspector.vector.KeyValue.kv;

import java.util.ArrayList;
import java.util.Collection;
import org.opentripplanner.apis.support.mapping.PropertyMapper;
import org.opentripplanner.framework.i18n.I18NString;
import org.opentripplanner.inspector.vector.KeyValue;
import org.opentripplanner.service.vehiclerental.model.RentalVehicleType;
import org.opentripplanner.service.vehiclerental.model.VehicleRentalVehicle;
import org.opentripplanner.street.model.RentalFormFactor;
import org.opentripplanner.transit.model.framework.FeedScopedId;

public class DigitransitRentalVehiclePropertyMapper extends PropertyMapper<VehicleRentalVehicle> {

  public static final String NAME = "a rental";

  @Override
  protected Collection<KeyValue> map(VehicleRentalVehicle vehicle) {
    var items = new ArrayList<KeyValue>();
    items.addAll(getFeedScopedIdAndNetwork(vehicle));
    items.add(kv("formFactor", vehicle.vehicleType().formFactor()));
    items.add(kv("propulsionType", vehicle.vehicleType().propulsionType()));
    if (vehicle.fuel() != null && vehicle.fuel().percent() != null) {
      items.add(kv("fuelPercentage", vehicle.fuel().percent().asDouble()));
    }
    items.add(kv("pickupAllowed", vehicle.isAllowPickup()));
    return items;
  }

  private static RentalVehicleType vehicleType(RentalFormFactor formFactor) {
    return RentalVehicleType.of()
      .withId(new FeedScopedId("1", formFactor.name()))
      .withName(I18NString.of("bicycle"))
      .withFormFactor(formFactor)
      .withPropulsionType(RentalVehicleType.PropulsionType.HUMAN)
      .withMaxRangeMeters(1000d)
      .build();
  }
}
