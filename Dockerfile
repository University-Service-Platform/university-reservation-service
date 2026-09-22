FROM gradle:8.10-jdk17 AS build
WORKDIR /app
COPY --chown=gradle:gradle . .
RUN gradle build -x test --no-daemon

FROM openjdk:17-jre-slim AS runtime
WORKDIR /app
COPY --from=build /app/build/libs/reservation-service.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]