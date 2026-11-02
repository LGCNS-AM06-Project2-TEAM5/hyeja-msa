# syntax=docker/dockerfile:1
FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /workspace
COPY infra/config-service/gradle/ gradle/
COPY infra/config-service/gradlew infra/config-service/build.gradle infra/config-service/settings.gradle ./
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew
COPY infra/config-service/src/ src/
RUN --mount=type=cache,target=/root/.gradle,sharing=locked ./gradlew --no-daemon bootJar && \
    find build/libs -name '*.jar' ! -name '*-plain.jar' -exec cp {} /app.jar \;

FROM eclipse-temurin:17-jre-jammy
RUN apt-get update && apt-get install -y --no-install-recommends curl && \
    rm -rf /var/lib/apt/lists/* && groupadd --system app && useradd --system --gid app app
WORKDIR /app
COPY --from=build --chown=app:app /app.jar app.jar
USER app
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
