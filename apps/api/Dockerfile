FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn --batch-mode -DskipTests dependency:go-offline
COPY src src
RUN --mount=type=cache,target=/root/.m2 mvn --batch-mode -DskipTests package
FROM eclipse-temurin:21-jre
RUN apt-get update && apt-get install -y --no-install-recommends wget && rm -rf /var/lib/apt/lists/*
RUN useradd --system --uid 10001 flowmind
WORKDIR /app
COPY --from=build /workspace/target/flowmind-api-*.jar app.jar
USER 10001
EXPOSE 8080
ENTRYPOINT ["java","-jar","app.jar"]

