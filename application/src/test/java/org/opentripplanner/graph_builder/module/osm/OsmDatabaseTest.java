package org.opentripplanner.graph_builder.module.osm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.opentripplanner.graph_builder.issue.api.DataImportIssueStore;
import org.opentripplanner.osm.DefaultOsmProvider;
import org.opentripplanner.osm.TestOsmProvider;
import org.opentripplanner.osm.model.OsmMemberType;
import org.opentripplanner.osm.model.OsmNode;
import org.opentripplanner.osm.model.OsmRelation;
import org.opentripplanner.osm.model.OsmRelationMember;
import org.opentripplanner.osm.model.OsmWay;
import org.opentripplanner.test.support.ResourceLoader;

public class OsmDatabaseTest {

  private static final ResourceLoader RESOURCE_LOADER = ResourceLoader.of(OsmDatabaseTest.class);

  /**
   * The way https://www.openstreetmap.org/way/13876983 does not contain the tag lcn (local cycling network)
   * but because it is part of a relation that _does_, the tag is copied from the relation to the way.
   * This test assert that this is really happening.
   */
  @Test
  void bicycleRouteRelations() {
    var osmdb = new OsmDatabase(DataImportIssueStore.NOOP);
    var provider = new DefaultOsmProvider(RESOURCE_LOADER.file("ehningen-minimal.osm.pbf"), true);
    provider.readOsm(osmdb);
    osmdb.postLoad();

    var way = osmdb.getWay(13876983L);
    assertNotNull(way);

    assertEquals("yes", way.getTag("lcn"));
    assertEquals("Gärtringer Weg", way.getTag("name"));
  }

  /**
   * When extracting Austria, Geofabrik produces data where a public transport relation that crosses
   * a border (https://www.openstreetmap.org/relation/4027804) references ways that are not in the
   * extract. This needs to be dealt with gracefully.
   */
  @Test
  void invalidPublicTransportRelation() {
    var osmdb = new OsmDatabase(DataImportIssueStore.NOOP);
    var file = RESOURCE_LOADER.file("brenner-invalid-relation-reference.osm.pbf");
    var provider = new DefaultOsmProvider(file, true);
    provider.readOsm(osmdb);
    osmdb.postLoad();

    var way = osmdb.getWay(302732658L);
    assertNotNull(way);
    assertEquals("platform", way.getTag("public_transport"));
  }

  @Test
  void isNodeBelongsToWayShouldNotReturnTrueForNodesSolelyOnBarriers() {
    var osmdb = new OsmDatabase(DataImportIssueStore.NOOP);

    var chain = OsmWay.of()
      .withId(999)
      .addTag("barrier", "chain")
      .addNodeRef(1)
      .addNodeRef(2)
      .build();

    var path = OsmWay.of().withId(1).addTag("highway", "path").addNodeRef(2).addNodeRef(3).build();

    osmdb.addWay(chain);
    osmdb.addWay(path);
    osmdb.doneSecondPhaseWays();

    assertFalse(osmdb.isNodeBelongsToWay(1L));
    assertTrue(osmdb.isNodeBelongsToWay(2L));
    assertTrue(osmdb.isNodeBelongsToWay(3L));
  }

  @Test
  void testWayIsntKeptForAreas() {
    var n1 = OsmNode.builder().withId(1).withLat(0).withLon(0).build();
    var n2 = OsmNode.builder().withId(2).withLat(0).withLon(3).build();
    var n3 = OsmNode.builder().withId(3).withLat(3).withLon(3).build();
    var n4 = OsmNode.builder().withId(4).withLat(3).withLon(0).build();
    var n5 = OsmNode.builder().withId(5).withLat(0.3).withLon(1).build();
    var n6 = OsmNode.builder().withId(6).withLat(0.3).withLon(1.5).build();
    var n7 = OsmNode.builder().withId(7).withLat(0.7).withLon(1.5).build();
    var n8 = OsmNode.builder().withId(8).withLat(0.7).withLon(1).build();
    var n9 = OsmNode.builder().withId(9).withLat(0.3).withLon(2).build();
    var n10 = OsmNode.builder().withId(10).withLat(0.3).withLon(2.5).build();
    var n11 = OsmNode.builder().withId(11).withLat(0.7).withLon(2.5).build();
    var n12 = OsmNode.builder().withId(12).withLat(0.7).withLon(2).build();
    var n13 = OsmNode.builder().withId(13).withLat(3).withLon(3).build();
    var n14 = OsmNode.builder().withId(14).withLat(3).withLon(4).build();
    var n15 = OsmNode.builder().withId(15).withLat(4).withLon(3).build();

    var simpleArea = OsmWay.of()
      .addTag("public_transport", "platform")
      .addNodeRef(13)
      .addNodeRef(14)
      .addNodeRef(15)
      .addNodeRef(13)
      .build();

    var outerRing = OsmWay.of()
      .withId(1)
      .addNodeRef(1)
      .addNodeRef(2)
      .addNodeRef(3)
      .addNodeRef(4)
      .addNodeRef(1)
      .addTag("highway", "residential")
      .build();

    var innerRing = OsmWay.of()
      .withId(2)
      .addNodeRef(5)
      .addNodeRef(6)
      .addNodeRef(7)
      .addNodeRef(8)
      .addNodeRef(5)
      .build();

    var innerRingWithBarrier = OsmWay.of()
      .withId(3)
      .addNodeRef(9)
      .addNodeRef(10)
      .addNodeRef(11)
      .addNodeRef(12)
      .addNodeRef(9)
      .addTag("barrier", "chain")
      .build();

    var outerMember = new OsmRelationMember();
    outerMember.setRole("outer");
    outerMember.setType(OsmMemberType.WAY);
    outerMember.setRef(1);

    var innerMember = new OsmRelationMember();
    innerMember.setRole("inner");
    innerMember.setType(OsmMemberType.WAY);
    innerMember.setRef(2);

    var innerBarrierMember = new OsmRelationMember();
    innerBarrierMember.setRole("inner");
    innerBarrierMember.setType(OsmMemberType.WAY);
    innerBarrierMember.setRef(3);

    var multipolygon = OsmRelation.builder()
      .addTag("type", "multipolygon")
      .addTag("highway", "pedestrian")
      .addMember(outerMember)
      .addMember(innerMember)
      .addMember(innerBarrierMember)
      .build();

    var provider = TestOsmProvider.of().build();

    // Set OsmProvider on all entities
    multipolygon = multipolygon.toBuilder().withOsmProvider(provider).build();
    simpleArea = simpleArea.copy().withOsmProvider(provider).build();
    outerRing = outerRing.copy().withOsmProvider(provider).build();
    innerRing = innerRing.copy().withOsmProvider(provider).build();
    innerRingWithBarrier = innerRingWithBarrier.copy().withOsmProvider(provider).build();
    n1 = n1.toBuilder().withOsmProvider(provider).build();
    n2 = n2.toBuilder().withOsmProvider(provider).build();
    n3 = n3.toBuilder().withOsmProvider(provider).build();
    n4 = n4.toBuilder().withOsmProvider(provider).build();
    n5 = n5.toBuilder().withOsmProvider(provider).build();
    n6 = n6.toBuilder().withOsmProvider(provider).build();
    n7 = n7.toBuilder().withOsmProvider(provider).build();
    n8 = n8.toBuilder().withOsmProvider(provider).build();
    n9 = n9.toBuilder().withOsmProvider(provider).build();
    n10 = n10.toBuilder().withOsmProvider(provider).build();
    n11 = n11.toBuilder().withOsmProvider(provider).build();
    n12 = n12.toBuilder().withOsmProvider(provider).build();
    n13 = n13.toBuilder().withOsmProvider(provider).build();
    n14 = n14.toBuilder().withOsmProvider(provider).build();
    n15 = n15.toBuilder().withOsmProvider(provider).build();

    var osmdb = new OsmDatabase(DataImportIssueStore.NOOP);
    osmdb.addRelation(multipolygon);
    osmdb.doneFirstPhaseRelations();
    osmdb.addWay(simpleArea);
    osmdb.addWay(outerRing);
    osmdb.addWay(innerRing);
    osmdb.addWay(innerRingWithBarrier);
    osmdb.doneSecondPhaseWays();
    osmdb.addNode(n1);
    osmdb.addNode(n2);
    osmdb.addNode(n3);
    osmdb.addNode(n4);
    osmdb.addNode(n5);
    osmdb.addNode(n6);
    osmdb.addNode(n7);
    osmdb.addNode(n8);
    osmdb.addNode(n9);
    osmdb.addNode(n10);
    osmdb.addNode(n11);
    osmdb.addNode(n12);
    osmdb.addNode(n13);
    osmdb.addNode(n14);
    osmdb.addNode(n15);
    osmdb.doneThirdPhaseNodes();

    // innerRing and simpleArea should no longer exist
    assertEquals(2, osmdb.getWays().size());
    assertNull(osmdb.getWay(innerRing.getId()));

    // simpleArea and multipolygon
    assertEquals(2, osmdb.getWalkableAreas().size());

    // innerRingWithBarrier should not be polluted with the highway tag when fetched from the way
    assertFalse(osmdb.getWay(innerRingWithBarrier.getId()).hasTag("highway"));
  }
}
