FROM maven:3.9.11-eclipse-temurin-17-alpine AS build

WORKDIR /workspace
COPY pom.xml ./
RUN mvn --batch-mode dependency:go-offline
COPY src ./src
RUN mvn --batch-mode package -DskipTests

FROM eclipse-temurin:17-jre-alpine

RUN addgroup -S resource && adduser -S resource -G resource
WORKDIR /app
COPY --from=build --chown=resource:resource /workspace/target/*.jar app.jar

USER resource
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
