package org.opentripplanner.common;

/**
 * Represents a time span.
 *
 */
public class LocalTimeSpan {

    /**
    * Seconds from midnight.
    */
    public int from;

    /**
    * Seconds from midnight.
    */
    public int to;
    
    public LocalTimeSpan(int from, int to) {
        this.from = from;
        this.to = to;
    }

    public String toString() {
        return String.format("from %s to %s", from, to);
    }
}
