package org.opentripplanner.updater.bike_park;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.Point;
import org.opentripplanner.common.LocalTimeSpan;
import org.opentripplanner.common.LocalTimeSpanWeek;
import org.opentripplanner.routing.bike_park.BikePark;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Load bike parks from the HSL Park and Ride API.
 *
 * @author hannesj
 */
public class HslBikeParkDataSource extends GenericJsonBikeParkDataSource{

    private static final Logger log = LoggerFactory.getLogger(HslBikeParkDataSource.class);

    private GeometryFactory gf = new GeometryFactory();

    public HslBikeParkDataSource() {
        super("results");
    }

    public BikePark makeBikePark(JsonNode node) {
        if (node.path("builtCapacity").path("BICYCLE").isMissingNode()) return null;

        BikePark station = new BikePark();
        station.id = node.path("id").asText();
        station.name = node.path("name").path("fi").asText();
        try {
            Point geometry = parseGeometry(node.path("location")).getCentroid();
            station.y = geometry.getY();
            station.x = geometry.getX();
            station.realTimeData = false;
            if (!node.path("status").asText().equals("IN_OPERATION")) {
                station.spacesAvailable = 0;
            } else {
                station.spacesAvailable = node.path("builtCapacity").path("BICYCLE").asInt();
            }
            List<String> tags = new ArrayList<String>();
            ArrayNode servicesArray = (ArrayNode) node.get("services");
            if (servicesArray.isArray()) {
                for (JsonNode jsonNode : servicesArray) {
                    tags.add("SERVICE_" + jsonNode.asText());
                }
            }
            ArrayNode authenticationMethods = (ArrayNode) node.get("authenticationMethods");
            if (authenticationMethods.isArray()) {
                for (JsonNode jsonNode : authenticationMethods) {
                    tags.add("AUTHENTICATION_METHOD_" + jsonNode.asText());
                }
            }
            tags.add("PRICING_METHOD_" + node.path("pricingMethod").asText());
            station.tags = tags;

            LocalTimeSpanWeek timeSpanWeek = new LocalTimeSpanWeek();
            JsonNode openDayHours = node.path("openingHours").path("byDayType");
            if (openDayHours.has("BUSINESS_DAY") && openDayHours.path("BUSINESS_DAY").has("from")) {
                timeSpanWeek.addSpan(
                    LocalTimeSpanWeek.DayType.BUSINESS_DAY,
                    convertOpeningHoursToLocalTimeSpan(openDayHours.path("BUSINESS_DAY"))
                );
            }
            if (openDayHours.has("SATURDAY") && openDayHours.path("SATURDAY").has("from")) {
                timeSpanWeek.addSpan(
                    LocalTimeSpanWeek.DayType.SATURDAY,
                    convertOpeningHoursToLocalTimeSpan(openDayHours.path("SATURDAY"))
                );
            }
            if (openDayHours.has("SUNDAY") && openDayHours.path("SUNDAY").has("from")) {
                timeSpanWeek.addSpan(
                    LocalTimeSpanWeek.DayType.SUNDAY,
                    convertOpeningHoursToLocalTimeSpan(openDayHours.path("SUNDAY"))
                );
            }

            station.openingHours = timeSpanWeek;

            return station;
        } catch (Exception e) {
            log.warn("Error parsing bike rental station " + station.id, e);
            return null;
        }
    }

    /**
     * Parses a {@link LocalTimeSpan} from an openingHour definition for a day type.
     * The times can either have just hours or hours:minutes.
     */
    private LocalTimeSpan convertOpeningHoursToLocalTimeSpan(JsonNode dayHours) {
        String from = dayHours.path("from").asText();
        int fromSecondsFromMidnight = Integer.parseInt(from.substring(0, 2)) * 60 * 60;
        if (from.length() > 2) {
            fromSecondsFromMidnight += Integer.parseInt(from.substring(3, 5)) * 60;
        }
        String to = dayHours.path("until").asText();
        int toSecondsFromMidnight = Integer.parseInt(to.substring(0, 2)) * 60 * 60;
        if (to.length() > 2) {
            toSecondsFromMidnight += Integer.parseInt(to.substring(3, 5)) * 60;
        }
        return new LocalTimeSpan(fromSecondsFromMidnight, toSecondsFromMidnight);
    }

    // TODO: These are inlined from GeometryDeserializer
    private Geometry parseGeometry(JsonNode root) {
        String typeName = root.get("type").asText();
        if(typeName.equals("Point")) {
            return this.gf.createPoint(this.parseCoordinate(root.get("coordinates")));
        } else if(typeName.equals("MultiPoint")) {
            return this.gf.createMultiPoint(this.parseLineString(root.get("coordinates")));
        } else if(typeName.equals("LineString")) {
            return this.gf.createLineString(this.parseLineString(root.get("coordinates")));
        } else if(typeName.equals("MultiLineString")) {
            return this.gf.createMultiLineString(this.parseLineStrings(root.get("coordinates")));
        } else {
            JsonNode arrayOfPolygons;
            if(typeName.equals("Polygon")) {
                arrayOfPolygons = root.get("coordinates");
                return this.parsePolygonCoordinates(arrayOfPolygons);
            } else if(typeName.equals("MultiPolygon")) {
                arrayOfPolygons = root.get("coordinates");
                return this.gf.createMultiPolygon(this.parsePolygons(arrayOfPolygons));
            } else if(typeName.equals("GeometryCollection")) {
                return this.gf.createGeometryCollection(this.parseGeometries(root.get("geometries")));
            } else {
                throw new UnsupportedOperationException();
            }
        }
    }

    private Geometry[] parseGeometries(JsonNode arrayOfGeoms) {
        Geometry[] items = new Geometry[arrayOfGeoms.size()];

        for(int i = 0; i != arrayOfGeoms.size(); ++i) {
            items[i] = this.parseGeometry(arrayOfGeoms.get(i));
        }

        return items;
    }

    private Polygon parsePolygonCoordinates(JsonNode arrayOfRings) {
        return this.gf.createPolygon(this.parseExteriorRing(arrayOfRings), this.parseInteriorRings(arrayOfRings));
    }

    private Polygon[] parsePolygons(JsonNode arrayOfPolygons) {
        Polygon[] polygons = new Polygon[arrayOfPolygons.size()];

        for(int i = 0; i != arrayOfPolygons.size(); ++i) {
            polygons[i] = this.parsePolygonCoordinates(arrayOfPolygons.get(i));
        }

        return polygons;
    }

    private LinearRing parseExteriorRing(JsonNode arrayOfRings) {
        return this.gf.createLinearRing(this.parseLineString(arrayOfRings.get(0)));
    }

    private LinearRing[] parseInteriorRings(JsonNode arrayOfRings) {
        LinearRing[] rings = new LinearRing[arrayOfRings.size() - 1];

        for(int i = 1; i < arrayOfRings.size(); ++i) {
            rings[i - 1] = this.gf.createLinearRing(this.parseLineString(arrayOfRings.get(i)));
        }

        return rings;
    }

    private Coordinate parseCoordinate(JsonNode array) {
        return new Coordinate(array.get(0).asDouble(), array.get(1).asDouble());
    }

    private Coordinate[] parseLineString(JsonNode array) {
        Coordinate[] points = new Coordinate[array.size()];

        for(int i = 0; i != array.size(); ++i) {
            points[i] = this.parseCoordinate(array.get(i));
        }

        return points;
    }

    private LineString[] parseLineStrings(JsonNode array) {
        LineString[] strings = new LineString[array.size()];

        for(int i = 0; i != array.size(); ++i) {
            strings[i] = this.gf.createLineString(this.parseLineString(array.get(i)));
        }

        return strings;
    }
}

