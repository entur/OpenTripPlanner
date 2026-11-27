package org.opentripplanner.perfrig;

import graphql.ExecutionInput;
import graphql.GraphQL;
import graphql.schema.GraphQLSchema;
import java.util.Map;
import java.util.Set;
import org.opentripplanner.apis.support.graphql.OtpDataFetcherExceptionHandler;
import org.opentripplanner.apis.transmodel.TransmodelGraphQLRequestContext;
import org.opentripplanner.apis.transmodel.support.AbortOnUnprocessableRequestExecutionStrategy;

/**
 * The Transmodel trip query of the rig. A case is a set of variables for it; a variable that is
 * not given leaves the argument out, so the default of the router-config applies.
 */
final class TripQuery {

  static final Set<String> VARIABLES = Set.of(
    "from",
    "to",
    "dateTime",
    "arriveBy",
    "searchWindow",
    "pageCursor",
    "modes",
    "numTripPatterns",
    "wheelchairAccessible",
    "walkSpeed",
    "walkReluctance",
    "waitReluctance",
    "transferPenalty",
    "transferSlack",
    "maximumTransfers",
    "maximumAdditionalTransfers",
    "bikePreferences",
    "scooterPreferences",
    "relaxTransitGroupPriority",
    "banned",
    "whiteListed",
    "filters",
    "via",
    "itineraryFilters",
    "timetableView",
    "bookingTime",
    "ignoreRealtimeUpdates",
    "locale",
    "accessEgressPenalty",
    "maxAccessEgressDurationForMode",
    "maxDirectDurationForMode",
    "boardSlackDefault",
    "alightSlackDefault",
    "boardSlackList",
    "alightSlackList"
  );

  static final String QUERY = """
    query rig(
      $from: Location!, $to: Location!, $dateTime: DateTime, $arriveBy: Boolean,
      $searchWindow: Int, $pageCursor: String, $modes: Modes, $numTripPatterns: Int,
      $wheelchairAccessible: Boolean, $walkSpeed: Float, $walkReluctance: Float,
      $waitReluctance: Float, $transferPenalty: Int, $transferSlack: Int,
      $maximumTransfers: Int, $maximumAdditionalTransfers: Int,
      $bikePreferences: BikePreferencesInput, $scooterPreferences: ScooterPreferencesInput,
      $relaxTransitGroupPriority: RelaxCostInput, $banned: InputBanned,
      $whiteListed: InputWhiteListed, $filters: [TripFilterInput!],
      $via: [TripViaLocationInput!], $itineraryFilters: ItineraryFilters,
      $timetableView: Boolean, $bookingTime: DateTime, $ignoreRealtimeUpdates: Boolean,
      $locale: Locale, $accessEgressPenalty: [PenaltyForStreetMode!],
      $maxAccessEgressDurationForMode: [StreetModeDurationInput!],
      $maxDirectDurationForMode: [StreetModeDurationInput!],
      $boardSlackDefault: Int, $alightSlackDefault: Int,
      $boardSlackList: [TransportModeSlack], $alightSlackList: [TransportModeSlack]
    ) {
      trip(
        from: $from, to: $to, dateTime: $dateTime, arriveBy: $arriveBy,
        searchWindow: $searchWindow, pageCursor: $pageCursor, modes: $modes,
        numTripPatterns: $numTripPatterns, wheelchairAccessible: $wheelchairAccessible,
        walkSpeed: $walkSpeed, walkReluctance: $walkReluctance, waitReluctance: $waitReluctance,
        transferPenalty: $transferPenalty, transferSlack: $transferSlack,
        maximumTransfers: $maximumTransfers,
        maximumAdditionalTransfers: $maximumAdditionalTransfers,
        bikePreferences: $bikePreferences, scooterPreferences: $scooterPreferences,
        relaxTransitGroupPriority: $relaxTransitGroupPriority, banned: $banned,
        whiteListed: $whiteListed, filters: $filters, via: $via,
        itineraryFilters: $itineraryFilters, timetableView: $timetableView,
        bookingTime: $bookingTime, ignoreRealtimeUpdates: $ignoreRealtimeUpdates,
        locale: $locale, accessEgressPenalty: $accessEgressPenalty,
        maxAccessEgressDurationForMode: $maxAccessEgressDurationForMode,
        maxDirectDurationForMode: $maxDirectDurationForMode,
        boardSlackDefault: $boardSlackDefault, alightSlackDefault: $alightSlackDefault,
        boardSlackList: $boardSlackList, alightSlackList: $alightSlackList
      ) {
        dateTime
        fromPlace { ...place }
        toPlace { ...place }
        metadata {
          searchWindowUsed
          nextDateTime
          prevDateTime
          pageDepartureTimeStart
          pageDepartureTimeEnd
        }
        nextPageCursor
        previousPageCursor
        routingErrors { code description inputField }
        tripPatterns {
          aimedStartTime
          expectedStartTime
          aimedEndTime
          expectedEndTime
          duration
          directDuration
          distance
          streetDistance
          walkTime
          waitingTime
          generalizedCost
          generalizedCost2
          waitTimeOptimizedCost
          transferPriorityCost
          emission { co2 }
          systemNotices { tag text }
          timePenalty { appliedTo timePenalty generalizedCostDelta }
          legs {
            id
            mode
            transportSubmode
            aimedStartTime
            expectedStartTime
            aimedEndTime
            expectedEndTime
            duration
            directDuration
            distance
            generalizedCost
            realtime
            ride
            rentedBike
            serviceDate
            bikeRentalNetworks
            fromPlace { ...place }
            toPlace { ...place }
            line { id publicCode name transportMode transportSubmode }
            authority { id }
            operator { id }
            serviceJourney { id }
            datedServiceJourney { id }
            fromEstimatedCall { ...call }
            toEstimatedCall { ...call }
            intermediateEstimatedCalls { ...call }
            intermediateQuays { id }
            situations { situationNumber }
            bookingArrangements {
              bookingMethods
              bookingNote
              latestBookingTime
              latestBookingDay
              minimumBookingPeriod
              bookWhen
            }
            interchangeFrom { ...interchange }
            interchangeTo { ...interchange }
            pointsOnLink { points length distance }
            elevationProfile { distance elevation }
            steps {
              distance
              relativeDirection
              streetName
              heading
              exit
              stayOn
              area
              bogusName
              latitude
              longitude
              elevationProfile { distance elevation }
            }
            emission { co2 }
          }
        }
      }
    }

    fragment place on Place {
      name
      latitude
      longitude
      vertexType
      viaLocationType
      flexibleArea
      quay { id name publicCode stopPlace { id } }
      bikeRentalStation { id }
      rentalVehicle { id }
    }

    fragment call on EstimatedCall {
      quay { id }
      scheduledQuay { id }
      stopPositionInPattern
      aimedArrivalTime
      aimedDepartureTime
      expectedArrivalTime
      expectedDepartureTime
      actualArrivalTime
      actualDepartureTime
      date
      forBoarding
      forAlighting
      requestStop
      timingPoint
      cancellation
      realtime
      destinationDisplay { frontText via }
      notices { id }
    }

    fragment interchange on Interchange {
      staySeated
      guaranteed
      priority
      maximumWaitTime
      fromServiceJourney { id }
      toServiceJourney { id }
    }
    """;

  private final GraphQLSchema schema;

  TripQuery(GraphQLSchema schema) {
    this.schema = schema;
  }

  /** Execute the query, as TransmodelGraph does, and return the result as the HTTP API would. */
  Map<String, Object> execute(
    Map<String, Object> variables,
    TransmodelGraphQLRequestContext context
  ) {
    for (String name : variables.keySet()) {
      if (!VARIABLES.contains(name)) {
        throw new IllegalArgumentException("Unknown trip query variable: " + name);
      }
    }
    var input = ExecutionInput.newExecutionInput()
      .query(QUERY)
      .operationName("rig")
      .context(context)
      .variables(variables)
      .build();
    try (var strategy = new AbortOnUnprocessableRequestExecutionStrategy()) {
      var graphQL = GraphQL.newGraphQL(schema)
        .queryExecutionStrategy(strategy)
        .defaultDataFetcherExceptionHandler(new OtpDataFetcherExceptionHandler())
        .build();
      return graphQL.execute(input).toSpecification();
    }
  }
}
