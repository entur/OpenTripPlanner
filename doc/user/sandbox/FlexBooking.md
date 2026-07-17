# Flex Booking

## Contact Info

- Entur (Norway)

## Documentation

The flex booking feature adds real-time data to flexible transport. It assumes each flexible line
has a single active vehicle whose current tour — the ordered chain of already-booked passenger
pickups and dropoffs — is reported over a SIRI-ET feed. When a passenger requests a direct flex
trip, OTP inserts their pickup and dropoff into the vehicle's booked tour, but only when every
already-booked stop's **deviation budget** tolerates the added delay. The resulting itinerary
carries the *absolute* times at which the vehicle can actually reach the passenger, instead of the
static NeTEx assumption that the vehicle freely honors the requested location and time.

Behavior overview:

- **No real-time tour** for a flex trip and service date → the static NeTEx behavior is unchanged.
- **A tour is stored** → the static direct-flex result for that trip is suppressed and replaced by
  the insertion-based result. If no feasible insertion exists, no direct flex itinerary is offered
  for that trip at all.
- **The vehicle has fewer than two booked calls** (or the journey is cancelled) → the tour is
  removed and the static behavior applies again.

Insertions must also respect the trip's static NeTEx stop time windows and booking rules
(`latestBookingTime`, `minimumBookingNotice`), and the passenger's requested departure/arrival
time — the vehicle never waits for a passenger.

Only *unscheduled* flex trips (area-based, without a fixed schedule) are supported, and only
direct (door-to-door) flex itineraries are real-time aware in this iteration; flex used as
access/egress to scheduled transit keeps the static behavior.

The insertion algorithm — per-stop deviation budgets, capacity timeline, beeline pre-screening
and segment-cached A* evaluation — is shared with the [carpooling sandbox module](Carpooling.md).

### Configuration

Flex booking is a sandbox feature that must be enabled in `otp-config.json` together with flex
routing itself:

```json
{
  "otpFeatures": {
    "FlexRouting": true,
    "FlexBooking": true
  }
}
```

To receive booked tours, add the updater to your `router-config.json`:

```json
{
  "updaters": [
    {
      "type": "siri-et-flex-booking-updater",
      "feedId": "F",
      "url": "https://example.com/siri-et",
      "frequency": "1m"
    }
  ]
}
```

The updater accepts the same parameters as the `siri-et-carpooling-updater` (see
[Carpooling](Carpooling.md)). The `feedId` must match the feed the flex trips were imported with,
so the SIRI journeys can be matched to them.

### SIRI-ET Data Format

Each `EstimatedVehicleJourney` describes one flex service journey's active vehicle:

- `FramedVehicleJourneyRef` is **required** and matches the journey to a flex trip:
  `DatedVehicleJourneyRef` holds the service journey id, `DataFrameRef` the service date
  (`yyyy-MM-dd`). Journeys referencing unknown trips, trips that are not unscheduled flex trips,
  or inactive dates are skipped.
- `EstimatedVehicleJourneyCode` and `OperatorRef` are required.
- The **first call** is the vehicle's tour start / current position; subsequent calls are the
  booked passenger stops in visit order. Every call carries exactly one `DepartureStopAssignment`
  (the last call: `ArrivalStopAssignment`) with an `ExpectedFlexibleArea` — a `CircularArea`
  (latitude/longitude) or a GML polygon whose centroid is used.
- `LatestExpectedArrivalTime` on a booked stop encodes that stop's remaining deviation budget as
  the difference to its (expected or aimed) arrival time. Without it a 15-minute default applies.
- `ExpectedDepartureOccupancy`/`OnboardCount` (including the driver) and
  `ExpectedDepartureCapacity`/`TotalCapacity` describe the seat timeline. Send `TotalCapacity`
  explicitly — the default (5) is car-sized, not bus-sized.
- `Cancellation=true`, or a journey with fewer than two calls, removes the stored tour.

## Changelog

- 2026-07-17: Initial version — real-time booked tours for unscheduled flex trips, applied to
  direct flex routing.
