# syntax=docker/dockerfile:1
FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /workspace
COPY services/recommendation-service/gradle/ gradle/
COPY services/recommendation-service/gradlew services/recommendation-service/build.gradle services/recommendation-service/settings.gradle ./
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew
COPY services/recommendation-service/src/ src/
RUN --mount=type=cache,target=/root/.gradle,sharing=locked ./gradlew --no-daemon bootJar && \
    find build/libs -name '*.jar' ! -name '*-plain.jar' -exec cp {} /app.jar \;

FROM eclipse-temurin:17-jre-jammy
RUN apt-get update && apt-get install -y --no-install-recommends curl && \
    rm -rf /var/lib/apt/lists/* && groupadd --system app && useradd --system --gid app app
WORKDIR /app
RUN mkdir -p /app/logs && chown app:app /app/logs
COPY --from=build --chown=app:app /app.jar app.jar
USER app
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
