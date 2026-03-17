# ── Stage 1: Build ────────────────────────────────────────────────────────────
#
# Uses GraalVM CE 25 for the native compilation.
# NOTE: GraalVM CE for Java 25 is cutting-edge — if you hit build issues,
# pin to ghcr.io/graalvm/graalvm-ce:java21 for a stable fallback.
#
FROM ghcr.io/graalvm/graalvm-ce:java25 AS build

# Install Maven
RUN microdnf install -y maven && microdnf clean all

WORKDIR /app

# Copy dependency descriptors first for better layer caching.
# Maven only re-downloads dependencies when pom.xml changes.
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Copy the rest of the source and build the native executable
COPY src ./src
RUN mvn package -Pnative -DskipTests -q

# ── Stage 2: Runtime ───────────────────────────────────────────────────────────
#
# Distroless contains only the bare minimum to run a native Linux binary.
# No shell, no package manager — smallest attack surface possible.
#
FROM quay.io/quarkus/quarkus-distroless-image:2.0

WORKDIR /app

# Copy the native executable produced by Quarkus
# The file is named after your artifactId — adjust if yours differs
COPY --from=build /app/target/*-runner /app/application

EXPOSE 8080

CMD ["/app/application", "-Dquarkus.http.host=0.0.0.0"]