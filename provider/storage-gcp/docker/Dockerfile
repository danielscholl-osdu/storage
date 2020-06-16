FROM openjdk:8-slim
RUN apt-get update && apt-get install -y curl

ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-jar","/app.jar"]
EXPOSE 8080

ARG JAR_FILE
COPY ${JAR_FILE} app.jar
