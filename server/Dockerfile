# syntax=docker/dockerfile:1.7
FROM maven:3.9-eclipse-temurin-21@sha256:2b4496088e7b80ae10a8c9f74e574ea21380325a006ec684532ad6bad5bc7273 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN --mount=type=cache,target=/root/.m2/repository \
    mvn -B -Dmaven.test.skip=true package

FROM eclipse-temurin:21-jre@sha256:d2b9f8f12212cadcfdf889461531784e8fd097feade954d65b31ee7a71c473ec
ARG SOURCE_COMMIT=unknown
LABEL org.opencontainers.image.revision=$SOURCE_COMMIT
WORKDIR /app
ENV SPRING_PROFILES_ACTIVE=prod
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build /workspace/target/colla-platform-server-*.jar /app/colla-platform-server.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/colla-platform-server.jar"]
