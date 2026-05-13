package org.opentripplanner.apis.vectortiles;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import org.opentripplanner.inspector.vector.astar.AStarTraceSummary;
import org.opentripplanner.standalone.api.OtpServerRequestContext;

/**
 * REST endpoint for managing A* search traces used by the debug vector tile
 * visualization. Allows listing available traces and selecting which one
 * to render on the map.
 */
@Path("/debug/astar-trace")
public class AStarTraceResource {

  private final OtpServerRequestContext serverContext;

  public AStarTraceResource(@Context OtpServerRequestContext serverContext) {
    this.serverContext = serverContext;
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public List<AStarTraceSummary> listTraces() {
    var store = serverContext.aStarTraceStore();
    if (store == null) {
      return List.of();
    }
    return store.listTraces();
  }

  @GET
  @Path("/active")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getActiveTrace() {
    var store = serverContext.aStarTraceStore();
    if (store == null) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }
    return store
      .getActiveTraceId()
      .map(id -> Response.ok(id).build())
      .orElse(Response.status(Response.Status.NOT_FOUND).build());
  }

  @POST
  @Path("/{id}/activate")
  public Response activateTrace(@PathParam("id") String id) {
    var store = serverContext.aStarTraceStore();
    if (store == null) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }
    store.setActiveTrace(id);
    return Response.ok().build();
  }

  /**
   * Walk the parent chain from a vertex back to the search origin and return
   * the branch as a GeoJSON LineString.
   */
  @GET
  @Path("/{id}/branch")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getBranch(@PathParam("id") String id, @QueryParam("vertex") String vertexId) {
    var store = serverContext.aStarTraceStore();
    if (store == null || vertexId == null) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }
    var trace = store.getTrace(id);
    if (trace.isEmpty()) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }

    var coords = trace.get().branchToOrigin(vertexId);
    if (coords.size() < 2) {
      return Response.ok("{\"type\":\"FeatureCollection\",\"features\":[]}").build();
    }

    // Build GeoJSON manually to avoid adding a dependency
    var sb = new StringBuilder();
    sb.append("{\"type\":\"Feature\",\"geometry\":{\"type\":\"LineString\",\"coordinates\":[");
    for (int i = 0; i < coords.size(); i++) {
      if (i > 0) {
        sb.append(",");
      }
      sb.append("[").append(coords.get(i).x).append(",").append(coords.get(i).y).append("]");
    }
    sb.append("]},\"properties\":{\"vertices\":").append(coords.size()).append("}}");
    return Response.ok(sb.toString()).build();
  }
}
