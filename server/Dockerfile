FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
ENV SPRING_PROFILES_ACTIVE=prod
COPY --from=build /workspace/target/colla-platform-server-*.jar /app/colla-platform-server.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/colla-platform-server.jar"]
