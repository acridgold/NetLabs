# ---- сборка ----
FROM gradle:8.10-jdk23 AS build
WORKDIR /src
COPY settings.gradle.kts build.gradle.kts ./
COPY src ./src
RUN gradle installDist --no-daemon

# ---- генератор файла ----
FROM alpine:3.20 AS gen
ENV FILE_NAME=test.bin SIZE_MB=20480
WORKDIR /files
CMD ["sh", "-c", "[ -f \"/files/$FILE_NAME\" ] || dd if=/dev/urandom of=\"/files/$FILE_NAME\" bs=1M count=$SIZE_MB; ls -lh \"/files/$FILE_NAME\""]

# ---- рантайм ----
FROM eclipse-temurin:23-jre AS app
COPY --from=build /src/build/install/lab2 /opt/lab2
WORKDIR /data
RUN mkdir -p /data/uploads
VOLUME /data/uploads
EXPOSE 5267
ENTRYPOINT ["/opt/lab2/bin/lab2"]
CMD ["server", "5267"]