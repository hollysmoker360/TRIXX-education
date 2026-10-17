# Backend runtime image (docs/OPERATIONS.md sections 1-3).
# Multi-stage: the build stage compiles with the local Maven/JDK toolchain; the final image only
# carries the JRE and the Spring Boot jar.
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build

COPY pom.xml pom.xml
RUN mvn -B -q dependency:go-offline
COPY src src
RUN mvn -B -q package -DskipTests

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /build/target/schoolsphere-backend-0.1.0.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
