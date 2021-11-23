package org.opentripplanner.updater.car_park;

import org.junit.Test;
import org.opentripplanner.routing.car_park.CarPark;

import java.util.List;

import static org.junit.Assert.*;

public class TestHslCarParkDataSource {
    @Test
    public void testHslCarParkDataSource() throws Exception {
        HslCarParkDataSource source = new HslCarParkDataSource();
        source.setUrl("file:src/test/resources/bike/hsl.json");
        assertTrue(source.update());
        List<CarPark> carParks = source.getCarParks();

        // Station without car parking and INACTIVE station should be ignored,
        // so only 5 parking areas
        assertEquals(4, carParks.size());
        for (CarPark carPark : carParks) {
            System.out.println(carPark);
        }

        CarPark ainola = carParks.get(0);
        assertEquals("Ainola", ainola.name.toString());
        assertEquals("170", ainola.id);
        assertEquals(25.101, ainola.x, 0.001);
        assertEquals(60.4553, ainola.y, 0.0003);
        assertEquals(46, ainola.maxCapacity);
        assertEquals(46, ainola.spacesAvailable);
        assertEquals(4, ainola.tags.size());
        assertEquals("hslpark:SERVICE_LIGHTING", ainola.tags.get(0));
        assertEquals("hslpark:SERVICE_BICYCLE_FRAME_LOCK", ainola.tags.get(1));
        assertEquals("hslpark:AUTHENTICATION_METHOD_HSL_TRAVEL_CARD", ainola.tags.get(2));
        assertEquals("hslpark:PRICING_METHOD_PARK_AND_RIDE_247_FREE", ainola.tags.get(3));

        CarPark fake = carParks.get(2);
        assertEquals("Asemakuja itäinen", fake.name.toString());
        assertEquals("406", fake.id);
        // operative: false overrides available capacity
        assertEquals(18, fake.maxCapacity);
        assertEquals(18, fake.spacesAvailable);
    }

}