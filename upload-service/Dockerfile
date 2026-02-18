FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

# Copy parent POM and all modules (Maven needs all declared modules to resolve parent POM)
COPY pom.xml ./
COPY common-lib ./common-lib
COPY upload-service ./upload-service
COPY build-service ./build-service
COPY request-handler ./request-handler

# Build
RUN apk add --no-cache maven
RUN mvn clean package -pl upload-service -am -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copy JAR
COPY --from=builder /app/upload-service/target/*.jar app.jar

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]