package org.opentripplanner.standalone.configure;

import static com.google.common.truth.Truth.assertThat;

import dagger.BindsInstance;
import dagger.Component;
import jakarta.inject.Singleton;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.opentripplanner.framework.transaction.RepositoryRegistry;
import org.opentripplanner.framework.transaction.api.RepositoryHandle;
import org.opentripplanner.framework.transaction.internal.TransactionFactory;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.RaptorTransitDataTestFactory;
import org.opentripplanner.transit.model.calendar.DefaultTripCalendars;
import org.opentripplanner.transit.model.timetable.TimetableSnapshot;
import org.opentripplanner.transit.repository.MutableTimetableSnapshot;
import org.opentripplanner.transit.repository.ReadOnlyTimetableSnapshot;
import org.opentripplanner.transit.repository.TimetableSnapshotLifecycle;
import org.opentripplanner.transit.service.TimetableRepository;

/**
 * Verifies the real Dagger scoping added for issue #7441: bindings inside one {@link
 * RequestScopedFactory} build (one simulated HTTP request) are cached and shared, while two
 * separate builds (two requests) get independent instances.
 */
class RequestScopedFactoryTest {

  @Test
  void transitServiceIsCachedWithinOneRequestButNotAcrossRequests() {
    var repositoryRegistry = TransactionFactory.createRepositoryRegistry();
    var timetableSnapshot = new TimetableSnapshot(
      RaptorTransitDataTestFactory.empty(),
      new DefaultTripCalendars()
    );
    var timetableRepositoryHandle = repositoryRegistry.registerRepositorySnapshot(
      timetableSnapshot,
      new TimetableSnapshotLifecycle(timetableSnapshot, false, () -> LocalDate.of(2026, 1, 1))
    );
    var factory = DaggerRequestScopedFactoryTest_TestFactory.builder()
      .timetableRepository(new TimetableRepository())
      .repositoryRegistry(repositoryRegistry)
      .timetableRepositoryHandle(timetableRepositoryHandle)
      .build();

    var requestOne = factory.requestScopedFactoryBuilder().build();
    assertThat(requestOne.transitService()).isSameInstanceAs(requestOne.transitService());
    assertThat(requestOne.transactionScope()).isSameInstanceAs(requestOne.transactionScope());

    var requestTwo = factory.requestScopedFactoryBuilder().build();
    assertThat(requestOne.transitService()).isNotSameInstanceAs(requestTwo.transitService());
  }

  @Singleton
  @Component(modules = ConstructApplicationModule.class)
  interface TestFactory {
    RequestScopedFactory.Builder requestScopedFactoryBuilder();

    @Component.Builder
    interface Builder {
      @BindsInstance
      Builder timetableRepository(TimetableRepository timetableRepository);

      @BindsInstance
      Builder repositoryRegistry(RepositoryRegistry repositoryRegistry);

      @BindsInstance
      Builder timetableRepositoryHandle(
        RepositoryHandle<
          ReadOnlyTimetableSnapshot,
          MutableTimetableSnapshot
        > timetableRepositoryHandle
      );

      TestFactory build();
    }
  }
}
