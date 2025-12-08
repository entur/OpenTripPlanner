package org.opentripplanner.street.model.vertex;

import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.framework.geometry.WgsCoordinate;
import org.opentripplanner.framework.i18n.I18NString;

public class TransitPathwayNodeVertex extends StationElementVertex {

  public TransitPathwayNodeVertex(FeedScopedId id, WgsCoordinate coordinate, I18NString name) {
    super(id, coordinate.longitude(), coordinate.latitude(), name);
  }
}
