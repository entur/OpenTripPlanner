package org.opentripplanner.ext.ojp.ojp;

import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.xml.bind.JAXBException;
import javax.xml.transform.TransformerException;
import org.opentripplanner.api.model.transit.DefaultFeedIdMapper;
import org.opentripplanner.api.model.transit.FeedScopedIdMapper;
import org.opentripplanner.api.model.transit.HideFeedIdMapper;
import org.opentripplanner.ext.ojp.RequestHandler;
import org.opentripplanner.ext.ojp.parameters.TriasApiParameters;
import org.opentripplanner.ext.ojp.service.CallAtStopService;
import org.opentripplanner.ext.ojp.service.OjpService;
import org.opentripplanner.standalone.api.OtpServerRequestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/ojp/v2")
public class OjpResource {

  private static final Logger LOG = LoggerFactory.getLogger(OjpResource.class);

  private final RequestHandler handler;

  public OjpResource(@Context OtpServerRequestContext context) {
    var transitService = context.transitService();
    var service = new CallAtStopService(transitService, context.graphFinder());
    var idMapper = idMapper(context.triasApiParameters());
    var serviceMapper = new OjpService(
      service,
      context.routingService(),
      idMapper,
      transitService.getTimeZone()
    );
    this.handler = new RequestHandler(serviceMapper, OjpCodec::serialize, "OJP");
  }

  @POST
  @Produces(MediaType.APPLICATION_XML)
  public Response index(String xmlString) {
    try {
      var ojp = OjpCodec.deserialize(xmlString);
      return handler.handleRequest(ojp);
    } catch (JAXBException | TransformerException e) {
      LOG.error("Error reading OJP request", e);
      return handler.error("Could not read TRIAS request.");
    }
  }

  private static FeedScopedIdMapper idMapper(TriasApiParameters triasApiConfig) {
    if (triasApiConfig.hideFeedId()) {
      return new HideFeedIdMapper(triasApiConfig.hardcodedInputFeedId());
    } else {
      return new DefaultFeedIdMapper();
    }
  }
}
