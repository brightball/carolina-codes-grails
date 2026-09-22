# carolina-codes-grails

Read-only v1 polyglot API for Carolina Code Conference. **Groovy** + **Grails 7.2.3** (latest stable) rest-api profile.

Queries PostgreSQL `v1_*` views via JDBC/Hikari. GORM is pointed at in-memory H2 with `dbCreate: none` so Grails cannot alter the shared catalog.

```bash
export JAVA_HOME="$(mise where java@27.0.0)"
DATABASE_URL=postgres://postgres:postgres@127.0.0.1:5432/carolina_dev \
CAROLINA_URL=http://127.0.0.1:4000 \
POLYGLOT_REGISTER_TOKEN=dev \
PUBLIC_BASE_URL=http://127.0.0.1:4020 \
PORT=4020 \
./gradlew --no-daemon bootRun
```

`GET /` reports `language: "Groovy"` and `framework: "Grails"`. `GET /health` returns `{"status":"ok"}` without touching Postgres. Elixir registration runs in the background at startup, so an unreachable `CAROLINA_URL` does not delay `/health`.

```bash
./gradlew --no-daemon test
./gradlew --no-daemon spotbugsMain          # SAST (SpotBugs + FindSecBugs)
./gradlew --no-daemon dependencyAudit       # OSV scan of resolved runtime deps
./gradlew --no-daemon codenarcMain          # Groovy style
gitleaks detect --source . --verbose
```

Pre-commit runs those five checks as independent hooks (`SKIP=test,sast,audit,gitleaks,style git commit` to escape). Install once with `pre-commit install`. Hooks need `pre-commit`, `gitleaks`, and `osv-scanner` on PATH (or `mise` with this repo’s `mise.toml`); Gradle hooks call `./scripts/gradle-java27` so a JDK 27 `JAVA_HOME` or `mise` java@27 is used.

Gitea Actions (`.gitea/workflows/ci.yml`) runs the same five checks as parallel jobs.
