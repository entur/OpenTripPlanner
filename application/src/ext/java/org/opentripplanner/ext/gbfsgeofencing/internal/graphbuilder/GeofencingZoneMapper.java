package org.opentripplanner.ext.gbfsgeofencing.internal.graphbuilder;

import static java.util.stream.Collectors.toMap;

import com.google.common.hash.Hashing;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
import org.locationtech.jts.geom.Geometry;
import org.mobilitydata.gbfs.v3_0.geofencing_zones.GBFSFeature;
import org.mobilitydata.gbfs.v3_0.geofencing_zones.GBFSGeofencingZones;
import org.mobilitydata.gbfs.v3_0.geofencing_zones.GBFSName;
import org.opentripplanner.core.model.i18n.I18NString;
import org.opentripplanner.core.model.i18n.TranslatedString;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.street.geometry.GeometryUtils;
import org.opentripplanner.street.geometry.UnsupportedGeometryException;
import org.opentripplanner.service.vehiclerental.model.GeofencingZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Maps GBFS v3.0 geofencing zones to OTP's internal model.
 * <p>
 * This is a standalone implementation for the sandbox feature, avoiding dependencies on the
 * vehicle rental updater package.
 * <p>
 * Note: Only GBFS v3.0 is supported. GBFS v2.x feeds are not supported.
 */
class GeofencingZoneMapper {

  private static final Logger LOG = LoggerFactory.getLogger(GeofencingZoneMapper.class);

  private final String systemId;

  GeofencingZoneMapper(String systemId) {
    this.systemId = systemId;
  }

  /**
   * Maps all geofencing zones from a GBFS v3.0 feed to OTP's internal model.
   */
  List<GeofencingZone> mapGeofencingZones(GBFSGeofencingZones input) {
    return input
      .getData()
      .getGeofencingZones()
      .getFeatures()
      .stream()
      .filter(f -> f.getGeometry() != null)
      .filter(this::hasValidRules)
      .map(this::toInternalModel)
      .filter(Objects::nonNull)
      .toList();
  }

  /**
   * Checks if a feature has valid rules for mapping.
   * A feature needs at least one rule to determine drop-off and pass-through permissions.
   */
  private boolean hasValidRules(GBFSFeature feature) {
    var properties = feature.getProperties();
    if (properties == null) {
      LOG.warn("Skipping geofencing zone with null properties");
      return false;
    }
    var rules = properties.getRules();
    if (rules == null || rules.isEmpty()) {
      LOG.warn("Skipping geofencing zone with null or empty rules: {}", featureName(feature));
      return false;
    }
    return true;
  }

  @Nullable
  private GeofencingZone toInternalModel(GBFSFeature feature) {
    Geometry g;
    try {
      g = GeometryUtils.convertGeoJsonToJtsGeometry(feature.getGeometry());
    } catch (UnsupportedGeometryException e) {
      LOG.error("Could not convert geofencing zone", e);
      return null;
    }

    var id = fallbackId(g);
    return new GeofencingZone(
      new FeedScopedId(systemId, id),
      featureName(feature),
      g,
      !feature.getProperties().getRules().get(0).getRideEndAllowed(),
      !feature.getProperties().getRules().get(0).getRideThroughAllowed()
    );
  }

  @Nullable
  private I18NString featureName(GBFSFeature feature) {
    var names = feature.getProperties().getName();
    if (names == null || names.isEmpty()) {
      return null;
    }
    return TranslatedString.getI18NString(
      names.stream().collect(toMap(GBFSName::getLanguage, GBFSName::getText)),
      false
    );
  }

  /**
   * Some zones don't have a name, so we use the hash of the geometry as a fallback ID.
   */
  private static String fallbackId(Geometry geom) {
    return Hashing.murmur3_32_fixed()
      .hashBytes(geom.toString().getBytes(StandardCharsets.UTF_8))
      .toString();
  }
}
