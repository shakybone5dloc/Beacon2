# Beacon's page printer: headless Chromium behind a small HTTP service, in a container with no
# secrets. In the stack it sits on an internal network and reaches the internet only through
# the egress proxy (see proxy/). See Capture.java.

FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /src
COPY Capture.java .
RUN javac -d /out Capture.java

FROM eclipse-temurin:21-jre-alpine
# Chromium prints the pages; the fonts keep printed pages readable.
RUN apk add --no-cache chromium font-liberation ttf-freefont \
    && addgroup -S capture && adduser -S capture -G capture
WORKDIR /app
COPY --from=build /out/ .
USER capture
ENV CAPTURE_BROWSER=/usr/bin/chromium-browser \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=25"
EXPOSE 8090
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD wget -qO- http://localhost:8090/health || exit 1
ENTRYPOINT ["java", "-cp", "/app", "Capture"]
