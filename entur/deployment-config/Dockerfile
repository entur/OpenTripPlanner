# OTP runtime image: the Temurin 25 JRE on a distroless base (gcr.io/distroless/java25-debian13).
#
# The image has no shell, no package manager and no JDK tools. There is no entrypoint script: the
# journey-planner pods load the graph published under a stable, serialization-version-specific name
# (see README.md, "Graph lookup"), so nothing needs to be resolved at startup.
#
# Debugging a running pod: use an ephemeral debug container with a full JDK, e.g.
#   kubectl debug -it <pod> --image=eclipse-temurin:25-jdk-noble --target=otp2 --profile=restricted
#
# The ":nonroot" tag runs as uid 65532; Kubernetes overrides this with runAsUser 1000.
FROM gcr.io/distroless/java25-debian13:nonroot

# Data directory. In Kubernetes the config files are mounted into it and the graph is streamed
# from GCS; locally, mount a graph directory here. WORKDIR is used because the image has no shell.
WORKDIR /code/otpdata/norway
WORKDIR /code

COPY --chmod=444 otp-shaded/target/otp-shaded-*.jar /code/otp-shaded.jar

# Metrics agent (used only by Graph Builder)
ADD --chmod=444 https://repo1.maven.org/maven2/io/prometheus/jmx/jmx_prometheus_javaagent/0.20.0/jmx_prometheus_javaagent-0.20.0.jar /opt/jmx/jmx_prometheus_javaagent.jar
# JMX Exporter config (used only by Graph Builder)
COPY --chmod=444 entur/deployment-config/docker/jmx_exporter_config.yml /opt/jmx/config.yaml

EXPOSE 8080

# The base image's entrypoint is "java -jar"; reset it so that the command line is given in full,
# as the Helm charts do. The CMD below is the default for local use.
ENTRYPOINT []
CMD ["java","-Dfile.encoding=UTF-8", "-Xms256m", "-Xmx6144m", "-server", "-javaagent:/opt/jmx/jmx_prometheus_javaagent.jar=9779:/opt/jmx/config.yaml", "-jar", "/code/otp-shaded.jar", "--load", "/code/otpdata/norway", "--serve"]

# For debug, add parameter "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"
