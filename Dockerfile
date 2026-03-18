# ── Stage 1: Build ────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk AS build

WORKDIR /app

COPY . .
RUN chmod +x mvnw && \
    sed -i 's/distributionSha256Sum=.*/distributionSha256Sum=/' .mvn/wrapper/maven-wrapper.properties && \
    ./mvnw package -DskipTests -q

# ── Stage 2: Runtime ──────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre

WORKDIR /work/
COPY --from=build /app/target/quarkus-app/lib/ /work/lib/
COPY --from=build /app/target/quarkus-app/app/ /work/app/
COPY --from=build /app/target/quarkus-app/quarkus/ /work/quarkus/
COPY --from=build /app/target/quarkus-app/quarkus-run.jar /work/quarkus-run.jar

EXPOSE 8382
USER 1001

ENTRYPOINT ["java", "-jar", "/work/quarkus-run.jar"]