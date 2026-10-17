FROM maven:3.9.16-eclipse-temurin-21@sha256:99e61abcff91a9b1333463bd8451fb18495d6eba9250ac66a338b518f8278320 AS build
WORKDIR /app
ARG GIT_COMMIT=unknown
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src src
RUN mvn -B -Dbuild.commit="${GIT_COMMIT}" verify
FROM eclipse-temurin:21-jre@sha256:227118764cccd3c3294617061aaa93a08309c63a395eefa83e6b35032992ea99
WORKDIR /app
RUN rm -f /usr/bin/pebble \
    && mkdir /app/data \
    && chown -R 10001:10001 /app
COPY --from=build --chown=10001:10001 /app/target/signalpilot-1.0.0.jar app.jar
USER 10001
ENV SIGNALPILOT_BIND=0.0.0.0
EXPOSE 8091
ENTRYPOINT ["java","-jar","app.jar"]
