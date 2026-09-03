# Spring Boot Auth & Data API

A small application consisting of two Spring Boot services and PostgreSQL.

Service A handles registration and JWT authentication, forwards text-processing requests to Service B, and stores successful processing results in PostgreSQL.

## Architecture

- **auth-api** — registration, login, JWT validation, and request processing.
- **data-api** — converts text to uppercase and validates an internal shared token.
- **PostgreSQL** — stores users and processing logs.

```text
Client ──Bearer JWT──> auth-api ──X-Internal-Token──> data-api
                          │
                          ▼
                      PostgreSQL
```

Both applications and PostgreSQL share a Docker Compose network.

Inside that network:

- Service A reaches Service B at `http://data-api:8081`.
- Service A reaches PostgreSQL at `postgres:5432`.

### Authentication

Two different tokens are used:

- **JWT** authenticates clients calling `auth-api`.
- **X-Internal-Token** authenticates internal requests to `data-api`.

The internal token proves that the caller knows the shared secret. It does not independently verify that the caller is Service A.

## Repository Structure

```text
.
├── auth-api/
│   ├── src/main/
│   ├── src/test/
│   ├── Dockerfile
│   ├── .dockerignore
│   ├── pom.xml
│   ├── mvnw
│   └── mvnw.cmd
├── data-api/
│   ├── src/main/
│   ├── src/test/
│   ├── Dockerfile
│   ├── .dockerignore
│   ├── pom.xml
│   ├── mvnw
│   └── mvnw.cmd
├── postgres/
│   └── init/
│       └── 001-create-tables.sql
├── docker-compose.yml
├── .env.example
└── README.md
```

## Requirements

### Docker deployment

- Docker Desktop with Linux containers enabled
- Docker Compose

A local Java or Maven installation is not required for Docker deployment. The Dockerfiles build the applications using Maven Wrapper.

### Local development and tests

- JDK 25
- Docker Desktop for PostgreSQL and Testcontainers
- Maven Wrapper, included in both application directories

The commands below use PowerShell and should be executed from the repository root unless stated otherwise.

## Environment Configuration

Copy the example configuration:

```powershell
Copy-Item .env.example .env
```

Edit `.env` and configure:

```dotenv
POSTGRES_DB=appdb
POSTGRES_USER=appuser
POSTGRES_PASSWORD=replace-with-a-database-password
POSTGRES_URL=jdbc:postgresql://postgres:5432/appdb
JWT_SECRET=
INTERNAL_TOKEN=
```

Replace the sample database password and fill in both secrets before starting the application.

| Variable | Purpose |
|---|---|
| `POSTGRES_DB` | Database created during the initial PostgreSQL setup |
| `POSTGRES_URL` | JDBC URL used by `auth-api` |
| `POSTGRES_USER` | PostgreSQL username |
| `POSTGRES_PASSWORD` | PostgreSQL password |
| `JWT_SECRET` | JWT signing and verification secret; at least 32 UTF-8 bytes |
| `INTERNAL_TOKEN` | Shared secret used by both services |
| `DATA_API_URL` | Service B address; supplied by Compose as `http://data-api:8081` |

### Generate secrets

Run the following to generate a cryptographically random value and copy it to the clipboard:

```powershell
$secretBytes = New-Object byte[] 32
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($secretBytes)
$rng.Dispose()

[Convert]::ToBase64String($secretBytes) | Set-Clipboard
```

Paste the generated value into `JWT_SECRET`. Run the commands again to generate a separate value for `INTERNAL_TOKEN`.

The JWT implementation uses the configured string as UTF-8 key material; it does not Base64-decode it.

Do not commit `.env`, share secrets, or log passwords and tokens.

> Compose reads `.env` for variable substitution. Environment variables already present in the terminal can override values from `.env`. Use a fresh terminal when switching from local development to Docker deployment.

## Run with Docker Compose

Make sure locally running applications are not occupying ports `8080` and `8081`.

Validate the configuration without printing resolved secrets:

```powershell
docker compose config --quiet
```

Build and start all services:

```powershell
docker compose up -d --build
```

Check container status:

```powershell
docker compose ps
```

Follow application logs:

```powershell
docker compose logs -f auth-api data-api
```

Wait for:

```text
Started AuthApiApplication
Started DataApiApplication
```

Pressing `Ctrl+C` stops following logs; it does not stop the containers.

### Published ports

| Service | Host address |
|---|---|
| auth-api | `http://localhost:8080` |
| data-api | `http://localhost:8081` |
| PostgreSQL | `localhost:5433` |

PostgreSQL still listens on port `5432` inside the Docker network.

### Stop services

```powershell
docker compose down
```

The database volume is preserved.

**Warning:** `docker compose down -v` also deletes the database volume and its data.

### Apply environment changes

After editing `.env`, recreate the affected containers:

```powershell
docker compose up -d --force-recreate auth-api data-api
```

A simple `docker compose restart` does not apply changed environment values.

Changing PostgreSQL initialization variables does not update users, passwords, or databases already stored in an existing volume.

## API Usage

Run the following commands in the same PowerShell session so that variables are preserved.

### 1. Register a user

A unique email avoids conflicts with previous test runs.

```powershell
$email = "demo-$([guid]::NewGuid().ToString('N'))@example.com"

$authBody = @{
    email = $email
    password = "test-password"
} | ConvertTo-Json

$response = Invoke-WebRequest `
    -Uri "http://localhost:8080/api/auth/register" `
    -Method Post `
    -ContentType "application/json" `
    -Body $authBody

$response.StatusCode
```

Expected result:

```text
201
```

Registration returns an empty response body.

Passwords are stored as BCrypt hashes. Registration requires a password of at least 8 characters and no more than 72 UTF-8 bytes.

### 2. Log in

```powershell
$login = Invoke-RestMethod `
    -Uri "http://localhost:8080/api/auth/login" `
    -Method Post `
    -ContentType "application/json" `
    -Body $authBody

$token = $login.token

"Token received: $(-not [string]::IsNullOrWhiteSpace($token))"
```

Expected HTTP status: `200`.

Expected console output: 
```Token received: True.```

Response format:

```json
{
  "token": "<JWT>"
}
```

JWTs expire after one hour.

### 3. Process text

```powershell
$processBody = @{
    text = "hello"
} | ConvertTo-Json

Invoke-RestMethod `
    -Uri "http://localhost:8080/api/process" `
    -Method Post `
    -Headers @{ Authorization = "Bearer $token" } `
    -ContentType "application/json" `
    -Body $processBody
```

Expected HTTP status: `200`.

Response:

```json
{
  "result": "HELLO"
}
```

Service A:

1. Validates the JWT.
2. Extracts the user ID.
3. Calls Service B with `X-Internal-Token`.
4. Saves the input and output in PostgreSQL.
5. Returns the result.

### 4. Verify the processing log

If you configured a different database name or username, adjust this command:

```powershell
docker compose exec postgres psql -U appuser -d appdb -c "SELECT u.email, p.input_text, p.output_text, p.created_at FROM processing_log p JOIN users u ON u.id = p.user_id ORDER BY p.created_at DESC LIMIT 5;"
```

The results should include the registered user and `hello` → `HELLO`.

### 5. Verify access restrictions

This helper prints the HTTP status for successful and unsuccessful requests:

```powershell
function Test-PostStatus {
    param(
        [string]$Url,
        [string]$Body,
        [hashtable]$Headers = @{}
    )

    try {
        $response = Invoke-WebRequest `
            -Uri $Url `
            -Method Post `
            -Headers $Headers `
            -ContentType "application/json" `
            -Body $Body

        [int]$response.StatusCode
    } catch {
        if ($null -ne $_.Exception.Response) {
            [int]$_.Exception.Response.StatusCode
        } else {
            throw
        }
    }
}
```

Service A without a JWT — expected `401`:

```powershell
Test-PostStatus `
    -Url "http://localhost:8080/api/process" `
    -Body $processBody
```

Service B without an internal token — expected `403`:

```powershell
Test-PostStatus `
    -Url "http://localhost:8081/api/transform" `
    -Body $processBody
```

Service B with an incorrect internal token — expected `403`:

```powershell
Test-PostStatus `
    -Url "http://localhost:8081/api/transform" `
    -Headers @{ "X-Internal-Token" = "deliberately-wrong-token" } `
    -Body $processBody
```

### Response status summary

| Scenario | Status |
|---|---|
| Successful registration | `201` |
| Duplicate email | `409` |
| Successful login | `200` |
| Incorrect login credentials | `401` |
| Missing, invalid, or expired JWT on `/api/process` | `401` |
| Invalid request body with valid authentication | `400` |
| Successful processing | `200` |
| Service B request failure | `502` |
| Missing or invalid internal token on a valid Service B request | `403` |

## Automated Tests

Docker Desktop must be running. Compose services are not required for the isolated integration tests.

Run the main auth integration suite:

```powershell
.\auth-api\mvnw.cmd -f auth-api/pom.xml "-Dtest=AuthFlowTest" test
```

Run the data controller suite:

```powershell
.\data-api\mvnw.cmd -f data-api/pom.xml "-Dtest=TransformControllerTest" test
```

Run all tests and build both applications:

```powershell
.\auth-api\mvnw.cmd -f auth-api/pom.xml clean verify
.\data-api\mvnw.cmd -f data-api/pom.xml clean verify
```

### auth-api coverage

`AuthFlowTest` uses:

- A dedicated PostgreSQL container managed by Testcontainers
- A local HTTP stub instead of the real Service B
- The real Spring Security configuration, JWT handling, services, and repositories

It covers:

- Registration → login → processing → PostgreSQL persistence,
  with Service B replaced by an HTTP stub
- Password hashing and verification using the configured BCrypt encoder
- Duplicate registration
- Incorrect login password
- Missing JWT on /api/process
- JWTs with invalid signatures
- Expired JWTs
- Whitespace-only text with a valid JWT
- HTTP 500 from Service B mapped to HTTP 502
- No Service B calls for the tested authentication and validation failures
- No processing-log entries for the tested processing failures

The test database is isolated from the Compose database. Test secrets are supplied by the test class; local `.env` values are not required.

### data-api coverage

Controller tests cover:

- Valid internal token and successful transformation
- Missing internal token
- Incorrect internal token
- Blank text

These tests do not replace the manual Compose smoke test, which also checks container packaging and networking.

## Local Development

PostgreSQL can run in Docker while both applications run locally.

First, start PostgreSQL:

```powershell
docker compose up -d postgres
```

If the application containers are already running, stop them to release their ports:

```powershell
docker compose stop auth-api data-api
```

### Terminal 1: auth-api

Set the actual database credentials and a valid JWT secret:

```powershell
$env:POSTGRES_URL = "jdbc:postgresql://localhost:5433/appdb"
$env:POSTGRES_USER = "appuser"
$env:POSTGRES_PASSWORD = "<your-database-password>"
$env:JWT_SECRET = "<your-generated-jwt-secret>"
$env:INTERNAL_TOKEN = "<your-internal-secret>"
$env:DATA_API_URL = "http://localhost:8081"

.\auth-api\mvnw.cmd -f auth-api/pom.xml spring-boot:run
```

### Terminal 2: data-api

Use the same internal secret as Service A:

```powershell
$env:INTERNAL_TOKEN = "<your-internal-secret>"

.\data-api\mvnw.cmd -f data-api/pom.xml spring-boot:run
```

Spring Boot does not automatically load the root `.env` file when launched locally. Supply the variables through the terminal or the IDE run configuration.

## Database Initialization and Persistence

The database contains:

- `users`: UUID, unique email, and password hash
- `processing_log`: UUID, user ID, input, output, and creation timestamp

Initialization scripts are stored in:

```text
postgres/init/
```

The PostgreSQL image executes these scripts only when initializing an empty data directory. Editing an initialization script and restarting an existing container does not update the database schema.

Hibernate uses `ddl-auto: validate`: it checks the schema but does not create or modify tables.

The integration test uses a copy of the schema script under:

```text
auth-api/src/test/resources/db/001-create-tables.sql
```

Keep both schema scripts synchronized when changing the schema.

## Troubleshooting

### Port already in use

Stop local applications occupying ports `8080` or `8081`.

PostgreSQL is published on host port `5433` to avoid conflicts with a local PostgreSQL installation using `5432`.

### JWT secret is too short

If startup reports:

```text
JWT_SECRET must contain at least 32 UTF-8 bytes
```

Generate a new secret, update `.env`, and recreate `auth-api`.

Previously issued JWTs become invalid after changing the signing secret.

### Incorrect database address

Use:

- Local application: `jdbc:postgresql://localhost:5433/appdb`
- Containerized application: `jdbc:postgresql://postgres:5432/appdb`

Inside a container, `localhost` refers to that container itself.

### Processing returns 502

Check Service B logs and confirm that both applications received the same `INTERNAL_TOKEN`:

```powershell
docker compose logs --tail 100 data-api auth-api
```

Also check that `DATA_API_URL` is `http://data-api:8081` inside Compose.

Do not print or share resolved environment configuration containing secrets.

## Scope and Limitations

This is a small demonstration project.

- JWT authentication is stateless.
- Refresh tokens and token revocation are not implemented.
- The internal shared token is not a substitute for stronger service identity mechanisms.
- Local HTTP is used for development; production deployments require HTTPS and appropriate secret management.
- The PostgreSQL account created through `POSTGRES_USER` has elevated privileges; production applications should use a restricted database account.