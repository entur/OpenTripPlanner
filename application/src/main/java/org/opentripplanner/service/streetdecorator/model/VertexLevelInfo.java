package org.opentripplanner.service.streetdecorator.model;

import javax.annotation.Nullable;

public record VertexLevelInfo(@Nullable Level level, long osmVertexId) {}
