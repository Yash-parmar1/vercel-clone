FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

# Copy parent POM and all modules (Maven needs all declared modules to resolve parent POM)
COPY pom.xml ./
COPY common-lib ./common-lib
COPY request-handler ./request-handler
COPY upload-service ./upload-service
COPY build-service ./build-service

RUN apk add --no-cache maven
RUN mvn clean package -pl request-handler -am -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=builder /app/request-handler/target/*.jar app.jar

EXPOSE 8083

ENTRYPOINT ["java", "-jar", "app.jar"]