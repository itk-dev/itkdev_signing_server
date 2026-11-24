# Repository Guidelines

## Project Overview

ITKDev Signing Server is a Spring Boot application. It is a thin adapter between a calling application and NemLog-In document signing.

The server gets a PDF reference through `GET /sign`. It shows the NemLog-In signing iframe. Then it redirects the browser to the caller.

```
Calling application → ITKDev Signing Server → NemLog-In
```

There is no database. State is two `HttpSession` attributes and two document directories.

## Architecture & Data Flow

Three controllers delegate to one service. All code is in `src/main/java/dk/itkdev/signing/`.

| Class | Role |
|---|---|
| `Application.java` | Entry point. Registers the BouncyCastle provider in a static block. |
| `config/ItkdevProperties.java` | Binds `itkdev.*`. |
| `config/SignatureValidationConfiguration.java` | Validation beans. `setIgnoreSsl(true)` is hardcoded. |
| `controller/SigningController.java` | `GET /` (API documentation) and `GET /sign` (switch on `action`). |
| `controller/SigningResultController.java` | `POST /signing-result` (iframe callback, switch on `type`). |
| `controller/TestController.java` | `/test` flow. Each handler returns 404 unless `itkdev.test-page-enabled` is `true`. |
| `service/SigningService.java` | Fetch, hash, payload, save, validate, cleanup. |

### Signing flow

1. `GET /sign?action=sign&uri={b64}&forward_url={b64}&hash={sha1}` starts the flow.
2. `handleSign` decodes `forward_url`. Then it calls `validateHash` and `isValidDomain`.
3. `fetchAndPrepareDocument` downloads the PDF to `sourceDocumentsDir/<32hex>.pdf`.
4. The session gets `forward_url` (base64) and `filename`.
5. `generateSigningPayload` builds the payload. The `sign` view shows the iframe.
6. `sign.html` posts the iframe result to `POST /signing-result`. The fields are `type`, `result`, `name`, `format`, `correlationId`.
7. `SigningResultController` saves `signedDocumentsDir/<32hex>-signed.pdf`. Then it sends a 302 to `forward_url` with `file=<name>&action=result|cancel|error`.
8. The caller gets the file with `GET /sign?action=download&file=<name>`. This call deletes the source file and the signed file.

Other actions: `getcid` returns `{"cid": "<uuid>"}`. `result` and `cancel` redirect to the session `forward_url`.

### Known gaps

- Only `download` calls `cleanupDocuments`. Cancelled, failed and not-downloaded flows keep their files. There is no scheduled cleanup.
- No Java code uses `temp-documents/`. Only the Docker files refer to it.
- The `/test` flow skips the hash check. `/signing-result` continues to call `isValidDomain`. Thus the server host must be in `itkdev.allowed-domains`, or the list must be empty.
- An empty `itkdev.allowed-domains` list permits all domains (with a `LOG.warn`).

## Key Directories

| Path | Purpose |
|---|---|
| `src/main/java/dk/itkdev/signing/` | All application code (7 classes). |
| `src/main/resources/templates/` | Thymeleaf: `index.html`, `sign.html`, `test.html`, `test-result.html`. |
| `config/` | `application.yaml.example` is in git. `application.yaml` and `certificate.p12` are gitignored secrets. |
| `Signing-Server/` | NemLog-In SDK v2.0.11. Gitignored. Do not change it. |
| `.docker/` | nginx configuration and templates. |
| `signed-documents/`, `signers-documents/` | Runtime PDF storage. |

## Development Commands

All commands use [Task](https://taskfile.dev/). Maven runs in Docker (`maven:3-eclipse-temurin-25`). No local Java is necessary.

```bash
task setup               # First time: sdk:download + config:init + build:all
task build               # mvn clean package -DskipTests (depends on build:sdk)
task build:sdk:force     # Build the SDK again (ignores the .sdk-built marker)
task dev                 # mvn spring-boot:run on port 8088
task run:jar             # Run target/itkdev-signing-webapp-*.jar
task clean               # mvn clean
task docker:build        # Build ghcr.io/itk-dev/signing-server:latest (--pull --no-cache)
task prod:build TAG=x.y.z  # Build, tag :TAG and :latest, push to GHCR
```

- `task build:sdk` does nothing if `.sdk-built` exists.
- There is no `test`, `lint` or `format` task.
- `task docker:push VERSION=…` and `task config:upload-size` do not exist. `README.md`, `CLAUDE.md` and `.env` refer to them incorrectly.
- `Signing-Server/` must exist before an image build, because the `Dockerfile` copies it.

Compose:

```bash
docker compose up -d                                 # Local: builds from source
docker compose -f docker-compose.server.yml up -d    # Production: pulls the GHCR image
```

The production file needs `COMPOSE_PROJECT_NAME`, `COMPOSE_SERVER_DOMAIN` and the external `frontend` network. It has no document volumes and no upload-size overrides for the application.

## Code Conventions & Common Patterns

- **Dependency injection**: constructor injection only, with `private final` fields. Do not use `@Autowired` on fields. SDK string beans use `@Qualifier("signingClientUrl")`, `@Qualifier("entityID")`, `@Qualifier("validationServiceUrl")`.
- **Routing**: one public handler with a `switch` expression that calls private `handleX` methods (`handleGetCid`, `handleSign`, `handleResult`, `handleDownload`).
- **Error handling**: no custom exceptions and no `@ControllerAdvice`. Throw `IllegalArgumentException` for bad input and `SecurityException` for hash, domain, session or file failures. `SigningController` and `SigningResultController` catch `Exception` and return `errorResponse()`.
- **Error shape**: HTTP 200 with `{"error": true, "message": "...", "code": 0}`.
- **Logging**: `private static final Logger LOG = LoggerFactory.getLogger(X.class)` with `{}` placeholders (SLF4J on Log4j2). Use `signingService.debug(msg, args…)` for flow traces. It logs at INFO when `itkdev.debug` is `true`.
- **Session state**: keys `"forward_url"` (base64) and `"filename"`. Each controller declares the constants `SESSION_FORWARD_URL` and `SESSION_FILENAME`. Clear the session before each redirect.
- **Security checks** (all in `SigningService`):
  - `validateHash`: SHA-1 of `salt + forward_url`, compared with `MessageDigest.isEqual`.
  - `isValidDomain`: exact host match in `itkdev.allowed-domains`. Call it before each fetch and each redirect.
  - `getSignedDocumentPath`: file name must match `^[a-z0-9]{32}\.pdf$`.
  - Source URL must match `^https?://.*\.pdf$`.
- **Style**: Java 21 language level. 4-space indent in Java, 2-space indent in templates. No Lombok and no records. `var` and switch expressions are in use. Public service methods have Javadoc.
- **Templates**: inline `<style>`, no shared layout, no static assets. `sign.html` serves the production flow and the test flow.
- **Async**: none. All handlers are synchronous. `RestTemplate` is created in the `SigningService` constructor.

## Important Files

- `pom.xml`: Spring Boot parent `4.1.3`, SDK `dk.gov.nemlogin:*` at `2.0.11`.
- `Taskfile.yml`: the single source of build and run commands.
- `src/main/resources/application.yaml`: bundled defaults with placeholders.
- `config/application.yaml.example`: template for the mounted configuration. The container uses `--spring.config.location=file:/app/config/application.yaml`, so the mounted file must be complete.
- `src/main/resources/templates/index.html`: built-in API reference. **Update it for each API change.** Update the API table in `README.md` also.
- `Dockerfile`, `docker-entrypoint.sh`: the container starts as root, remaps `appuser` to `PUID`/`PGID` (default `1042`), then drops privileges with `gosu`.
- `CHANGELOG.md`: Keep a Changelog 1.1.0. Add a plain bullet below `## [Unreleased]` for each change.

Configuration namespaces:

- `nemlogin.signing.*` (SDK): `signing-client-url`, `validation-service-url`, `entity-id`, `keystore-path`, `key-pair-alias`, `keystore-password`, `private-key-password`.
- `itkdev.*`: `hash-salt`, `allowed-domains`, `signed-documents-dir`, `source-documents-dir`, `debug`, `test-page-enabled`.

## Runtime/Tooling Preferences

- Java 21 language level, built and run on a Java 25 JDK/JRE in Docker.
- Maven only, through `task`. The Maven cache is the Docker volume `itkdev-signing-m2`, not the host `~/.m2`.
- Port `8088` for the application. nginx listens on `8080` in the two compose files.
- `ENV JAVA_TOOL_OPTIONS="-XX:-UseBASE64Intrinsics"` in the `Dockerfile` is a CVE workaround. Keep it until the base image has the fix.
- Git: branches `main`, `develop`, `feature/<name>`, `release/<x.y.z>`. Tags are bare semver (`0.0.7`, no `v` prefix). Commit messages are plain sentences.
- The `pom.xml` version (`1.0.0-SNAPSHOT`) does not follow releases. The release version is in the git tag, the image tag and `CHANGELOG.md`.
- Do not commit `config/application.yaml`, `*.p12`, `*.cer` or `*.pem`.

## Testing & QA

There is no test suite, no CI, no linter and no git hook. `src/test/` does not exist, and each Maven build uses `-DskipTests`. `task build` is the only compile check.

Do these manual checks after a change (`task dev` must be in operation):

```bash
curl http://localhost:8088/sign?action=getcid
# {"cid":"<uuid>"}

curl "http://localhost:8088/sign?action=sign&uri=dGVzdA==&forward_url=dGVzdA==&hash=invalid"
# {"error":true,"message":"Incorrect hash value","code":0}
```

- Open `http://localhost:8088/` and make sure that the API reference shows.
- For the full flow, set `itkdev.test-page-enabled: true` in `config/application.yaml`. Then open `http://localhost:8088/test`, upload a PDF, sign it and validate it.
- Do not enable the test page in production.
