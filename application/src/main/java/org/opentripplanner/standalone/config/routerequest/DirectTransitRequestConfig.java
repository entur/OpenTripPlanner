package org.opentripplanner.standalone.config.routerequest;

import static org.opentripplanner.standalone.config.framework.json.OtpVersion.V2_9;

import org.opentripplanner.routing.api.request.preference.DirectTransitPreferences;
import org.opentripplanner.standalone.config.framework.json.NodeAdapter;

public class DirectTransitRequestConfig {

  static void map(
    NodeAdapter root,
    DirectTransitPreferences.Builder builder
  ) {
    NodeAdapter c = root
      .of("directTransitSearch")
      .since(V2_9)
      .summary("Extend the search result with extra paths using a direct transit search")
      .description(
        """
        The direct transit search finds paths using a single transit leg, limited to a specified
        cost window. It will find paths even if they are not optimal in regard to the criteria in
        the main raptor search.
        
        This featue is off by default!
        """
      )
      .asObject();

    if (c.isEmpty()) {
      return;
    }
    var dft = DirectTransitPreferences.DEFAULT;

    builder
      .withCostRelaxFunction(
        c
          .of("costRelaxFunction")
          .since(V2_9)
          .summary("The generalized-cost window for which paths to include.")
          .description(
            """
            A generalized-cost relax function of `2x + 10m` will include paths that have a cost up 
            to 2 times plus 10 minutes compared to the cheapest path. I.e. if the cheapest path has
            a cost of 100m the results will include paths with a cost 210m.
            """
          )
          .asCostLinearFunction(dft.costRelaxFunction())
      )
      .withExtraAccessEgressCostFactor(
        c
          .of("extraAccessEgressCostFactor")
          .since(V2_9)
          .summary("Add an extra cost to access/egress legs for these results")
          .description(
            """
            The cost for access/egress will be multiplied by this factor. This can be used to limit
            the amount of walking in the paths.
            """
          )
          .asDouble(dft.extraAccessEgressCostFactor())
      )
      .withDisableAccessEgress(
        c
          // TODO Should this be maximumAccessEgressDuration instead - then 0s is the same as
          //      turning this off, and 10m ca be used to limit it to nearby stops? No setting
          //      it should use the defaults for the regular request.
          .of("disableAccessEgress")
          .since(V2_9)
          .summary("Only add paths for stop to stop searches")
          .description(
            """
            Don't include paths where access or egress is necessary. In this case the search will
            only be used when searching to and from a stop or station.
            """
          )
          .asBoolean(dft.disableAccessEgress())
      );
  }

}
