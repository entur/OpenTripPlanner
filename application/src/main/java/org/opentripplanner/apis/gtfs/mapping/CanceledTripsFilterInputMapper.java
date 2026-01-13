package org.opentripplanner.apis.gtfs.mapping;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.opentripplanner.apis.gtfs.generated.GraphQLTypes;
import org.opentripplanner.transit.model.basic.TransitMode;

public class CanceledTripsFilterInputMapper {

  @Nullable
  public static Set<TransitMode> toTransitModes(
    List<GraphQLTypes.GraphQLCanceledTripsFilterSelectInput> inputs
  ) {
    if (inputs == null) {
      return null;
    }
    if (
      inputs
        .stream()
        .anyMatch(input -> input.getGraphQLModes() != null && input.getGraphQLModes().isEmpty())
    ) {
      throw new IllegalArgumentException(
        "Mode filter must be either null or have at least one entry."
      );
    }
    return inputs
      .stream()
      .flatMap(include -> {
        var modes = include.getGraphQLModes();
        if (modes == null) {
          return Stream.of();
        }
        return include.getGraphQLModes().stream().map(TransitModeMapper::map);
      })
      .collect(Collectors.toSet());
  }
}
