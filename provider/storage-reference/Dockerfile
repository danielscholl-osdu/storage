FROM openjdk:8-slim
WORKDIR /app
COPY target/storage-reference-0.0.5-SNAPSHOT-spring-boot.jar storage-reference.jar
# Run the web service on container startup.
CMD java -Djava.security.egd=file:/dev/./urandom -Dserver.port=8080 -jar /app/storage-reference.jar
