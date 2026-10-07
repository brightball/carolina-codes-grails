# Memory

Index of accepted decisions for this Grails 7 / Groovy 4 API. The records, including why and what was rejected, are in [DECISIONS.md](DECISIONS.md).

Grails Forge does not ship an ADR folder. This file is the working index an agent should read first. `DECISIONS.md` is the Nygard-style log. Accepted records bind. When a durable decision changes, add or update the record in `DECISIONS.md` and change this index in the same commit. Supersede a record instead of deleting it. Git history is the changelog.

| ID | Accepted fact |
| --- | --- |
| D1 | JDBC and HikariCP query the PostgreSQL `v1_*` views. GORM stays on in-memory H2 with `dbCreate: none` and is not pointed at the CMS catalog. |
| D2 | JDK 27 (`JAVA_HOME` or mise `java@27.0.0`). The Gradle 9 build keeps the `GroovyCompile` workaround that drops Grails' `groovyOptions.configurationScript` mutation. |
| D3 | Register once on a daemon thread at boot. No heartbeat. If the CMS is down, log and keep serving. `GET /health` does not need the database. |
| D4 | Five fail-closed gates, as separate pre-commit hooks and parallel Gitea jobs: `./gradlew --no-daemon test`, `spotbugsMain`, `dependencyAudit`, `codenarcMain`, and `gitleaks detect --source . --verbose`. Gradle hooks use `./scripts/gradle-java27`. |

The API is read-only and speaks ordinary JSON for the OpenAPI v1 routes. Never query Ash tables.

Do not put secrets, home-directory paths, or tailnet hostnames in this file.
