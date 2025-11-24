package org.opentripplanner.ext.fares.service.gtfs.v1;

import org.opentripplanner.transit.model.basic.Money;
import org.opentripplanner.transit.model.framework.FeedScopedId;

/** Holds fare and corresponding fareId */
public record FareAndId(Money fare, FeedScopedId fareId) {}
