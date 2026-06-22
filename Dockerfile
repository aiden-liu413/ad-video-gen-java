FROM eclipse-temurin:17-jre-ubi9-minimal

WORKDIR /app

RUN mkdir -p /app/data/db /app/data/videos \
    && chown -R 1001:0 /app \
    && chmod -R g=u /app

COPY --chown=1001:0 docker/ffmpeg/ffmpeg /usr/local/bin/ffmpeg
COPY --chown=1001:0 target/ad-video-gen-java-*.jar /app/app.jar

ENV SERVER_PORT=48080 \
    SPRING_DATASOURCE_URL="jdbc:h2:file:/app/data/db/ad-video-gen;MODE=MySQL" \
    SPRING_H2_CONSOLE_SETTINGS_WEB_ALLOW_OTHERS=true \
    FFMPEG_BINARY=ffmpeg \
    FFMPEG_OUTPUT_DIR=/app/data/videos \
    PUBLIC_BASE_URL=http://localhost:48080

VOLUME ["/app/data/db", "/app/data/videos"]
EXPOSE 48080

USER 1001

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
