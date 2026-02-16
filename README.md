# OS2Forms Signing Webapp

Standalone Spring Boot application for digital document signing in the
[OS2Forms](https://os2forms.os2.eu/) ecosystem. Replaces the previous
Drupal-based proxy module
([os2forms_dig_sig_server](https://github.com/MitID-Digital-Signature/os2forms_dig_sig_server/))
by combining the proxy logic with the
[NemLog-In Signing SDK](https://github.com/MitID-Digital-Signature/Signing-Server.git)
into a single service.

```
Previous:  OS2Forms → Drupal proxy → Java Signing-Server → NemLog-In
Now:       OS2Forms → os2forms-signing-webapp (this project) → NemLog-In
```

The API is backward-compatible with the
[OS2Forms digital_signature client module](https://github.com/OS2Forms/os2forms/tree/develop/modules/os2forms_digital_signature),
requiring no changes on the OS2Forms side.

## Requirements

- Docker and Docker Compose
- [Task](https://taskfile.dev/) (task runner)
- Git (to clone the NemLog-In SDK)
- An OCES3 certificate (`.p12`) registered with NemLog-In

**NOTE:** Java and Maven are not required locally. All build and run commands
execute inside Docker containers (`maven:3-eclipse-temurin-21`). A named Docker
volume (`os2forms-signing-m2`) is used to cache Maven dependencies between
builds.

## Installation

### 1. Clone the Repository

```bash
git clone <REPOSITORY_URL>
cd os2forms-signing-webapp
```

### 2. First-Time Setup

Run the setup task to clone the SDK, initialize configuration, and build
everything:

```bash
task setup
```

This performs three steps:

1. Clones the [NemLog-In Signing SDK](https://github.com/MitID-Digital-Signature/Signing-Server.git) into `Signing-Server/`
2. Copies `config/application.yaml.example` to `config/application.yaml`
3. Builds the SDK libraries and the webapp

### 3. Configure

Edit `config/application.yaml` with your NemLog-In credentials and OS2Forms
settings:

```bash
$EDITOR config/application.yaml
```

Place your OCES3 certificate at `config/certificate.p12`.

### 4. Run

```bash
task dev
```

The application starts on port **8088** by default.

## Configuration

Configuration is split into two namespaces in `config/application.yaml`:

### NemLog-In SDK (`nemlogin.signing.*`)

| Variable | Description |
|----------|-------------|
| `signing-client-url` | NemLog-In signing client URL |
| `validation-service-url` | NemLog-In validation service URL |
| `entity-id` | Your entity ID registered with NemLog-In |
| `keystore-path` | Path to your OCES3 certificate (`.p12`) |
| `key-pair-alias` | Key pair alias in the keystore |
| `keystore-password` | Keystore password |
| `private-key-password` | Private key password |

**NemLog-In environments:**

| Environment | Signing Client URL | Validation URL |
|-------------|-------------------|----------------|
| Test | `https://underskrift.test-nemlog-in.dk/` | `https://validering.test-nemlog-in.dk/api/validate` |
| Production | `https://underskrift.nemlog-in.dk/` | `https://validering.nemlog-in.dk/api/validate` |

### Application (`os2forms.*`)

| Variable | Description | Default |
|----------|-------------|---------|
| `hash-salt` | Salt for SHA-1 hash validation of forward URLs. Must match the OS2Forms digital_signature module. | *(required)* |
| `allowed-domains` | List of allowed domains for PDF URLs and forward URLs. | `[]` |
| `signed-documents-dir` | Directory for storing signed PDFs. | `./signed-documents/` |
| `source-documents-dir` | Directory for storing fetched source PDFs. | `./signers-documents/` |
| `debug` | Enable debug logging. | `false` |

**NOTE:** If `allowed-domains` is empty, all domains are allowed. This is not
recommended for production.

## Available Tasks

Run `task --list` to see all available tasks:

| Task | Description |
|------|-------------|
| `task setup` | Clone SDK, init config, and build everything |
| `task clone` | Clone the Signing-Server SDK repo |
| `task build:sdk` | Build SDK libraries (install to local Maven repo) |
| `task build` | Build the webapp |
| `task build:all` | Build SDK + webapp |
| `task dev` | Run in development mode (`mvn spring-boot:run`) |
| `task run:jar` | Run the built JAR directly |
| `task clean` | Maven clean |
| `task config:init` | Copy example config to `application.yaml` |

## API

All actions are served from a single endpoint `GET /sign` with an `action`
query parameter. The signing result callback is handled at `POST /signing-result`.

### Endpoints

#### `GET /sign?action=getcid`

Returns a new correlation ID.

**Response:**

```json
{"cid": "7f03374d-5488-49cc-b952-0abfa297e3df"}
```

#### `GET /sign?action=sign&uri=<BASE64>&forward_url=<BASE64>&hash=<SHA1>`

Initiates the signing flow. Fetches the PDF, generates a signing payload, and
renders the NemLog-In signing iframe.

| Parameter | Type | Description |
|-----------|------|-------------|
| `uri` | string | Base64-encoded URL to the PDF file |
| `forward_url` | string | Base64-encoded redirect URL for after signing |
| `hash` | string | SHA-1 hash of `<SALT>` + decoded `forward_url` |

#### `GET /sign?action=result&file=<FILENAME>`

Redirects to the `forward_url` with `?file=<FILENAME>&action=result`.

#### `GET /sign?action=cancel&file=<FILENAME>`

Redirects to the `forward_url` with `?file=<FILENAME>&action=cancel`.

#### `GET /sign?action=download&file=<FILENAME>&leave=<0|1>`

Downloads the signed PDF as `application/pdf`.

| Parameter | Type | Description |
|-----------|------|-------------|
| `file` | string | Filename (must match `^[a-z0-9]{32}\.pdf$`) |
| `leave` | string | `1` to keep file on server, `0` to delete after download |

### Error Response Format

All errors are returned as JSON:

```json
{"error": true, "message": "Description of the error", "code": 0}
```

## Signing Flow

```
1. OS2Forms → GET /sign?action=getcid
   ← {"cid": "uuid"}

2. OS2Forms → GET /sign?action=sign&uri=<b64>&forward_url=<b64>&hash=<sha1>
   App: decode URI → validate hash → validate domain → fetch PDF
   App: generate signing payload via NemLog-In SDK
   ← Render signing page (iframe loads NemLog-In signing client)

3. User authenticates with MitID in iframe and signs document
   iframe → postMessage → JavaScript → form POST to /signing-result

4. App: decode signed document → save as {hash}-signed.pdf
   App: read forward_url from session → validate domain
   ← HTTP 302 redirect to {forward_url}?file={hash}.pdf&action=result

5. OS2Forms → GET /sign?action=download&file={hash}.pdf&leave=0
   ← Binary PDF, file deleted after sending
```

## Deployment

### Docker

Build and run with Docker Compose:

```bash
docker compose build
docker compose up -d
```

The service is accessible through the nginx reverse proxy on port **8080**.

**Required volumes:**

| Host Path | Container Path | Description |
|-----------|---------------|-------------|
| `config/application.yaml` | `/app/config/application.yaml` | Application configuration |
| `config/certificate.p12` | `/app/config/certificate.p12` | OCES3 certificate |

### Production (Traefik)

For production with Traefik and HTTPS, set the required environment variables
and use the server compose file:

```bash
export COMPOSE_PROJECT_NAME=os2forms-signing
export COMPOSE_SERVER_DOMAIN=signing.example.dk

docker compose -f docker-compose.server.yml build
docker compose -f docker-compose.server.yml up -d
```

### Architecture

The Docker image uses a three-stage build:

1. **build-sdk** — Builds the NemLog-In SDK libraries (`eclipse-temurin:21-jdk-jammy`)
2. **build-app** — Builds the webapp against SDK JARs (`eclipse-temurin:21-jdk-jammy`)
3. **final** — Runtime with minimal JRE image (`eclipse-temurin:21-jre-jammy`), runs as non-root user on port 8088

Nginx sits in front as a reverse proxy on port 8080.

## Verification

After starting the application, verify it works:

1. **Test getcid:**
   ```bash
   curl http://localhost:8088/sign?action=getcid
   ```
   Expected: `{"cid":"<UUID>"}`

2. **Test error handling:**
   ```bash
   curl "http://localhost:8088/sign?action=sign&uri=dGVzdA==&forward_url=dGVzdA==&hash=invalid"
   ```
   Expected: `{"error":true,"message":"Incorrect hash value","code":0}`

## Related Repositories

- [NemLog-In Signing SDK](https://github.com/MitID-Digital-Signature/Signing-Server.git) — SignSDK v2.0.2 (build dependency)
- [os2forms_dig_sig_server](https://github.com/MitID-Digital-Signature/os2forms_dig_sig_server/) — The Drupal proxy module this project replaces
- [OS2Forms digital_signature](https://github.com/OS2Forms/os2forms/tree/develop/modules/os2forms_digital_signature) — The OS2Forms client module (unchanged, our API is backward-compatible)

## License

TBD
