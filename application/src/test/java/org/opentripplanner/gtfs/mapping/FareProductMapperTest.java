package org.opentripplanner.gtfs.mapping;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.onebusaway.gtfs.model.AgencyAndId;
import org.onebusaway.gtfs.model.FareProduct;
import org.onebusaway.gtfs.model.RiderCategory;
import org.opentripplanner.framework.i18n.I18NString;
import org.opentripplanner.transit.model.basic.Money;

class FareProductMapperTest {

  public static final IdFactory ID_FACTORY = new IdFactory("1");
  public static final String CAT_NAME = "Cat 1";

  @Test
  void map() {
    var gtfs = new FareProduct();
    gtfs.setFareProductId(new AgencyAndId("1", "1"));
    gtfs.setAmount(1);
    gtfs.setName("day pass");
    gtfs.setCurrency("USD");
    gtfs.setDurationAmount(1);
    gtfs.setDurationUnit(5);

    var mapper = new FareProductMapper(ID_FACTORY);
    var internal = mapper.map(gtfs);

    assertEquals(internal.price(), Money.usDollars(1));
    assertEquals(100, internal.price().minorUnitAmount());
  }

  @Test
  void noFractionDigits() {
    var gtfs = new FareProduct();
    gtfs.setFareProductId(new AgencyAndId("1", "1"));
    gtfs.setAmount(100);
    gtfs.setName("day pass");
    gtfs.setCurrency("JPY");

    var mapper = new FareProductMapper(ID_FACTORY);
    var internal = mapper.map(gtfs);

    assertEquals("¥100", internal.price().toString());
    assertEquals(100, internal.price().minorUnitAmount());
  }

  @Test
  void riderCategory() {
    var gtfs = fareProduct();
    var category = new RiderCategory();
    category.setId(new AgencyAndId("1", "cat1"));
    category.setName(CAT_NAME);
    gtfs.setRiderCategory(category);

    var mapper = new FareProductMapper(ID_FACTORY);
    var internal = mapper.map(gtfs);

    var mapped = internal.category();

    assertEquals(I18NString.of(CAT_NAME), mapped.name());
    assertEquals("", mapped.id().toString());
  }

  private static @NotNull FareProduct fareProduct() {
    var gtfs = new FareProduct();
    gtfs.setFareProductId(new AgencyAndId("1", "1"));
    gtfs.setAmount(1);
    gtfs.setName("day pass");
    gtfs.setCurrency("USD");
    return gtfs;
  }
}
