package org.opentripplanner.ext.gbfsgeofencing.configure;

import dagger.Module;
import dagger.Provides;
import jakarta.inject.Singleton;
import javax.annotation.Nullable;
import org.opentripplanner.ext.gbfsgeofencing.internal.GbfsGeofencingRepositoryBuilder;
import org.opentripplanner.ext.gbfsgeofencing.internal.graphbuilder.GbfsGeofencingGraphBuilder;
import org.opentripplanner.routing.graph.Graph;
import org.opentripplanner.standalone.config.BuildConfig;

@Module
public class GbfsGeofencingGraphBuilderModule {

  @Provides
  @Singleton
  @Nullable
  static GbfsGeofencingGraphBuilder provideGbfsGeofencingGraphBuilder(
    BuildConfig config,
    @Nullable GbfsGeofencingRepositoryBuilder repositoryBuilder,
    Graph graph
  ) {
    if (repositoryBuilder == null || !config.gbfsGeofencing.hasFeeds()) {
      return null;
    }

    return new GbfsGeofencingGraphBuilder(config.gbfsGeofencing, repositoryBuilder, graph);
  }
}
