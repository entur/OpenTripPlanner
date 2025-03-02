<!--
  NOTE! Part of this document is generated. Make sure you edit the template, not the generated doc.

   - Template directory is:  /doc/templates
   - Generated directory is: /doc/user 
-->
# Stop consolidation

## Contact Info

- [Jon Campbell](mailto:jon.campbell@arcadis.com), Arcadis, USA

## Feature explanation

This sandbox feature allows you to "combine" equivalent stops from across several feeds into a single,
consolidated one. 

It is achieved by defining a "primary" stop and one or more "secondary" stops. During the graph
build all trip patterns are modified so that the secondary ones are swapped out for their
primary equivalent.

## Effects

This has the following consequences 

- When you query the departures for a primary stop you see a consolidated view of all the equivalent departures.
- When transferring at a consolidated stop you no longer get instructions like "walk 5 meters to stop X"

!!! warning "Downsides"

    However, this feature has also severe downsides:

    - It makes real-time trip updates referencing a stop id much more complicated and in many cases 
      impossible to resolve. 
      You can only reference a stop by its sequence, which only works in GTFS-RT, not SIRI.
    - Fare calculation and transfers are unlikely to work as expected.


## Configuration

To enable this feature you need to add a file to OTP's working directory and configure
its name like this:

<!-- INSERT: config -->

The additional config file must look like the following:

<!-- INSERT: file -->

The column names mean the following:

- `stop_group_id`: id to group several rows in the file together
- `feed_id`: feed id of the stop
- `stop_id`: id of the stop
- `is_primary`: whether the row represents a primary stop, `1` means yes and `0` means no

