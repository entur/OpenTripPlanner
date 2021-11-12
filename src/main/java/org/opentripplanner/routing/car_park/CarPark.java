/* This program is free software: you can redistribute it and/or
 modify it under the terms of the GNU Lesser General Public License
 as published by the Free Software Foundation, either version 3 of
 the License, or (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>. */

package org.opentripplanner.routing.car_park;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.locationtech.jts.geom.Geometry;
import org.opentripplanner.common.LocalTimeSpan;
import org.opentripplanner.common.LocalTimeSpanDate;
import org.opentripplanner.common.LocalTimeSpanWeek;
import org.opentripplanner.util.I18NString;

import javax.xml.bind.annotation.XmlAttribute;
import java.io.Serializable;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;


public class CarPark implements Serializable {
    private static final long serialVersionUID = 8311460609708089384L;

    /**
     * Unique ID of the car park. Creator should ensure the ID is unique server-wide (prefix by a
     * source ID if there are several sources)
     */
    @XmlAttribute
    @JsonSerialize
    public String id;

    @XmlAttribute
    @JsonSerialize
    public I18NString name;

    /** Note: x = Longitude, y = Latitude */
    @XmlAttribute
    @JsonSerialize
    public double x, y;

    @XmlAttribute
    @JsonSerialize
    public int spacesAvailable = Integer.MAX_VALUE;

    @XmlAttribute
    @JsonSerialize
    public int maxCapacity = Integer.MAX_VALUE;

    /**
     * Whether this parking has space available information updated in real-time. If no real-time
     * data, users should take spacesAvailable with a pinch of salt, as they are a crude estimate.
     */
    @XmlAttribute
    @JsonSerialize
    public boolean realTimeData = true;

    public Geometry geometry;

    public List<String> tags;

    /**
     * Contains opening hours for business days, saturday and sunday,
     * where all times are seconds from midnight.
     */
    public LocalTimeSpanWeek openingHours;

    /**
     * Dates should be in the format of YYYYMMDD, returns a {@link LocalTimeSpanDate} for each date.
     */
    public ArrayList<LocalTimeSpanDate> getOpeningHoursForDates(List<String> dates) {
        ArrayList<LocalTimeSpanDate> timeSpanDates = new ArrayList<LocalTimeSpanDate>();
        for (int i = 0; i < dates.size(); i++) {
            String date = dates.get(i);
            if (date.length() != 8) {
                timeSpanDates.add(null);
                continue;
            }
            LocalDate localDate = LocalDate.parse(date, DateTimeFormatter.BASIC_ISO_DATE);
            DayOfWeek dayOfWeek = localDate.getDayOfWeek();
            LocalTimeSpanDate timeSpanDate = new LocalTimeSpanDate(date);
            if (openingHours != null) {
                if (dayOfWeek == DayOfWeek.SUNDAY) {
                    ArrayList<LocalTimeSpan> openingHoursForSunday = openingHours.sundayTimeSpans;
                    if (openingHoursForSunday != null) {
                        timeSpanDate.addSpans(openingHoursForSunday);
                    }
                } else if (dayOfWeek == DayOfWeek.SATURDAY) {
                    ArrayList<LocalTimeSpan> openingHoursForSaturday = openingHours.saturdayTimeSpans;
                    if (openingHoursForSaturday != null) {
                        timeSpanDate.addSpans(openingHoursForSaturday);
                    }
                } else {
                    ArrayList<LocalTimeSpan> openingHoursForBusinessDays = openingHours.businessDayTimeSpans;
                    if (openingHoursForBusinessDays != null) {
                        timeSpanDate.addSpans(openingHoursForBusinessDays);
                    }
                }
            }
            timeSpanDates.add(timeSpanDate);
        }
        return timeSpanDates;
    }

    public boolean equals(Object o) {
        if (!(o instanceof CarPark)) {
            return false;
        }
        CarPark other = (CarPark) o;
        return other.id.equals(id);
    }

    public int hashCode() {
        return id.hashCode() + 1;
    }

    public String toString () {
        return String.format(Locale.US, "Car park %s at %.6f, %.6f", name, y, x);
    }
}
