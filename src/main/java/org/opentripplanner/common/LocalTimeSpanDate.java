package org.opentripplanner.common;

import java.util.ArrayList;

/**
 * Represents a date that contains a list of time spans.
 */
public class LocalTimeSpanDate {

    /**
    * Date in YYYYMMDD format.
    */
    public String date;

    public ArrayList<LocalTimeSpan> localTimeSpans;
    
    public LocalTimeSpanDate(String date) {
        this.date = date;
    }

    public void addSpans(ArrayList<LocalTimeSpan> timeSpans) {
        if (localTimeSpans == null) {
            localTimeSpans = new ArrayList<LocalTimeSpan>();
        }
        this.localTimeSpans.addAll(timeSpans);
    }

    public String toString() {
        return String.format("%s time spans exist for %s", localTimeSpans == null ? 0 : localTimeSpans.size(), date);
    }
}
