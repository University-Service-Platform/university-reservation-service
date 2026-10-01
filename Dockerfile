FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY . .
RUN chmod +x ./gradlew && ./gradlew bootJar -x test --no-daemon

FROM eclipse-temurin:17-jre AS runtime
WORKDIR /app
COPY --from=build /app/build/libs/reservation-service.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
