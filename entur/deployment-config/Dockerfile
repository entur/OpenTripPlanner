FROM eclipse-temurin:21.0.7_6-jre-noble

# The default user "ubuntu" must be removed, otherwise it supercedes the user "appuser" created
# in this Dockerfile
RUN userdel -r ubuntu

RUN mkdir -p /code/otpdata/norway

WORKDIR /code

RUN apt-get -y update && apt-get -y upgrade && apt-get install -y \
    apt-transport-https \
    ca-certificates \
    gnupg \
    curl

# From https://cloud.google.com/sdk/docs/install
RUN echo "deb [signed-by=/usr/share/keyrings/cloud.google.gpg] https://packages.cloud.google.com/apt cloud-sdk main" | tee -a /etc/apt/sources.list.d/google-cloud-sdk.list \
    && curl https://packages.cloud.google.com/apt/doc/apt-key.gpg | gpg --dearmor -o /usr/share/keyrings/cloud.google.gpg \
    && apt-get update -y \
    && apt-get install -y google-cloud-cli

COPY --chmod=444 otp-shaded/target/otp-shaded-*.jar /code/otp-shaded.jar

# Copy metrics agent (used only by Graph Builder)
RUN mkdir -p /opt/agent-bond
ADD --chmod=444 https://repo1.maven.org/maven2/io/fabric8/agent-bond-agent/1.2.0/agent-bond-agent-1.2.0.jar /opt/agent-bond/agent-bond.jar
COPY --chmod=444 entur/deployment-config/docker/jmx_exporter_config.yml /opt/agent-bond/

COPY --chmod=755 entur/deployment-config/docker/docker-entrypoint.sh /docker-entrypoint.sh

ENTRYPOINT ["/docker-entrypoint.sh"]

RUN  addgroup appuser \
     && adduser --disabled-password --gecos "" appuser --ingroup appuser \
     && chown -R appuser:appuser /code/otpdata

USER appuser

EXPOSE 8080
CMD ["java","-Dfile.encoding=UTF-8", "-Xms256m", "-Xmx6144m", "-server", "-javaagent:/opt/agent-bond/agent-bond.jar=jolokia{{'{{' }}host=0.0.0.0{{ '}}' }},jmx_exporter{{ '{{' }}9779:/opt/agent-bond/jmx_exporter_config.yml{{ '}}' }}", "-jar", "code/otp-shaded.jar", "--load", "/code/otpdata/norway", "--serve"]

# For debug, add parameter "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005"
