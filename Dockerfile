FROM maven:3.9.9-eclipse-temurin-17 AS builder
WORKDIR /workspace

COPY pom.xml ./
COPY frontend/package.json frontend/package-lock.json ./frontend/
RUN mvn -B -DskipTests dependency:go-offline

COPY src ./src
COPY frontend ./frontend
RUN mvn -B -DskipTests package

FROM eclipse-temurin:17-jre-jammy

RUN apt-get update \
    && apt-get install --no-install-recommends -y ffmpeg curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system app \
    && useradd --system --gid app --home-dir /app app

WORKDIR /app
RUN mkdir -p /app/data/db /app/data/videos \
    && chown -R app:app /app

COPY --from=builder --chown=app:app /workspace/target/ad-video-gen-java-*.jar /app/app.jar

ENV SERVER_PORT=48080 \
    SPRING_DATASOURCE_URL="jdbc:h2:file:/app/data/db/ad-video-gen;MODE=MySQL" \
    SPRING_H2_CONSOLE_SETTINGS_WEB_ALLOW_OTHERS=true \
    FFMPEG_BINARY=ffmpeg \
    FFMPEG_OUTPUT_DIR=/app/data/videos \
    PUBLIC_BASE_URL=http://localhost:48080

VOLUME ["/app/data/db", "/app/data/videos"]
EXPOSE 48080

USER app

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD curl --fail --silent http://localhost:48080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
