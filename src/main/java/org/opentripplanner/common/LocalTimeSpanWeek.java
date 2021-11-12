package org.opentripplanner.common;

import java.util.ArrayList;

/**
 * Represents a week that contains days or day types with time spans.
 */
public class LocalTimeSpanWeek {

    public enum DayType {
        BUSINESS_DAY, SATURDAY, SUNDAY;
    }

    public ArrayList<LocalTimeSpan> businessDayTimeSpans;

    public ArrayList<LocalTimeSpan> saturdayTimeSpans;

    public ArrayList<LocalTimeSpan> sundayTimeSpans;

    public void addSpan(DayType dayType, LocalTimeSpan timeSpan) {
        if (dayType == DayType.BUSINESS_DAY) {
            if (businessDayTimeSpans == null) {
                businessDayTimeSpans = new ArrayList<LocalTimeSpan>();
            }
            businessDayTimeSpans.add(timeSpan);
        } else if (dayType == DayType.SATURDAY) {
            if (saturdayTimeSpans == null) {
                saturdayTimeSpans = new ArrayList<LocalTimeSpan>();
            }
            saturdayTimeSpans.add(timeSpan);
        } else if (dayType == DayType.SUNDAY) {
            if (sundayTimeSpans == null) {
                sundayTimeSpans = new ArrayList<LocalTimeSpan>();
            }
            sundayTimeSpans.add(timeSpan);
        }
    }
}
