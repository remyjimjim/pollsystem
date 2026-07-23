# syntax=docker/dockerfile:1
#
# Backend container image for Fly.io (see docs/DEPLOYING-FLY.md).
# Conservative-baseline path: plain JVM, ~1–2 GB RAM per instance.

# --- Build stage: compile the Spring Boot fat JAR ---------------------------
# eclipse-temurin has no system `gradle`, so we build with the repo's pinned
# Gradle wrapper (./gradlew, 8.10.2) rather than a `gradle` that isn't installed.
FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /app

# Wrapper + build scripts first so the Gradle distribution and dependency
# downloads cache in their own layer, independent of source changes.
COPY gradlew ./
COPY gradle ./gradle
COPY settings.gradle.kts build.gradle.kts ./
RUN --mount=type=cache,target=/root/.gradle \
    chmod +x gradlew && ./gradlew --version --no-daemon

COPY src ./src
RUN --mount=type=cache,target=/root/.gradle ./gradlew bootJar -x test --no-daemon

# --- Runtime stage: JRE only, non-root -------------------------------------
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
# logback-spring.xml's prod profile writes combined.log/error.log under ./logs
# (LOG_DIR default). Create it writable by the non-root user so file logging
# works. Console logging also goes to stdout, so `flyctl logs` works regardless;
# these files are ephemeral (lost on machine restart) and capped at ~1.5 GB.
RUN useradd --system --uid 1001 spring \
 && mkdir -p /app/logs \
 && chown spring /app/logs
COPY --from=build /app/build/libs/pollsystem-0.0.1-SNAPSHOT.jar app.jar
USER spring
EXPOSE 8080
# MaxRAMPercentage lets the JVM use most of the Fly machine's memory_mb.
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75.0","-jar","/app/app.jar"]
