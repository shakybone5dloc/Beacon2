# The Beacon API: built with the Maven wrapper, run on a small JRE image. Pages are printed
# to PDF by the separate capture service (capture/), so no browser runs here.

FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /src
# Dependencies first, so they're cached until pom.xml changes.
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -q -B dependency:go-offline
COPY src src
# Tests run in CI before images are built; they need Docker, which isn't available here.
RUN ./mvnw -q -B package -DskipTests && cp target/beacon-api-*.jar /app.jar

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S beacon && adduser -S beacon -G beacon
WORKDIR /app
COPY --from=build /app.jar app.jar
USER beacon
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health/readiness || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
