FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /app

COPY pom.xml ./
COPY common-lib ./common-lib
COPY build-service ./build-service

RUN apk add --no-cache maven
RUN mvn clean package -pl build-service -am -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Install Docker CLI (needed for build-service)
RUN apk add --no-cache docker-cli

COPY --from=builder /app/build-service/target/*.jar app.jar

EXPOSE 8082

ENTRYPOINT ["java", "-jar", "app.jar"]