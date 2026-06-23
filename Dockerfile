FROM eclipse-temurin:17-jre-ubi9-minimal

ADD docker/ffmpeg/ffmpeg-master-latest-linux64-gpl.tar.xz /tmp/ffmpeg/

RUN cp /tmp/ffmpeg/ffmpeg-master-latest-linux64-gpl/bin/ffmpeg /usr/local/bin/ffmpeg \
    && chmod 0755 /usr/local/bin/ffmpeg \
    && rm -rf /tmp/ffmpeg

WORKDIR /app
RUN mkdir -p /app/data/db /app/data/videos \
    && chown -R 1001:0 /app \
    && chmod -R g=u /app

COPY --chown=1001:0 target/ad-video-gen-java-*.jar /app/app.jar

# S3_ACCESS_KEY / S3_SECRET_KEY 无默认值，需在 docker run 时传入
ENV SERVER_PORT=48080 \
    SPRING_DATASOURCE_URL="jdbc:h2:file:/app/data/db/ad-video-gen;MODE=MySQL" \
    SPRING_H2_CONSOLE_SETTINGS_WEB_ALLOW_OTHERS=true \
    FFMPEG_BINARY=ffmpeg \
    FFMPEG_OUTPUT_DIR=/app/data/videos

VOLUME ["/app/data/db", "/app/data/videos"]
EXPOSE 48080

USER 1001

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
