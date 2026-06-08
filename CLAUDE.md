# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**ITKDev Signing Server** — a standalone Spring Boot application that acts as a stateless adapter for NemLog-In digital document signing. Receives PDF references over a simple HTTP API, presents the NemLog-In signing iframe, and redirects back to the caller after signing.

```
Calling application → ITKDev Signing Server (this project) → NemLog-In
```

The landing page (`/`) serves built-in API documentation with endpoint details, parameter descriptions, and the signing flow.

## Build & Development Commands

All commands use [Task](https://taskfile.dev/). Java/Maven run inside Docker containers (`maven:3-eclipse-temurin-21`) — no local Java installation needed. Maven dependencies are cached in a named Docker volume (`itkdev-signing-m2`).

The SDK (`Signing-Server/`) must be downloaded and built first.

```bash
task setup            # Download SDK + init config + build everything (first-time)
task sdk:download     # Download and extract the NemLog-In Signing SDK
task build:sdk        # Build SDK libraries (mvn clean install in Signing-Server/)
task build:sdk:force  # Rebuild SDK even if already built (ignores the .sdk-built marker)
task build            # Build our webapp (mvn clean package); depends on build:sdk
task build:all        # Force-rebuild SDK + webapp
task dev              # Run in dev mode (mvn spring-boot:run, port 8088 exposed)
task run:jar          # Run the built jar directly
task clean            # Maven clean
task config:init      # Copy application.yaml.example → application.yaml
task docker:build     # Build the Docker image (--no-cache)
task docker:push VERSION=x.y.z  # Build, tag, and push image to GHCR
```

**`.sdk-built` marker**: `build:sdk` is a no-op once the `.sdk-built` file exists (a Taskfile `status:` guard). After changing anything under `Signing-Server/`, run `task build:sdk:force` (or delete `.sdk-built`) — a plain `task build` will *not* pick up SDK changes.

**Tests**: there is no test suite. Every Maven invocation passes `-DskipTests`, and there is no `src/test/`. Do not assume `task test` exists.

For production deployment, use the server compose file (pulls the pre-built GHCR image, Traefik + HTTPS):
```bash
docker compose -f docker-compose.server.yml up -d
```
`docker compose up -d` (the default `docker-compose.yml`) is the local-dev variant that builds from source.

Verify with: `curl http://localhost:8088/sign?action=getcid` → `{"cid":"uuid"}`

## Architecture

### SDK Dependency Model

The app depends on the [NemLog-In Signing SDK](https://cms.nemlog-in.dk/media/3qdj0erk/nemlogin-signsdk-java-2-0-2.zip) v2.0.2 as library JARs installed to the local Maven repo. The SDK directory is gitignored. Key SDK libraries:

- `nemlogin-signing-spring-boot` — auto-configures `SigningPayloadService`, `SignatureKeys`, `signingClientUrl`, `entityID`, `validationServiceUrl`
- `nemlogin-signing-pades` — PAdES PDF signing format
- `nemlogin-signing-jws` — JWS signing (required)
- `nemlogin-signing-pdf-generator` / `nemlogin-signing-pdf-validator` — PDF generation & pre-sign validation
- `nemlogin-signing-validation` — signature validation via NemLog-In API

We do **not** modify SDK code. Our app only implements: the proxy API, document fetching, file management, signing page template, and test tooling.

### Source Layout

```
src/main/java/dk/itkdev/signing/
├── Application.java                    # @SpringBootApplication + BouncyCastle provider init
├── config/
│   ├── ItkdevProperties.java           # Bound to itkdev.* config namespace
│   └── SignatureValidationConfiguration.java  # Validation service + context builder beans
├── controller/
│   ├── SigningController.java          # GET /sign — main API (action-based routing) + GET / (index)
│   ├── SigningResultController.java    # POST /signing-result — iframe callback
│   └── TestController.java            # /test — upload, sign, validate flow (behind feature flag)
└── service/
    └── SigningService.java             # Core logic: fetch, hash, payload, save, validate

src/main/resources/templates/
├── index.html          # Built-in API reference (landing page)
├── sign.html           # NemLog-In signing iframe container (reused by both production and test flows)
├── test.html           # Test page: PDF upload form
└── test-result.html    # Test page: result display with validation report
```

### Important: Built-in API Documentation

The landing page (`/`) at `src/main/resources/templates/index.html` serves as the built-in API reference. When any API changes are made, **always update `index.html`** to reflect the changes.

### API Contract

Single endpoint, action-based routing:

| Action | Request | Response |
|--------|---------|----------|
| `getcid` | `GET /sign?action=getcid` | `{"cid": "uuid"}` |
| `sign` | `GET /sign?action=sign&uri={b64}&forward_url={b64}&hash={sha1}` | Signing page with NemLog-In iframe |
| `result` | `GET /sign?action=result&file={name}` | 302 redirect to forward_url |
| `cancel` | `GET /sign?action=cancel&file={name}` | 302 redirect to forward_url |
| `download` | `GET /sign?action=download&file={name}` | Binary PDF stream (one-time; file deleted after download) |

Error responses: `{"error": true, "message": "...", "code": 0}`

### Test Page (`/test`)

Self-contained signing test behind `itkdev.test-page-enabled` (default `false`). Bypasses hash validation and domain whitelisting. Flow:

```
GET /test              → Upload form
POST /test/upload      → Save PDF, render sign.html (reused)
POST /signing-result   → Existing controller saves signed doc, redirects to /test/result
GET /test/result       → Result page with download + validate buttons
POST /test/validate    → Calls NemLog-In validation API, renders report
```

### Configuration Namespaces

- `nemlogin.signing.*` — SDK config (entity ID, keystore, signing client URL, validation service URL)
- `itkdev.*` — App config (hashSalt, allowedDomains, document directories, debug, testPageEnabled)

### Security Model

- **Hash validation**: SHA-1(salt + forward_url) with timing-safe `MessageDigest.isEqual()`
- **Domain whitelisting**: PDF URLs and forward URLs checked against `itkdev.allowed-domains`
- **Filename validation**: Download filenames must match `^[a-z0-9]{32}\.pdf$`
- **Session-based state**: forward_url stored in HttpSession (not cookies)

## Key SDK Reference Files

When working with signing internals, refer to these upstream files in `Signing-Server/`:

- **Signing payload generation**: `examples/nemlogin-signing-webapp/.../DocumentSigningService.java`
- **SDK auto-configuration**: `library/nemlogin-signing-spring-boot/.../NemLogInAutoConfiguration.java`
- **Signing page template**: `examples/nemlogin-signing-webapp/.../templates/sign.html`
- **Validation service**: `library/nemlogin-signing-validation/.../SignatureValidationService.java`
- **Validation models**: `library/nemlogin-signing-validation/.../model/` (ValidationReport, ValidationResult, ValidationSignature, ValidationCertificate)

## Docker

Two-stage Dockerfile: stage 1 builds the SDK then the webapp (`maven:3-eclipse-temurin-21`); stage 2 is the runtime (`eclipse-temurin:21-jre-jammy`) on port 8088. Config is mounted at `/app/config/application.yaml`.

**Runtime user**: the container *starts as root* and runs `docker-entrypoint.sh`, which remaps the `appuser` UID/GID to the `PUID`/`PGID` env vars (default `1042`), `chown`s the document directories, then drops to `appuser` via `gosu` before launching the JVM. This is how host-volume permissions are kept correct — it is not a plain non-root container.

**Document directories** (all mounted as volumes): `signed-documents/`, `signers-documents/` (fetched/uploaded source PDFs), and `temp-documents/`.

**Two compose files:**
- `docker-compose.yml` — local dev. Builds the image from source; nginx is configured via `.docker/templates`.
- `docker-compose.server.yml` — production. Pulls `ghcr.io/itk-dev/signing-server:${TAG}`, adds Traefik labels with Let's Encrypt TLS.

Both put an nginx reverse proxy (port 8080) in front of the signing server. Upload-size limits are tunable via the `MAX_UPLOAD_SIZE` / `MAX_UPLOAD_SIZE_BYTES` env vars (see `.env`), which override the Spring/Tomcat defaults.

## Tech Stack

- Java 17 language level (`<java.version>17</java.version>`), built and run on a Java 21 JDK/JRE in Docker
- Spring Boot 3.2.1
- Thymeleaf templates
- Maven build (inside Docker — no local Java required)
- NemLog-In SignSDK v2.0.2
- BouncyCastle security provider (registered in `Application.java`)
- Docker with nginx reverse proxy
