FROM eclipse-temurin:25.0.2_10-jdk-noble

# The default user "ubuntu" must be removed, otherwise it supercedes the user "appuser" created
# in this Dockerfile
RUN userdel -r ubuntu

RUN mkdir -p /code/otpdata/norway

WORKDIR /code

RUN apt-get -y update && apt-get -y upgrade && apt-get install -y \
    ca-certificates \
    curl \
    && rm -rf /var/lib/apt/lists/*

COPY --chmod=444 otp-shaded/target/otp-shaded-*.jar /code/otp-shaded.jar

# Copy metrics agent (used only by Graph Builder)
RUN mkdir -p /opt/jmx
ADD --chmod=444 https://repo1.maven.org/maven2/io/prometheus/jmx/jmx_prometheus_javaagent/0.20.0/jmx_prometheus_javaagent-0.20.0.jar /opt/jmx/jmx_prometheus_javaagent.jar
# JMX Exporter config (used only by Graph Builder)
COPY --chmod=444 entur/deployment-config/docker/jmx_exporter_config.yml /opt/jmx/config.yaml

COPY --chmod=755 entur/deployment-config/docker/docker-entrypoint.sh /docker-entrypoint.sh

ENTRYPOINT ["/docker-entrypoint.sh"]

RUN  addgroup appuser \
     && adduser --disabled-password --gecos "" appuser --ingroup appuser \
     && chown -R appuser:appuser /code/otpdata

USER appuser

EXPOSE 8080
CMD ["java","-Dfile.encoding=UTF-8", "-Xms256m", "-Xmx6144m", "-server", "-javaagent:/opt/jmx/jmx_prometheus_javaagent.jar=9779:/opt/jmx/config.yaml", "-jar", "code/otp-shaded.jar", "--load", "/code/otpdata/norway", "--serve"]

# For debug, add parameter "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005"
