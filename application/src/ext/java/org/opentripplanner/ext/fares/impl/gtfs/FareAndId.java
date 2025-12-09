package org.opentripplanner.ext.fares.impl.gtfs;

import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.transit.model.basic.Money;

/** Holds fare and corresponding fareId */
public record FareAndId(Money fare, FeedScopedId fareId) {}
