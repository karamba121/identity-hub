FROM maven:3.9.11-eclipse-temurin-17-alpine AS build

WORKDIR /workspace

COPY pom.xml ./
RUN mvn --batch-mode dependency:go-offline

COPY src ./src
RUN mvn --batch-mode package -DskipTests

FROM eclipse-temurin:17-jre-alpine

RUN addgroup -S identityhub && adduser -S identityhub -G identityhub

WORKDIR /app
COPY --from=build --chown=identityhub:identityhub /workspace/target/*.jar app.jar

USER identityhub
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
