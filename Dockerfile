# --- Build stage: compile and package the fat jar ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Cache dependencies first (only re-downloads when pom.xml changes)
COPY pom.xml .
RUN mvn -q dependency:go-offline -B

# Build the app. Tests are skipped here: they need Docker (Testcontainers),
# which isn't available in the image build. Run `mvn test` in CI instead.
# Exclude local-only deps (devtools, H2) from the production fat jar.
COPY src ./src
RUN mvn -q clean package -DskipTests -B

# --- Runtime stage: small JRE image with just the jar ---
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# Run as a non-root user
RUN useradd --system --uid 10001 appuser
USER appuser

COPY --from=build /app/target/bombus-*.jar app.jar

# Render provides $PORT; fall back to 8080 for local runs.
# Faster tiered compilation helps cold start stay under Render's port-scan window.
ENV PORT=8080
ENV JAVA_OPTS="-XX:TieredStopAtLevel=1 -XX:+UseSerialGC -Xss512k"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar --server.port=${PORT} --server.address=0.0.0.0"]
