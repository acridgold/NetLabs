# ---- Стадия сборки ----
FROM eclipse-temurin:23-jdk AS build
WORKDIR /app
COPY . .
RUN chmod +x ./gradlew && ./gradlew clean jar --no-daemon

# ---- Стадия запуска ----
FROM eclipse-temurin:23-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar

RUN apt-get update && apt-get install -y --no-install-recommends iproute2 iputils-ping \
    && rm -rf /var/lib/apt/lists/*

ENV GROUP_ADDRESS=230.0.0.1
ENV PORT=5000
ENV IFACE=eth0

ENTRYPOINT ["sh", "-c", "exec java -jar app.jar \"$GROUP_ADDRESS\" \"$PORT\" \"$IFACE\""]