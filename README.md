# carolina-codes-grails

Read-only v1 polyglot API for Carolina Code Conference. **Groovy 4.0.33** and **Grails 7.2.3** (rest-api profile) on **JDK 27**.

Groovy **4.0.33** is the `org.apache.groovy:groovy` version the Grails BOM resolves onto the runtime classpath. `GET /` reports that same value from `GroovySystem.version`. Grails **7.2.3** is `grailsVersion` in `gradle.properties`. JDK **27** is mise `java@27.0.0` (`JAVA_HOME` may point at that JDK). The Gradle wrapper is Gradle 9 (`gradle-9.8.0-rc-1`).

Notable packages already in this build:

- SpotBugs Gradle plugin 6.5.11 and FindSecBugs 1.14.0 (`spotbugsMain`)
- CodeNarc 3.7.0-groovy-4.0 (`codenarcMain`)
- A CycloneDX 1.5 BOM of the resolved runtime classpath, scanned with osv-scanner 2.6.0 (`dependencyAudit`)
- gitleaks 8.30.1 (`gitleaks detect`)
- HikariCP and the PostgreSQL JDBC driver 42.7.13 for catalog reads; H2 is on the runtime classpath only so GORM can stay off the CMS database

Queries PostgreSQL `v1_*` views via JDBC/Hikari. GORM is pointed at in-memory H2 with `dbCreate: none` so Grails cannot alter the shared catalog. The decision log is `DECISIONS.md` (`MEMORY.md` indexes it).

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
