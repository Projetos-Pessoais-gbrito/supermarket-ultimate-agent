# Production image for the API (used by Render, see render.yaml and docs/DEPLOY.md)

FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Dependencies first, so code changes reuse the cached layer
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

# Tests run in CI (they need Docker for Testcontainers), not in the image build
COPY src src
RUN ./mvnw -B -q package -DskipTests \
    && cp "$(ls target/*.jar | grep -v plain)" app.jar

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 app
COPY --from=build /app/app.jar app.jar
USER app

# Small instances (512 MB): let the heap grow to 75% of the container memory
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
