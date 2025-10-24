package org.opentripplanner.service.streetdetails.model;

import javax.annotation.Nullable;

/**
 * Represents level information for a vertex. The {@link Level} is nullable because sometimes only
 * the vertical order is known. The osmNodeId is necessary to reliably map the level to the correct
 * vertex.
 */
public record VertexLevelInfo(@Nullable Level level, long osmNodeId) {}
