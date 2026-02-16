FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /app

# Copy parent POM and common-lib first
COPY pom.xml ./
COPY common-lib ./common-lib
COPY upload-service ./upload-service

# Build
RUN apk add --no-cache maven
RUN mvn clean package -pl upload-service -am -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Copy JAR
COPY --from=builder /app/upload-service/target/*.jar app.jar

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]