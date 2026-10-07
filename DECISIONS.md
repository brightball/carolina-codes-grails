# Decisions

Architecture decision records for this Grails 7 / Groovy 4 API.

Grails Forge does not generate an ADR directory, and Groovy has no separate decision format of its own. The practice that fits this stack is Michael Nygard's short record: status, date, context, decision, and consequences, kept in git next to the code. One file at the repo root is enough while the log is small. A Spock spec can read it from the project directory the same way it reads `build.gradle`. Numbered files under `docs/adr/` are the later split if this log grows. Do not start that split until an agent can no longer find a record by reading this file.

`MEMORY.md` is only the index of what is accepted now. This file is the log. Accepted records bind. Proposed records do not. A superseded record stays here with status `superseded` and a link to its replacement. Supersede a record instead of deleting it. Git history is the changelog. Do not add an amendment log to a record. When you change an accepted record in place, set an `Updated` date.

Add or update a record when a durable decision changes (catalog access, the JDK or Gradle pin, registration, the quality gates, or the public JSON shape). Update `MEMORY.md` in the same change. Skip trivia. Do not put secrets, home-directory paths, tailnet hostnames, or real tokens in this file. `postgres:postgres` and `POLYGLOT_REGISTER_TOKEN=dev` may appear only as the public local contract.

## D1. Query `v1_*` views with JDBC, and keep GORM off the CMS catalog

- Status: accepted
- Date: 2026-09-05

### Context

The polyglot API reads the same PostgreSQL catalog as the Phoenix CMS. That catalog's public contract is the `v1_*` views. Ash resource tables and the base tables under the views are not the API. Grails rest-api applications default to GORM owning `dataSource`, and a tutorial-shaped change would point that datasource at `DATABASE_URL` and set `dbCreate` so Hibernate manages schema. Either change can alter or lock the shared catalog.

### Decision

Query `v1_speakers`, `v1_sponsors`, `v1_years`, `v1_talks`, `v1_sponsorships`, and `v1_year_sponsors` with JDBC through a HikariCP pool in `CatalogService`. The pool is created on the first catalog call (`minimumIdle` 0, maximum 2). GORM uses an in-memory H2 database, `dbCreate: none`, in every environment, including production. Hibernate `hbm2ddl.auto` is `none`.

### Consequences

`GET /health` can run with no Postgres connection. A GORM domain mapping cannot migrate the CMS database. Catalog SQL stays in one service, which is what the Spock fake catalog replaces. Agents must not retarget `dataSource` at the CMS database to "simplify" persistence.

### Alternatives

Pointing GORM at the views was rejected because `dbCreate` and Hibernate DDL still sit on that connection. Copying the starter Compose database into this repo was rejected because the views already live in the CMS database (Postgres 16).

## D2. JDK 27, with the Gradle 9 Groovy compile workaround

- Status: accepted
- Date: 2026-09-22

### Context

The fleet moved this API onto JDK 27 (`mise` `java@27.0.0`, `JAVA_HOME`, Docker `openjdk:27`, release 27 in `build.gradle`). Grails 7.2.3 is built with the Gradle 9 wrapper (`gradle-9.8.0-rc-1`). Grails 7.2.3 assigns `groovyOptions.configurationScript` in a `GroovyCompile` `doFirst` action. Gradle 9 finalizes that property, so the mutation fails the build.

### Decision

Compile and run on JDK 27. Keep `scripts/gradle-java27` as the hook and CI entrypoint so a non-27 `JAVA_HOME` is not used by accident. In `build.gradle`, when the task graph is ready, remove the Grails `ClosureTaskAction` from each `GroovyCompile` task so it cannot assign `groovyOptions.configurationScript` after Gradle has finalized it.

### Consequences

JDK 21 pins, `scripts/gradle-java21`, and a Temurin 21 image are out of date for this repo. The `GroovyCompile` action removal is load-bearing. Deleting it as unused cleanup breaks the Gradle 9 build. Groovy itself stays on the version the Grails BOM resolves (`org.apache.groovy:groovy`); it is not pinned in `gradle.properties`.

### Alternatives

Staying on JDK 21 was the earlier pin and was superseded by this record on 2026-09-22. Downgrading the wrapper to Gradle 8 to avoid the `doFirst` clash was rejected because the rest of the JDK 27 build uses the Gradle 9 wrapper.

## D3. Register once on a daemon thread

- Status: accepted
- Date: 2026-09-22

### Context

The starter contract is one registration POST on boot and no heartbeat. Elixir keep-alives the warm API. A synchronous POST at startup adds the CMS connect and read timeout to the time until `GET /health` can be served. Fly suspends this process; a slow CMS would make every cold start look unhealthy.

### Decision

`BootStrap` calls `CatalogService.registerWithElixir()` once, except when the Grails env is `test`. The method returns as soon as a daemon thread is started. An empty `CAROLINA_URL` or token skips the POST. Failures are logged on that thread. The process keeps serving. `GET /health` does not call the catalog and does not wait on registration.

### Consequences

Registration can still be in flight when the first health check succeeds. A second call in-process does not send a second POST (`REGISTRATION_STARTED`). Do not add a heartbeat loop. Do not move the POST back onto the startup thread.

### Alternatives

Registering inside the Hikari pool startup was rejected because health and registration would then share one failure domain. Skipping registration entirely was rejected because the CMS rotates to this API only after a successful register.

## D4. Five separate fail-closed quality gates

- Status: accepted
- Date: 2026-09-22

### Context

A single `./gradlew check` or one pre-commit `run-all` hook hides which gate failed and encourages skipping every check to skip one of them. The gates that landed with the JDK 27 work are tests, static analysis, dependency vulnerabilities, secret scanning, and Groovy style.

### Decision

Run five fail-closed checks, each able to fail on its own:

* `./gradlew --no-daemon test`
* `./gradlew --no-daemon spotbugsMain` (SpotBugs plus the FindSecBugs plugin, `ignoreFailures = false`)
* `./gradlew --no-daemon dependencyAudit` (CycloneDX BOM of the resolved runtime classpath, then osv-scanner)
* `./gradlew --no-daemon codenarcMain` (CodeNarc, zero violations at every priority)
* `gitleaks detect --source . --verbose`

The same five are independent pre-commit hooks (`test`, `sast`, `audit`, `gitleaks`, `style`) and parallel Gitea jobs with no `needs` between them. Gradle hooks call `./scripts/gradle-java27`.

### Consequences

`codenarcTest` and `spotbugsTest` stay off so the gates measure application code. `dependencyAudit` requires osv-scanner on `PATH` or via `mise`. Do not fold the five commands into one hook or one CI job. Do not set `ignoreFailures = true`.

### Alternatives

A single Gradle `check` task was rejected because a style failure and a failing spec would be one red step. Running the gates only in CI was rejected because the pre-commit hooks already call the same commands.
