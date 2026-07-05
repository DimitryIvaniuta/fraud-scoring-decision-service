FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
COPY . .
RUN ./gradlew clean bootJar --no-daemon

FROM eclipse-temurin:25-jre
WORKDIR /app
RUN addgroup --system fraud && adduser --system --ingroup fraud fraud
COPY --from=build /workspace/build/libs/fraud-scoring-decision-service.jar /app/app.jar
USER fraud:fraud
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+UseG1GC", "-jar", "/app/app.jar"]
