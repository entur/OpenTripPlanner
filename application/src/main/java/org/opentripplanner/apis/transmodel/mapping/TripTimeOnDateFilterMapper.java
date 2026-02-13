package org.opentripplanner.apis.transmodel.mapping;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.opentripplanner.api.model.transit.FeedScopedIdMapper;
import org.opentripplanner.apis.transmodel.model.TransmodelTransportSubmode;
import org.opentripplanner.transit.model.basic.MainAndSubMode;
import org.opentripplanner.transit.model.basic.SubMode;
import org.opentripplanner.transit.model.basic.TransitMode;
import org.opentripplanner.transit.model.filter.transit.TripTimeOnDateFilterRequest;
import org.opentripplanner.transit.model.filter.transit.TripTimeOnDateSelectRequest;

/**
 * Maps GraphQL {@code EstimatedCallFilterInput} to a list of
 * {@link TripTimeOnDateFilterRequest} objects by extracting the {@code select} and {@code not}
 * criteria from the GraphQL input.
 * <p>
 * Each filter has {@code select} and {@code not} arrays of select criteria.
 */
public class TripTimeOnDateFilterMapper {

  private final FeedScopedIdMapper idMapper;

  public TripTimeOnDateFilterMapper(FeedScopedIdMapper idMapper) {
    this.idMapper = idMapper;
  }

  @SuppressWarnings("unchecked")
  public List<TripTimeOnDateFilterRequest> mapFilters(List<Map<String, ?>> filters) {
    var filterRequests = new ArrayList<TripTimeOnDateFilterRequest>();

    for (var filterInput : filters) {
      var filterRequestBuilder = TripTimeOnDateFilterRequest.of();

      if (filterInput.containsKey("select")) {
        var select = (List<Map<String, List<?>>>) filterInput.get("select");
        for (var it : select) {
          filterRequestBuilder.addSelect(mapSelectRequest(it));
        }
      }
      if (filterInput.containsKey("not")) {
        var not = (List<Map<String, List<?>>>) filterInput.get("not");
        for (var it : not) {
          filterRequestBuilder.addNot(mapSelectRequest(it));
        }
      }
      filterRequests.add(filterRequestBuilder.build());
    }
    return filterRequests;
  }

  @SuppressWarnings("unchecked")
  private TripTimeOnDateSelectRequest mapSelectRequest(Map<String, List<?>> input) {
    var builder = TripTimeOnDateSelectRequest.of();

    if (input.containsKey("lines")) {
      var lines = (List<String>) input.get("lines");
      builder.withRoutes(idMapper.parseListNullSafe(lines));
    }

    if (input.containsKey("authorities")) {
      var authorities = (List<String>) input.get("authorities");
      builder.withAgencies(idMapper.parseListNullSafe(authorities));
    }

    if (input.containsKey("transportModes")) {
      var tModes = new ArrayList<MainAndSubMode>();

      var transportModes = (List<Map<String, ?>>) input.get("transportModes");
      for (Map<String, ?> modeWithSubModes : transportModes) {
        var mainMode = (TransitMode) modeWithSubModes.get("transportMode");
        if (modeWithSubModes.containsKey("transportSubModes")) {
          var transportSubModes = (List<TransmodelTransportSubmode>) modeWithSubModes.get(
            "transportSubModes"
          );
          for (var subMode : transportSubModes) {
            tModes.add(new MainAndSubMode(mainMode, SubMode.of(subMode.getValue())));
          }
        } else {
          tModes.add(new MainAndSubMode(mainMode));
        }
      }
      builder.withTransportModes(tModes);
    }

    return builder.build();
  }
}
