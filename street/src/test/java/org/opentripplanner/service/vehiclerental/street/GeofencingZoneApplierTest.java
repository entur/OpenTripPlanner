package org.opentripplanner.service.vehiclerental.street;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opentripplanner.core.model.id.FeedScopedIdFactory.id;
import static org.opentripplanner.street.model.StreetModelFactory.intersectionVertex;
import static org.opentripplanner.street.model.StreetModelFactory.streetEdge;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Polygon;
import org.opentripplanner.service.vehiclerental.model.GeofencingZone;
import org.opentripplanner.street.geometry.GeometryUtils;
import org.opentripplanner.street.geometry.Polygons;
import org.opentripplanner.street.model.edge.Edge;
import org.opentripplanner.street.model.edge.StreetEdge;
import org.opentripplanner.street.model.vertex.StreetVertex;

class GeofencingZoneApplierTest {

  StreetVertex insideFrognerPark1 = intersectionVertex(59.928667, 10.699322);
  StreetVertex insideFrognerPark2 = intersectionVertex(59.9245634, 10.703902);
  StreetVertex outsideFrognerPark1 = intersectionVertex(59.921212, 10.70637639);
  StreetVertex outsideFrognerPark2 = intersectionVertex(59.91824, 10.70109);
  StreetVertex insideBusinessZone = intersectionVertex(59.95961972533365, 10.76411762080707);
  StreetVertex outsideBusinessZone = intersectionVertex(59.963673477748955, 10.764723087536936);

  StreetEdge insideFrognerPark = streetEdge(insideFrognerPark1, insideFrognerPark2);
  StreetEdge halfInHalfOutFrognerPark = streetEdge(insideFrognerPark2, outsideFrognerPark1);
  StreetEdge businessBorder = streetEdge(insideBusinessZone, outsideBusinessZone);
  final Set<Edge> allEdges = Set.of(insideFrognerPark, halfInHalfOutFrognerPark, businessBorder);
  final GeofencingZoneApplier applier = new GeofencingZoneApplier(
    ignored -> allEdges,
    ignored -> allEdges,
    true
  );

  static GeometryFactory fac = GeometryUtils.getGeometryFactory();
  final GeofencingZone zone = new GeofencingZone(
    id("frogner-park"),
    null,
    Polygons.OSLO_FROGNER_PARK,
    true,
    false
  );

  MultiPolygon osloMultiPolygon = fac.createMultiPolygon(new Polygon[] { Polygons.OSLO });
  final GeofencingZone businessArea = new GeofencingZone(
    id("oslo"),
    null,
    osloMultiPolygon,
    false,
    false
  );

  @Test
  void insideZone() {
    assertTrue(insideFrognerPark.getFromVertex().getGeofencingBoundaries().isEmpty());

    var result = applier.applyGeofencingZones(List.of(zone, businessArea));

    // interior edge should not have a boundary extension
    assertFalse(result.boundaryEdges().containsKey(insideFrognerPark));
  }

  @Test
  void halfInHalfOutZone() {
    assertTrue(insideFrognerPark.getFromVertex().getGeofencingBoundaries().isEmpty());

    var result = applier.applyGeofencingZones(List.of(zone, businessArea));

    // fromv (insideFrognerPark2) should have a boundary extension
    var boundaries = insideFrognerPark2.getGeofencingBoundaries();
    assertFalse(boundaries.isEmpty());

    // boundary extension should be present with entering=false (fromv inside, tov outside)
    assertTrue(result.boundaryEdges().containsKey(halfInHalfOutFrognerPark));
    var boundary = result.boundaryEdges().get(halfInHalfOutFrognerPark);
    assertEquals(zone, boundary.zone());
    assertFalse(boundary.entering());
  }

  @Test
  void outsideZone() {
    assertTrue(insideFrognerPark.getFromVertex().getGeofencingBoundaries().isEmpty());
    applier.applyGeofencingZones(List.of(zone, businessArea));
    // Interior vertices no longer get zone extensions; only boundary edges get extensions
    assertTrue(insideFrognerPark.getFromVertex().getGeofencingBoundaries().isEmpty());
  }

  @Test
  void businessAreaBorder() {
    assertFalse(insideFrognerPark.getFromVertex().rentalTraversalBanned(null));
    var result = applier.applyGeofencingZones(List.of(zone, businessArea));

    assertEquals(1, result.businessAreaEdges().size());

    var border = businessBorder.getFromVertex().getBusinessAreaBorder();
    assertNotNull(border);
    assertInstanceOf(BusinessAreaBorder.class, border);
  }

  @Test
  void zoneIndexIsPopulated() {
    var result = applier.applyGeofencingZones(List.of(zone, businessArea));

    assertNotNull(result.zoneIndex());

    // point inside Frogner Park should find the zone
    var zones = result.zoneIndex().getZonesContaining(insideFrognerPark1.getCoordinate());
    assertTrue(zones.contains(zone));

    // point outside both should find neither restricted zone
    var outsideZones = result.zoneIndex().getZonesContaining(outsideFrognerPark2.getCoordinate());
    assertFalse(outsideZones.contains(zone));
  }
}
