#!/bin/bash

set -e

echo "Starting storage service for provider: ${PROVIDER_NAME}"
echo "OpenTelemetry enabled: ${OTEL_JAVAAGENT_ENABLED}"
echo "Server port: ${PORT}"

JAVA_OPTS="--add-opens java.base/java.lang=ALL-UNNAMED \
           --add-opens java.base/java.lang.reflect=ALL-UNNAMED \
           -Djava.security.egd=file:/dev/./urandom \
           -Dserver.port=${PORT} \
           -Dlog4j.formatMsgNoLookups=true \
           -Dloader.main=org.opengroup.osdu.storage.provider.gcp.StorageApplicationGCP"

if [ "${OTEL_JAVAAGENT_ENABLED}" = "true" ]; then
  echo "Telemetry enabled"
  JAVA_OPTS="$JAVA_OPTS -javaagent:/app/telemetry/opentelemetry-javaagent.jar"
else
  echo "Telemetry disabled"
fi

echo "Running with options:"
echo "${JAVA_OPTS}"

exec java ${JAVA_OPTS} -jar "/app/storage-${PROVIDER_NAME}.jar"
