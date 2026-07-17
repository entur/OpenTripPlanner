package org.opentripplanner.ext.flexbooking.configure;

import dagger.Module;
import dagger.Provides;
import jakarta.inject.Singleton;
import javax.annotation.Nullable;
import org.opentripplanner.ext.carpooling.util.CarReachableVertexSnapper;
import org.opentripplanner.ext.flexbooking.FlexBookingRepository;
import org.opentripplanner.ext.flexbooking.FlexBookingService;
import org.opentripplanner.ext.flexbooking.internal.DefaultFlexBookingRepository;
import org.opentripplanner.ext.flexbooking.internal.DefaultFlexBookingService;
import org.opentripplanner.framework.application.OTPFeature;
import org.opentripplanner.routing.linking.internal.VertexCreationService;
import org.opentripplanner.street.service.StreetLimitationParametersService;
import org.opentripplanner.transit.configure.StaticTransitService;
import org.opentripplanner.transit.service.TransitService;

@Module
public class FlexBookingModule {

  @Provides
  @Singleton
  @Nullable
  public FlexBookingRepository provideFlexBookingRepository() {
    if (OTPFeature.FlexBooking.isOff()) {
      return null;
    }
    return new DefaultFlexBookingRepository();
  }

  /**
   * The service reads only static transit structures (flex index, calendars, area stops) — the
   * real-time tours come from the {@link FlexBookingRepository} — so it takes the app-singleton
   * {@link StaticTransitService} rather than a request-scoped one.
   */
  @Provides
  @Nullable
  public static FlexBookingService provideFlexBookingService(
    @Nullable FlexBookingRepository repository,
    StreetLimitationParametersService streetLimitationParametersService,
    @StaticTransitService TransitService transitService,
    VertexCreationService vertexCreationService,
    @Nullable CarReachableVertexSnapper carReachableVertexSnapper
  ) {
    if (OTPFeature.FlexBooking.isOff()) {
      return null;
    }
    return new DefaultFlexBookingService(
      repository,
      streetLimitationParametersService,
      transitService,
      vertexCreationService,
      carReachableVertexSnapper
    );
  }
}
