FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

# Copy parent POM and all modules (Maven needs all declared modules to resolve parent POM)
COPY pom.xml ./
COPY common-lib ./common-lib
COPY build-service ./build-service
COPY upload-service ./upload-service
COPY request-handler ./request-handler

RUN apk add --no-cache maven
RUN mvn clean package -pl build-service -am -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Install Docker CLI (needed for build-service)
RUN apk add --no-cache docker-cli

COPY --from=builder /app/build-service/target/*.jar app.jar

EXPOSE 8082

ENTRYPOINT ["java", "-jar", "app.jar"]