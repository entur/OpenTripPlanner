package org.opentripplanner.updater.vehicle_position;

import com.google.transit.realtime.GtfsRealtime.VehiclePosition;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nullable;
import org.opentripplanner.service.realtimevehicles.RealtimeVehicleRepository;
import org.opentripplanner.standalone.config.routerconfig.updaters.VehiclePositionsUpdaterConfig;
import org.opentripplanner.transit.service.TransitService;
import org.opentripplanner.updater.GraphWriterRunnable;
import org.opentripplanner.updater.trip.gtfs.GtfsRealtimeFuzzyTripMatcher;

class VehiclePositionUpdaterRunnable implements GraphWriterRunnable {

  private final List<VehiclePosition> updates;
  private final RealtimeVehicleRepository realtimeVehicleRepository;
  private final TransitService transitService;
  private final String feedId;

  @Nullable
  private final GtfsRealtimeFuzzyTripMatcher fuzzyTripMatcher;

  private final Set<VehiclePositionsUpdaterConfig.VehiclePositionFeature> vehiclePositionFeatures;

  public VehiclePositionUpdaterRunnable(
    RealtimeVehicleRepository realtimeVehicleRepository,
    TransitService transitService,
    Set<VehiclePositionsUpdaterConfig.VehiclePositionFeature> vehiclePositionFeatures,
    String feedId,
    @Nullable GtfsRealtimeFuzzyTripMatcher fuzzyTripMatcher,
    List<VehiclePosition> updates
  ) {
    this.updates = Objects.requireNonNull(updates);
    this.feedId = feedId;
    this.realtimeVehicleRepository = realtimeVehicleRepository;
    this.transitService = transitService;
    this.fuzzyTripMatcher = fuzzyTripMatcher;
    this.vehiclePositionFeatures = vehiclePositionFeatures;
  }

  @Override
  public void run() {
    RealtimeVehiclePatternMatcher matcher = new RealtimeVehiclePatternMatcher(
      feedId,
      transitService::getTrip,
      transitService::findPattern,
      transitService::findPattern,
      transitService.getCalendarService()::getServiceDatesForServiceId,
      realtimeVehicleRepository,
      transitService.getTimeZone(),
      fuzzyTripMatcher,
      vehiclePositionFeatures
    );
    // Apply new vehicle positions
    matcher.applyRealtimeVehicleUpdates(updates);
  }
}
