package org.opentripplanner.routing.linking.configure;

import static org.opentripplanner.routing.linking.VisibilityMode.COMPUTE_AREA_VISIBILITY_LINES;

import dagger.Module;
import dagger.Provides;
import org.opentripplanner.routing.graph.Graph;
import org.opentripplanner.routing.linking.LinkingContextFactory;
import org.opentripplanner.routing.linking.VertexLinker;
import org.opentripplanner.street.model.StreetLimitationParameters;
import org.opentripplanner.transit.service.SiteRepository;

@Module
public class LinkingServiceModule {

  @Provides
  static VertexLinker provideVertexLinker(
    Graph graph,
    StreetLimitationParameters streetLimitationParameters
  ) {
    return new VertexLinker(
      graph,
      COMPUTE_AREA_VISIBILITY_LINES,
      streetLimitationParameters.maxAreaNodes()
    );
  }

  @Provides
  static LinkingContextFactory provideLinkingContextFactory(
    Graph graph,
    SiteRepository siteRepository,
    VertexLinker vertexLinker
  ) {
    return new LinkingContextFactory(graph, vertexLinker, siteRepository::findStopOrChildIds);
  }
}
