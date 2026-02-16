FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /app

COPY pom.xml ./
COPY common-lib ./common-lib
COPY request-handler ./request-handler

RUN apk add --no-cache maven
RUN mvn clean package -pl request-handler -am -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

COPY --from=builder /app/request-handler/target/*.jar app.jar

EXPOSE 8083

ENTRYPOINT ["java", "-jar", "app.jar"]