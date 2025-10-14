package org.opentripplanner.graph_builder.module.osm.naming;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.opentripplanner.framework.i18n.I18NString;
import org.opentripplanner.graph_builder.services.osm.EdgeNamer;
import org.opentripplanner.osm.model.OsmEntity;
import org.opentripplanner.utils.lang.DoubleUtils;
import org.opentripplanner.utils.logging.ProgressTracker;
import org.slf4j.Logger;

/**
 * Base class for namers that use a geo buffer to query geo features.
 */
public abstract class NamerWithGeoBuffer implements EdgeNamer {

  PreciseBufferFactory preciseBufferFactory;

  @Override
  public I18NString name(OsmEntity way) {
    return way.getAssumedName();
  }

  void postprocess(
    Collection<EdgeOnLevel> unnamedEdges,
    int bufferMeters,
    String type,
    Logger logger
  ) {
    ProgressTracker progress = ProgressTracker.track(
      String.format("Assigning names to %s", type),
      500,
      unnamedEdges.size()
    );

    this.preciseBufferFactory = new PreciseBufferFactory(computeEnvelopeCenter(unnamedEdges), bufferMeters);

    final AtomicInteger namesApplied = new AtomicInteger(0);
    unnamedEdges
      .parallelStream()
      .forEach(edgeOnLevel -> {
        var buffer = preciseBufferFactory.preciseBuffer(edgeOnLevel.edge().getGeometry());
        if (assignNameToEdge(edgeOnLevel, buffer)) {
          namesApplied.incrementAndGet();
        }

        // Keep lambda! A method-ref would cause incorrect class and line number to be logged
        // noinspection Convert2MethodRef
        progress.step(m -> logger.info(m));
      });

    logger.info(
      "Assigned names to {} of {} {} ({}%)",
      namesApplied.get(),
      unnamedEdges.size(),
      type,
      DoubleUtils.roundTo2Decimals(((double) namesApplied.get() / unnamedEdges.size()) * 100)
    );

    logger.info(progress.completeMessage());
  }

  /**
   * Implementation-specific logic for naming an edge.
   * @return true if a name was applied, false otherwise.
   */
  abstract boolean assignNameToEdge(EdgeOnLevel edgeOnLevel, Geometry buffer);

  /**
   * Compute the centroid of all sidewalk edges.
   */
  private Coordinate computeEnvelopeCenter(Collection<EdgeOnLevel> edges) {
    var envelope = new Envelope();
    edges.forEach(e -> {
      envelope.expandToInclude(e.edge().getFromVertex().getCoordinate());
      envelope.expandToInclude(e.edge().getToVertex().getCoordinate());
    });
    return envelope.centre();
  }
}
