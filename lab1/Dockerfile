# ---- Стадия сборки ----
FROM eclipse-temurin:23-jdk-alpine AS build
WORKDIR /app
COPY . .
RUN chmod +x ./gradlew && ./gradlew clean jar --no-daemon

# ---- Стадия запуска ----
FROM eclipse-temurin:23-jre-alpine
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar

# RUN apk add --no-cache iproute2 iputils

ENV GROUP_ADDRESS=230.0.0.1
# ENV GROUP_ADDRESS=ff02::1
ENV PORT=5000
ENV IFACE=eth0

ENTRYPOINT ["sh", "-c", "exec java -Djava.net.preferIPv4Stack=true -Djava.net.preferIPv6Addresses=false -jar app.jar \"$GROUP_ADDRESS\" \"$PORT\" \"$IFACE\""]
# ENTRYPOINT ["sh", "-c", "sleep 60; exec java -Djava.net.preferIPv4Stack=false -Djava.net.preferIPv6Addresses=true -jar app.jar \"$GROUP_ADDRESS\" \"$PORT\" \"$IFACE\""]