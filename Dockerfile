# --- Build stage: compile and package the fat jar ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Cache dependencies first (only re-downloads when pom.xml changes)
COPY pom.xml .
RUN mvn -q dependency:go-offline -B

# Build the app. Tests are skipped here: they need Docker (Testcontainers),
# which isn't available in the image build. Run `mvn test` in CI instead.
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
ENV PORT=8080
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar app.jar --server.port=${PORT}"]
