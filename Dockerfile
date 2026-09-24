FROM maven:3.9.16-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src src
RUN mvn -B verify
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN mkdir /app/data && chown -R 10001:10001 /app
COPY --from=build /app/target/signalpilot-1.0.0.jar app.jar
USER 10001
ENV SIGNALPILOT_BIND=0.0.0.0
EXPOSE 8091
ENTRYPOINT ["java","-jar","app.jar"]
