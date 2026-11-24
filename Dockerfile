# Stage 1: Build SDK + webapp
FROM maven:3-eclipse-temurin-25 AS build
WORKDIR /build

# Build SDK libraries first
COPY Signing-Server/ Signing-Server/
RUN --mount=type=cache,target=/root/.m2 \
    mvn -f Signing-Server/pom.xml clean install -DskipTests

# Build the webapp
COPY pom.xml .
COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 \
    mvn clean package -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:25-jre-jammy AS final
WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends gosu && \
    rm -rf /var/lib/apt/lists/*

# CVE-2026-114938: turn off the Base64 intrinsic. Remove when the base image has the 2026-11-17 JDK fix.
ENV JAVA_TOOL_OPTIONS="-XX:-UseBASE64Intrinsics"

RUN adduser --disabled-password --gecos "" --home "/nonexistent" \
    --shell "/sbin/nologin" --no-create-home --uid 1042 appuser
RUN mkdir -p signed-documents signers-documents temp-documents config && \
    chown -R appuser:appuser /app

COPY --from=build --chown=appuser:appuser /build/target/itkdev-signing-webapp-*.jar app.jar
COPY --chmod=755 docker-entrypoint.sh /usr/local/bin/

EXPOSE 8088

ENTRYPOINT ["docker-entrypoint.sh"]
CMD ["java", "-jar", "app.jar", "--spring.config.location=file:/app/config/application.yaml"]
