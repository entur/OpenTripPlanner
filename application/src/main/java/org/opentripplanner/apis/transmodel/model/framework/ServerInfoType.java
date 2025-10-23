package org.opentripplanner.apis.transmodel.model.framework;

import static org.opentripplanner.framework.application.OtpFileNames.BUILD_CONFIG_FILENAME;
import static org.opentripplanner.framework.application.OtpFileNames.OTP_CONFIG_FILENAME;
import static org.opentripplanner.framework.application.OtpFileNames.ROUTER_CONFIG_FILENAME;
import static org.opentripplanner.model.projectinfo.OtpProjectInfo.projectInfo;

import graphql.Scalars;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLOutputType;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.opentripplanner.apis.transmodel.support.GqlUtil;

public class ServerInfoType {

  private static int getCpuCores() {
    int nCores = 0;
    try {
      InputStream fis = new FileInputStream("/proc/cpuinfo");
      BufferedReader br = new BufferedReader(new InputStreamReader(fis, StandardCharsets.UTF_8));
      String line;
      while ((line = br.readLine()) != null) {
        if (line.startsWith("model name")) {
          nCores += 1;
        }
      }
      fis.close();
    } catch (Exception ignore) {}
    return nCores;
  }

  public static GraphQLOutputType create() {
    return GraphQLObjectType.newObject()
      .name("ServerInfo")
      .description(
        """
        Information about the deployment. This is only useful to developers of OTP itself.
        It is not recommended for regular API consumers to use this type as it has no
        stability guarantees.
        """
      )
      .field(
        GraphQLFieldDefinition.newFieldDefinition()
          .name("version")
          .description("Maven version")
          .type(Scalars.GraphQLString)
          .dataFetcher(e -> projectInfo().version)
          .build()
      )
      .field(
        GraphQLFieldDefinition.newFieldDefinition()
          .name("buildTime")
          .description("OTP Build timestamp")
          .type(Scalars.GraphQLString)
          .dataFetcher(e -> projectInfo().versionControl.buildTime)
          .build()
      )
      .field(
        GraphQLFieldDefinition.newFieldDefinition()
          .name("gitBranch")
          .description("")
          .type(Scalars.GraphQLString)
          .dataFetcher(e -> projectInfo().versionControl.branch)
          .build()
      )
      .field(
        GraphQLFieldDefinition.newFieldDefinition()
          .name("gitCommit")
          .description("")
          .type(Scalars.GraphQLString)
          .dataFetcher(e -> projectInfo().versionControl.commit)
          .build()
      )
      .field(
        GraphQLFieldDefinition.newFieldDefinition()
          .name("gitCommitTime")
          .description("")
          .type(Scalars.GraphQLString)
          .dataFetcher(e -> projectInfo().versionControl.commitTime)
          .build()
      )
      .field(
        GraphQLFieldDefinition.newFieldDefinition()
          .name("otpConfigVersion")
          .description("The 'configVersion' of the " + OTP_CONFIG_FILENAME + " file.")
          .type(Scalars.GraphQLString)
          .dataFetcher(e -> projectInfo().otpConfigVersion)
          .build()
      )
      .field(
        GraphQLFieldDefinition.newFieldDefinition()
          .name("buildConfigVersion")
          .description("The 'configVersion' of the " + BUILD_CONFIG_FILENAME + " file.")
          .type(Scalars.GraphQLString)
          .dataFetcher(e -> projectInfo().buildConfigVersion)
          .build()
      )
      .field(
        GraphQLFieldDefinition.newFieldDefinition()
          .name("routerConfigVersion")
          .description("The 'configVersion' of the " + ROUTER_CONFIG_FILENAME + " file.")
          .type(Scalars.GraphQLString)
          .dataFetcher(e -> projectInfo().routerConfigVersion)
          .build()
      )
      .field(
        GraphQLFieldDefinition.newFieldDefinition()
          .name("otpSerializationVersionId")
          .description(
            "The otp-serialization-version-id used to check graphs for compatibility with current version of OTP."
          )
          .type(Scalars.GraphQLString)
          .dataFetcher(e -> projectInfo().getOtpSerializationVersionId())
          .build()
      )
      .field(
        GraphQLFieldDefinition.newFieldDefinition()
          .name("internalTransitModelTimeZone")
          .description(
            """
              The internal time zone of the transit data.

              Note: The input data can be in several time zones, but OTP internally operates on a single one.
            """
          )
          .type(Scalars.GraphQLString)
          .dataFetcher(e -> GqlUtil.getTransitService(e).getTimeZone())
          .build()
      )
      .field(
        GraphQLFieldDefinition.newFieldDefinition()
          .name("nCores")
          .description("Number of CPU cores on the OTP server")
          .type(Scalars.GraphQLInt)
          .dataFetcher(e -> getCpuCores())
          .build()
      )
      .field(
        GraphQLFieldDefinition.newFieldDefinition()
          .name("transitServiceValidityEnd")
          .description("End date of the transit data validity period")
          .type(Scalars.GraphQLString)
          .dataFetcher(e -> {
            var zonedDateTime = GqlUtil.getTransitService(e).getTransitServiceEnds();
            return zonedDateTime != null ? zonedDateTime.toLocalDate().toString() : null;
          })
          .build()
      )
      .field(
        GraphQLFieldDefinition.newFieldDefinition()
          .name("transitServiceValidityStart")
          .description("Start date of the transit data validity period")
          .type(Scalars.GraphQLString)
          .dataFetcher(e -> {
            var zonedDateTime = GqlUtil.getTransitService(e).getTransitServiceStarts();
            return zonedDateTime != null ? zonedDateTime.toLocalDate().toString() : null;
          })
          .build()
      )
      .build();
  }
}
