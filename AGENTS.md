# carolina-codes-grails

Finished read-only v1 HTTP API for the Carolina Code Conference polyglot fleet. **Groovy** and **Grails 7.2.3** (rest-api profile) on **JDK 27**.

This repository is that Grails API. Treat **this repo** as the workspace root. The Phoenix CMS is a different remote (`github.com/brightball/carolina-codes`). You do not need a checkout of the Elixir CMS, and you must not assume `../elixir` or any sibling directory exists. Do not fold this tree into the CMS git remote.

The HTTP contract is ordinary JSON for the OpenAPI v1 routes. Do not implement Ash JSON:API (`application/vnd.api+json`). Route and payload shape follow the CMS contract on that other remote: `priv/api/openapi.yaml` and `priv/api/AGENTS.md`.

## Purpose

The Phoenix app (`Carolina.Polyglot`) keeps **at most one** language API warm and reads speakers and sponsors from it. With no APIs registered, it falls back to Ash. This process must:

1. Query PostgreSQL `v1_*` views. Never query Ash resource tables. Never `SELECT` base catalog tables (`speakers`, `organizations`, `talks`, and the rest) as the public contract. The views are the API.
2. Expose the v1 routes below as ordinary JSON.
3. **Register once on boot** with the Elixir site (no heartbeat). If `CAROLINA_URL` is unset or the CMS is down, log and keep serving.

The API is read-only. No writes.

## Environment

| Variable | Example | Role |
| --- | --- | --- |
| `DATABASE_URL` | `postgres://postgres:postgres@127.0.0.1:5432/carolina_dev` | SQL views |
| `CAROLINA_URL` | `http://127.0.0.1:4000` | Elixir site (optional; register no-ops if down) |
| `POLYGLOT_REGISTER_TOKEN` | `dev` | Bearer token for register |
| `PUBLIC_BASE_URL` | `http://127.0.0.1:4020` | URL Elixir will call |
| `PORT` | `4020` | Listen port (`server.port` defaults to 4020; the container image sets `PORT=8080`) |

Postgres `v1_*` views live in the CMS database (Postgres 16). Handler and unit tests that use a fake catalog do not need Postgres. For live HTTP against the views, start Postgres 16 and set the variables above.

`GET /health` does not need the database. It returns `{"status":"ok"}`.

## SQL views (query these)

`v1_speakers`, `v1_sponsors`, `v1_years`, `v1_talks`, `v1_sponsorships`, `v1_year_sponsors`.

`CatalogService` queries those views with JDBC and HikariCP. GORM is not pointed at the CMS catalog. Its datasource is in-memory H2 with `dbCreate: none` in every environment, so Hibernate cannot alter the shared catalog. See [DECISIONS.md](DECISIONS.md) (D1).

Year-scoped speaker listings include `languages` and `topics` taken from `v1_talks`. Year-scoped sponsor rows include `tier` and `blurb`.

`photo_path` and `logo_path` values are web paths. Return the path. Serving the bytes is optional (the CMS usually hosts them).

## Required HTTP routes

Wrap list payloads as `{ "data": [ ... ] }`. Unknown slugs return 404 and `{ "error": "not_found" }`.

* `GET /health` — liveness (`{ "status": "ok" }`), no database
* `GET /` — identity (`language`, `language_version`, `api_version`, `framework`, `created_year`, `schema_version`, `endpoints`)
* `GET /v1/years`
* `GET /v1/speakers` and `GET /v1/speakers?year=2025`
* `GET /v1/speakers/{slug}` and `GET /v1/speakers/{year}/{slug}`
* `GET /v1/sponsors` and `GET /v1/sponsors?year=2025`
* `GET /v1/sponsors/{slug}` and `GET /v1/sponsors/{year}/{slug}`

`GET /` reports `language: "Groovy"` and `framework: "Grails"`. `language_version` is `GroovySystem.version`. Controllers render ordinary JSON (`application/json`) and set `X-Polyglot-Language` and `X-Polyglot-Framework`. Do not switch the public contract to Ash JSON:API.

## Register on boot (once)

`POST {CAROLINA_URL}/internal/api-endpoints/register`

```
Authorization: Bearer {POLYGLOT_REGISTER_TOKEN}
Content-Type: application/json
```

Body fields: `language`, `language_version`, `api_version`, `framework`, `created_year`, `base_url` (`PUBLIC_BASE_URL`), `schema_version` (1), `endpoints` (the identity endpoint list).

`BootStrap` calls `CatalogService.registerWithElixir()` once, outside the test environment. The call starts a daemon thread and returns. Do not heartbeat. Elixir keep-alives the currently warm API.

If `CAROLINA_URL` or the token is empty, registration returns without a request. If the POST fails (connection refused, timeout, 4xx/5xx), log and keep serving. A stalled CMS must not delay `GET /health`.

## Layout

| Path | Role |
| --- | --- |
| `grails-app/controllers/carolina` | Routes and ordinary JSON |
| `grails-app/services/carolina/CatalogService.groovy` | JDBC catalog and one-shot registration |
| `grails-app/init/carolina/BootStrap.groovy` | Starts registration outside tests |
| `grails-app/conf/application.yml` | H2 datasource for GORM (`dbCreate: none`), port |
| `src/test/groovy/carolina` | Spock specs (fake catalog; no Postgres) |
| `build.gradle` | Grails 7.2.3, JDK 27, fail-closed gates |
| `gradle.properties` | `grailsVersion` |
| `mise.toml` | JDK `27.0.0`, gitleaks, osv-scanner |
| `scripts/gradle-java27` | JDK 27 Gradle entrypoint for hooks and CI |
| `config/codenarc`, `config/spotbugs` | Style and SAST filters |
| `.pre-commit-config.yaml` | Five independent hooks |
| `.gitea/workflows/ci.yml` | The same five checks as parallel jobs |
| `Dockerfile` | This app's JDK 27 Grails image |
| `DECISIONS.md` | Accepted architecture decisions |
| `MEMORY.md` | Index of those decisions |

## JDK and quality gates

Requires JDK 27 (`JAVA_HOME` or `mise` `java@27.0.0`). `build.gradle` sets source and release to 27.

Fail-closed gates, also installed as pre-commit hooks and as parallel Gitea jobs:

* `./gradlew --no-daemon test`
* `./gradlew --no-daemon spotbugsMain`
* `./gradlew --no-daemon dependencyAudit`
* `./gradlew --no-daemon codenarcMain`
* `gitleaks detect --source . --verbose`

Install hooks with `pre-commit install`. `dependencyAudit` writes a CycloneDX BOM from Gradle's resolved runtime classpath and scans it with osv-scanner. Gradle hooks use `./scripts/gradle-java27`.

Grails 7.2.3 assigns `groovyOptions.configurationScript` inside `doFirst`. Gradle 9 finalizes that property, so `build.gradle` drops that execution-time mutation before the task runs. Leave that workaround in place. See D2 in `DECISIONS.md`.

## Decisions

Durable choices are recorded in [DECISIONS.md](DECISIONS.md). [MEMORY.md](MEMORY.md) indexes the accepted facts. Grails Forge does not create an ADR directory, so this app keeps one Nygard-style log at the repo root (context, decision, consequences) and a short index beside it. Spock reads both from the project directory.

Accepted records bind. A superseded record stays in the log and does not govern current work. If a task conflicts with an accepted record, update the log in the same change. Add or update a record when a durable decision changes (catalog access, JDK or Gradle pin, registration, quality gates, or the public JSON shape). Supersede a record instead of deleting it. Git history is the changelog. Do not keep an amendment log inside a record. When you change an accepted record in place, set its `Updated` date and update `MEMORY.md` so the index matches.

Do not put secrets, home-directory paths, tailnet hostnames, or real tokens in these files. The local examples in the environment table (`postgres:postgres`, `POLYGLOT_REGISTER_TOKEN=dev`) document the dev contract only.

## Checklist

* OpenAPI paths return 200 with ordinary JSON of the contract shape (404 on an unknown slug)
* `?year=` speaker rows include `languages` and `topics`; year-scoped sponsor rows include `tier`
* Register runs once at process start, on a daemon thread; log and keep serving if the Elixir site is down
* No writes; no Ash table names; GORM stays on H2 with `dbCreate: none`
* `GET /health` does not need the database
* JDK 27 and the five fail-closed gates still pass
* `DECISIONS.md` and `MEMORY.md` updated when a durable decision changes
