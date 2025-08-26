package org.opentripplanner.apis.gtfs.mapping.routerequest;

import static org.opentripplanner.apis.gtfs.mapping.routerequest.ArgumentUtils.getTransitModes;
import static org.opentripplanner.apis.gtfs.mapping.routerequest.StreetModeMapper.getStreetModeForRouting;
import static org.opentripplanner.apis.gtfs.mapping.routerequest.StreetModeMapper.validateStreetModes;

import graphql.schema.DataFetchingEnvironment;
import java.util.List;
import org.opentripplanner.apis.gtfs.generated.GraphQLTypes;
import org.opentripplanner.apis.gtfs.mapping.TransitModeMapper;
import org.opentripplanner.routing.api.request.StreetMode;
import org.opentripplanner.routing.api.request.request.JourneyRequestBuilder;
import org.opentripplanner.routing.api.request.request.StreetRequest;
import org.opentripplanner.routing.api.request.request.TransitRequestBuilder;
import org.opentripplanner.routing.api.request.request.filter.SelectRequest;
import org.opentripplanner.routing.api.request.request.filter.TransitFilterRequest;
import org.opentripplanner.transit.model.basic.MainAndSubMode;

public class ModePreferencesMapper {

  /**
   * TODO this doesn't support multiple street modes yet
   */
  static void setModes(
    JourneyRequestBuilder journey,
    GraphQLTypes.GraphQLPlanModesInput modesInput,
    DataFetchingEnvironment environment
  ) {
    var direct = modesInput.getGraphQLDirect();
    if (Boolean.TRUE.equals(modesInput.getGraphQLTransitOnly())) {
      journey.withDirect(new StreetRequest(StreetMode.NOT_SET));
    } else if (direct != null) {
      if (direct.isEmpty()) {
        throw new IllegalArgumentException("Direct modes must not be empty.");
      }
      var streetModes = direct.stream().map(DirectModeMapper::map).toList();
      journey.withDirect(new StreetRequest(getStreetModeForRouting(streetModes)));
    }

    var transit = modesInput.getGraphQLTransit();
    if (Boolean.TRUE.equals(modesInput.getGraphQLDirectOnly())) {
      journey.withTransit(TransitRequestBuilder::disable);
    } else if (transit != null) {
      var access = transit.getGraphQLAccess();
      if (access != null) {
        if (access.isEmpty()) {
          throw new IllegalArgumentException("Access modes must not be empty.");
        }
        var streetModes = access.stream().map(AccessModeMapper::map).toList();
        journey.withAccess(new StreetRequest(getStreetModeForRouting(streetModes)));
      }

      var egress = transit.getGraphQLEgress();
      if (egress != null) {
        if (egress.isEmpty()) {
          throw new IllegalArgumentException("Egress modes must not be empty.");
        }
        var streetModes = egress.stream().map(EgressModeMapper::map).toList();
        journey.withEgress(new StreetRequest(getStreetModeForRouting(streetModes)));
      }

      var transfer = transit.getGraphQLTransfer();
      if (transfer != null) {
        if (transfer.isEmpty()) {
          throw new IllegalArgumentException("Transfer modes must not be empty.");
        }
        var streetModes = transfer.stream().map(TransferModeMapper::map).toList();
        journey.withTransfer(new StreetRequest(getStreetModeForRouting(streetModes)));
      }

      // TODO: This validation should be moved into the journey constructor (Feature Envy)
      validateStreetModes(journey.build());

      var transitModes = getTransitModes(environment);
      if (transitModes != null) {
        if (transitModes.isEmpty()) {
          throw new IllegalArgumentException("Transit modes must not be empty.");
        }
        var filterRequestBuilder = TransitFilterRequest.of();
        var mainAndSubModes = transitModes
          .stream()
          .map(mode ->
            new MainAndSubMode(
              TransitModeMapper.map(
                GraphQLTypes.GraphQLTransitMode.valueOf((String) mode.get("mode"))
              )
            )
          )
          .toList();
        filterRequestBuilder.addSelect(
          SelectRequest.of().withTransportModes(mainAndSubModes).build()
        );
        journey.withTransit(transitRequestBuilder ->
          transitRequestBuilder.setFilters(List.of(filterRequestBuilder.build()))
        );
      }
    }
  }
}
