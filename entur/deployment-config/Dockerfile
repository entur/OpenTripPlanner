FROM gcr.io/distroless/java25-debian13:nonroot

WORKDIR /code/otpdata/norway
WORKDIR /code

COPY --chmod=444 otp-shaded/target/otp-shaded-*.jar /code/otp-shaded.jar

# Metrics agent (used only by Graph Builder)
ADD --chmod=444 https://repo1.maven.org/maven2/io/prometheus/jmx/jmx_prometheus_javaagent/0.20.0/jmx_prometheus_javaagent-0.20.0.jar /opt/jmx/jmx_prometheus_javaagent.jar
# JMX Exporter config (used only by Graph Builder)
COPY --chmod=444 entur/deployment-config/docker/jmx_exporter_config.yml /opt/jmx/config.yaml

EXPOSE 8080

ENTRYPOINT []
CMD ["java","-Dfile.encoding=UTF-8", "-Xms256m", "-Xmx6144m", "-server", "-javaagent:/opt/jmx/jmx_prometheus_javaagent.jar=9779:/opt/jmx/config.yaml", "-jar", "/code/otp-shaded.jar", "--load", "/code/otpdata/norway", "--serve"]

# For debug, add parameter "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"
