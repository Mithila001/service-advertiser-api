FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -B -Dmaven.test.skip=true package && cp target/*.jar /build/app.jar

FROM eclipse-temurin:21-jre-jammy
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 app && useradd --uid 10001 --gid app --no-create-home app \
    && mkdir -p /var/log/service-advertiser && chown app:app /var/log/service-advertiser
WORKDIR /app
COPY --from=build --chown=app:app /build/app.jar app.jar
USER 10001:10001
ENTRYPOINT ["java", "-jar", "app.jar"]
