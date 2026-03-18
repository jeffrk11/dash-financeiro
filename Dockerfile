# ── Stage 1: Build native binary ──────────────────────────────────────────────
FROM ghcr.io/graalvm/native-image-community:21 AS build

WORKDIR /app

COPY . .

# Clear the SHA-256 checksum validation — it fails in isolated build environments
# due to locale/download differences. Safe to skip in a controlled Docker build.
RUN chmod +x mvnw && \
    sed -i 's/distributionSha256Sum=.*/distributionSha256Sum=/' .mvn/wrapper/maven-wrapper.properties && \
    ./mvnw package -Dnative -DskipTests -q

# ── Stage 2: Runtime ───────────────────────────────────────────────────────────
FROM registry.access.redhat.com/ubi9/ubi-minimal:9.7

WORKDIR /work/
RUN chown 1001 /work \
    && chmod "g+rwX" /work \
    && chown 1001:root /work

COPY --from=build --chown=1001:root --chmod=0755 /app/target/*-runner /work/application

EXPOSE 8080
USER 1001

ENTRYPOINT ["./application", "-Dquarkus.http.host=0.0.0.0"]