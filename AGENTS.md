# carolina-codes-grails

Read-only v1 polyglot API. See README.md for install, run, and test commands.

## Cursor Cloud specific instructions

This repository is one sibling git remote in the carolina.codes polyglot fleet. Cloud agents should treat **this repo** as the workspace root. The Phoenix CMS is a different remote (`github.com/brightball/carolina-codes`); do not assume `../elixir` or other sibling directories exist unless those remotes are attached to the same Cloud environment.

Postgres `v1_*` views live in the CMS database. Handler/unit tests that use a fake catalog do not need Postgres. For live HTTP against the views, start Postgres 16 and set:

- `DATABASE_URL=postgres://postgres:postgres@127.0.0.1:5432/carolina_dev`
- `CAROLINA_URL=http://127.0.0.1:4000` (optional; registration no-ops if CMS is down)
- `POLYGLOT_REGISTER_TOKEN=dev`
- `PUBLIC_BASE_URL` / `PORT` as in the README

Do not query Ash tables. Do not fold this tree into the CMS git remote. Contract: CMS `priv/api/openapi.yaml` + `priv/api/AGENTS.md`.

Requires JDK 27 (`JAVA_HOME` / `mise` java@27.0.0). Quality gates (also pre-commit hooks and parallel Gitea jobs): `./gradlew --no-daemon test`, `spotbugsMain`, `dependencyAudit`, `codenarcMain`, and `gitleaks detect --source . --verbose`. Install hooks with `pre-commit install`. `dependencyAudit` writes a CycloneDX BOM from Gradle's resolved runtime classpath and scans it with osv-scanner. Gradle hooks use `./scripts/gradle-java27`.
