package org.opentripplanner.service.vehiclerental;

import java.io.Serializable;
import java.util.Collection;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.service.vehiclerental.model.VehicleRentalPlace;
import org.opentripplanner.service.vehiclerental.street.geofencing.GeofencingZoneIndex;

/**
 * The writable data store of vehicle rental information. Also exposes raw read access and
 * geofencing zone queries (via {@link GeofencingZoneService}); the higher-level
 * {@link VehicleRentalService} provides typed views on top of this.
 * <p>
 * Geofencing zones may be written here during graph build, in which case the repository is saved
 * into the serialized graph object.
 */
public interface VehicleRentalRepository extends GeofencingZoneService, Serializable {
  void addVehicleRentalStation(VehicleRentalPlace vehicleRentalStation);

  void removeVehicleRentalStation(FeedScopedId vehicleRentalStationId);

  /**
   * Register a geofencing zone index for a specific data source. Called by the vehicle rental
   * updater when zones are applied. Each updater (one per network) registers its own index;
   * re-registering with the same {@code dataSourceName} replaces the previous index.
   */
  void setGeofencingZoneIndex(String network, GeofencingZoneIndex index);

  Collection<VehicleRentalPlace> listRentalPlaces();

  VehicleRentalPlace getRentalPlace(FeedScopedId id);
}
