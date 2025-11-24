package org.opentripplanner.ext.fares.service.gtfs;

import org.opentripplanner.transit.model.basic.Money;
import org.opentripplanner.transit.model.framework.FeedScopedId;

/** Holds fare and corresponding fareId */
public record FareAndId(Money fare, FeedScopedId fareId) {}
