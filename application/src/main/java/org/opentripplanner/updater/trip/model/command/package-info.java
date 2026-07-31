/**
 * The command model: what a real-time feed asks. Commands are the format-independent output of
 * the SIRI-ET and GTFS-RT parsers, together with the references and value objects they carry.
 * Parsers are state-free, so commands hold references ({@link
 * org.opentripplanner.updater.trip.model.command.TripReference}, {@link
 * org.opentripplanner.updater.trip.model.command.StopReference}) - never resolved entities.
 * <p>
 * This package depends only on the policy package and transit value types.
 */
package org.opentripplanner.updater.trip.model.command;
