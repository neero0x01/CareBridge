# Build
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

# Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN apk add --no-cache curl \
    && addgroup -S carebridge && adduser -S carebridge -G carebridge
COPY --from=build /app/target/carebridge-api-*.jar /app/app.jar
USER carebridge
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
