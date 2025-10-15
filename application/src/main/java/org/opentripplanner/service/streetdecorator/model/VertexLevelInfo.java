package org.opentripplanner.service.streetdecorator.model;

import javax.annotation.Nullable;

public record VertexLevelInfo(@Nullable Double level, @Nullable String name, long osmVertexId) {}
