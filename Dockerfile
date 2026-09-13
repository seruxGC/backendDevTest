FROM maven:3.9.11-eclipse-temurin-21 AS build

WORKDIR /workspace

COPY pom.xml ./
RUN mvn --batch-mode dependency:go-offline

COPY src ./src
RUN mvn --batch-mode --no-transfer-progress package

FROM eclipse-temurin:21-jre AS runtime

WORKDIR /app

COPY --from=build --chown=10001:10001 \
    /workspace/target/similar-products-0.0.1-SNAPSHOT.jar \
    /app/app.jar

USER 10001:10001

EXPOSE 5000

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
