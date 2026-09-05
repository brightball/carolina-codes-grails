# carolina-codes-grails

Read-only v1 polyglot API for Carolina Code Conference. **Groovy** + **Grails 7.2.3** (latest stable) rest-api profile.

Queries PostgreSQL `v1_*` views via JDBC/Hikari. GORM is pointed at in-memory H2 with `dbCreate: none` so Grails cannot alter the shared catalog.

```bash
export JAVA_HOME="$(mise where java@21.0.2)"
DATABASE_URL=postgres://postgres:postgres@127.0.0.1:5432/carolina_dev \
CAROLINA_URL=http://127.0.0.1:4000 \
POLYGLOT_REGISTER_TOKEN=dev \
PUBLIC_BASE_URL=http://127.0.0.1:4020 \
PORT=4020 \
./gradlew --no-daemon bootRun
```

`GET /` reports `language: "Groovy"` and `framework: "Grails"`. `GET /health` returns `{"status":"ok"}` without touching Postgres.

```bash
./gradlew --no-daemon test
```
