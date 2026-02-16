# Stage 1: Build SDK libraries
FROM eclipse-temurin:21-jdk-jammy AS build-sdk
WORKDIR /build
COPY Signing-Server/ .
RUN --mount=type=cache,target=/root/.m2 mvn clean install -DskipTests

# Stage 2: Build os2forms webapp
FROM eclipse-temurin:21-jdk-jammy AS build-app
WORKDIR /build
COPY --from=build-sdk /root/.m2 /root/.m2
COPY pom.xml .
COPY src/ src/
RUN mvn clean package -DskipTests

# Stage 3: Runtime
FROM eclipse-temurin:21-jre-jammy AS final
WORKDIR /app

ARG UID=10001
RUN adduser --disabled-password --gecos "" --home "/nonexistent" \
    --shell "/sbin/nologin" --no-create-home --uid "${UID}" appuser
RUN mkdir -p signed-documents signers-documents temp-documents config && \
    chown -R appuser:appuser /app

COPY --from=build-app --chown=appuser:appuser /build/target/os2forms-signing-webapp-*.jar app.jar

USER appuser
EXPOSE 8088

ENTRYPOINT ["java", "-jar", "app.jar", "--spring.config.location=file:/app/config/application.yaml"]
