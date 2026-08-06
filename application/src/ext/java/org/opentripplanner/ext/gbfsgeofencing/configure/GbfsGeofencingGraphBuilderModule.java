package org.opentripplanner.ext.gbfsgeofencing.configure;

import dagger.Module;
import dagger.Provides;
import jakarta.inject.Singleton;
import javax.annotation.Nullable;
import org.opentripplanner.ext.gbfsgeofencing.internal.graphbuilder.GbfsGeofencingGraphBuilder;
import org.opentripplanner.service.vehiclerental.VehicleRentalRepository;
import org.opentripplanner.service.vehiclerental.internal.DefaultVehicleRentalRepository;
import org.opentripplanner.standalone.config.BuildConfig;
import org.opentripplanner.street.graph.Graph;

@Module
public class GbfsGeofencingGraphBuilderModule {

  @Provides
  @Singleton
  @Nullable
  static GbfsGeofencingGraphBuilder provideGbfsGeofencingGraphBuilder(
    BuildConfig config,
    Graph graph,
    VehicleRentalRepository rentalRepository
  ) {
    if (!config.gbfsGeofencing.hasFeeds()) {
      return null;
    }

    return new GbfsGeofencingGraphBuilder(
      config.gbfsGeofencing,
      graph,
      (DefaultVehicleRentalRepository) rentalRepository
    );
  }
}
