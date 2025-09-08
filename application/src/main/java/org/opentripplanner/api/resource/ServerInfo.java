package org.opentripplanner.api.resource;

import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.CacheControl;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.opentripplanner.api.model.serverinfo.ApiServerInfo;
import org.opentripplanner.model.projectinfo.OtpProjectInfo;

@Path("/")
public class ServerInfo {

  private static final ApiServerInfo SERVER_INFO = createServerInfo();
  private static final int MARGIN = 50;
  private static final int TEXT_INSET = 5;
  private static final int FONT_WIDTH = 82;

  /**
   * Determine the OTP version and CPU type of the running server. This information should not
   * change while the server is up, so it can safely be cached at startup. The project info is not
   * available before the graph is loaded, so for this to work this class should not be loaded
   * BEFORE that.
   */
  public static ApiServerInfo createServerInfo() {
    String cpuName = "unknown";
    int nCores = 0;
    try {
      InputStream fis = new FileInputStream("/proc/cpuinfo");
      BufferedReader br = new BufferedReader(new InputStreamReader(fis, StandardCharsets.UTF_8));
      String line;
      while ((line = br.readLine()) != null) {
        if (line.startsWith("model name")) {
          cpuName = line.split(": ")[1];
          nCores += 1;
        }
      }
      fis.close();
    } catch (Exception ignore) {}

    return new ApiServerInfo(cpuName, nCores, OtpProjectInfo.projectInfo());
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public ApiServerInfo getServerInfo() {
    return SERVER_INFO;
  }

  @GET
  @Path("version-badge.svg")
  @Produces("image/svg+xml;charset=utf-8")
  public Response getVersionBadge(
    @QueryParam("color") @DefaultValue("#E43") String color,
    @QueryParam("label") @DefaultValue("OTP Version") String label
  ) {
    String version =
      "%s (%s)".formatted(SERVER_INFO.version.getVersion(), SERVER_INFO.otpSerializationVersionId);
    var svg = generateOtpBadgeSvg(color, label, version);
    var cacheControl = new CacheControl();
    cacheControl.setMaxAge(120);

    return Response.ok(svg).cacheControl(cacheControl).build();
  }

  static String generateOtpBadgeSvg(String color, String label, String version) {
    int labelLength = fontWith(label);
    int versionLength = fontWith(version);
    int totalWidth = 4 * MARGIN + labelLength + versionLength;

    return """
    <svg width="{{svgWidth}}" height="20" viewBox="0 0 {{width}} 200"
        xmlns="http://www.w3.org/2000/svg" role="img" aria-label="{{label}}: {{version}}">
      <title>{{label}}: {{version}}</title>
      <linearGradient id="grad1" x2="0" y2="100%">
        <stop offset="0" stop-opacity=".1" stop-color="#EEE"/>
        <stop offset="1" stop-opacity=".1"/>
      </linearGradient>
      <mask id="mask1"><rect width="{{width}}" height="200" rx="30" fill="#FFF"/></mask>
      <g mask="url(#mask1)">
        <rect width="{{labelWidth}}" height="200" fill="{{color}}"/>
        <rect width="{{versionWidth}}" height="200" fill="#2179BF" x="{{labelWidth}}"/>
        <rect width="{{width}}" height="200" fill="url(#grad1)"/>
      </g>
      <g aria-hidden="true" fill="#fff" text-anchor="start" font-family="Verdana,DejaVu Sans,sans-serif" font-size="110">
        <text x="60" y="148" textLength="{{labelLength}}" fill="#000" opacity="0.25">{{label}}</text>
        <text x="50" y="138" textLength="{{labelLength}}">{{label}}</text>
        <text x="{{versionOffsetA}}" y="148" textLength="{{versionLength}}" fill="#000" opacity="0.25">{{version}}</text>
        <text x="{{versionOffsetB}}" y="138" textLength="{{versionLength}}">{{version}}</text>
      </g>
    </svg>
    """.replace("{{version}}", version)
      .replace("{{color}}", color)
      .replace("{{label}}", label)
      .replace("{{svgWidth}}", Double.toString(totalWidth / 10.0))
      .replace("{{width}}", Integer.toString(totalWidth))
      .replace("{{labelWidth}}", Integer.toString(labelLength + 2 * MARGIN))
      .replace("{{versionWidth}}", Integer.toString(versionLength + 2 * MARGIN))
      .replace("{{labelLength}}", Integer.toString(labelLength))
      .replace("{{versionLength}}", Integer.toString(versionLength))
      .replace("{{versionOffsetA}}", Integer.toString(labelLength + 3 * MARGIN + TEXT_INSET))
      .replace("{{versionOffsetB}}", Integer.toString(labelLength + 3 * MARGIN - TEXT_INSET));
  }

  /**
   * This method estimate the witdh needed for the given {@code text}. It is not accurate,
   * but the text will be stretched/compressed to match the width.
   */
  private static int fontWith(String text) {
    int length = text.length();
    int small = text.replaceAll("[^ .il1\\()\\[\\]{}!,:;]", "").length();
    int big = length - small;
    return big * FONT_WIDTH + small * ((FONT_WIDTH * 3) / 5);
  }
}
