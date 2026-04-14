package org.opentripplanner.osm.model;

public class RelationBuilder {

  private final OsmRelation.Builder relationBuilder = OsmRelation.builder();

  public static RelationBuilder ofMultiPolygon() {
    var builder = new RelationBuilder();
    builder.relationBuilder.addTag("type", "multipolygon");
    builder.relationBuilder.addTag("highway", "pedestrian");
    return builder;
  }

  public static RelationBuilder ofTurnRestriction(String restrictionType) {
    var builder = new RelationBuilder();
    builder.relationBuilder.addTag("type", "restriction");
    builder.relationBuilder.addTag("restriction", restrictionType);
    return builder;
  }

  public RelationBuilder withWayMember(long id, String role) {
    return withMember(id, role, OsmMemberType.WAY);
  }

  public RelationBuilder withNodeMember(long id, String role) {
    return withMember(id, role, OsmMemberType.NODE);
  }

  public OsmRelation build() {
    return relationBuilder.build();
  }

  private RelationBuilder withMember(long id, String role, OsmMemberType osmMemberType) {
    var member = new OsmRelationMember();
    member.setRole(role);
    member.setType(osmMemberType);
    member.setRef(id);
    relationBuilder.addMember(member);
    return this;
  }
}
