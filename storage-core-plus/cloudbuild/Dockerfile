FROM azul/zulu-openjdk:17-latest

WORKDIR /app

ARG PROVIDER_NAME
ENV PROVIDER_NAME core-plus

# Copy plugins
COPY tmp/os-osm-postgres-*.jar plugins/osm-postgres.jar
COPY tmp/os-oqm-rabbitmq-*.jar plugins/oqm-rabbitmq.jar
COPY tmp/os-obm-minio-*.jar plugins/osm-minio.jar
COPY tmp/apd-openid-*.jar plugins/apd-openid.jar
COPY otel/opentelemetry-javaagent.jar /app/telemetry/opentelemetry-javaagent.jar


ARG PORT
ENV PORT $PORT

# Copy the jar to the production image from the builder stage.
COPY storage-${PROVIDER_NAME}/target/storage-${PROVIDER_NAME}-*-spring-boot.jar storage-${PROVIDER_NAME}.jar

# Add a non-root user
RUN groupadd -g 10001 -r nonroot \
  && useradd -g 10001 -r -u 10001 nonroot
# Run as non-root user
USER 10001:10001

# Run the web service on container startup.
CMD java -Djava.security.egd=file:/dev/./urandom \
         -Dserver.port=${PORT} \
         -Dlog4j.formatMsgNoLookups=true \
         -Dloader.path=plugins/ \
         -Dloader.debug=true \
         -Dloader.main=org.opengroup.osdu.storage.provider.gcp.StorageCorePlusApplication \
         -jar /app/storage-${PROVIDER_NAME}.jar
