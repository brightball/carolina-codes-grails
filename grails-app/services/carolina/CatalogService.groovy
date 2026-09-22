package carolina

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import groovy.sql.Sql
import groovy.transform.CompileDynamic

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.sql.Connection
import java.time.Duration
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

@CompileDynamic
class CatalogService {

    static transactional = false

    static final String LANGUAGE = 'Groovy'
    static final String FRAMEWORK = 'Grails'
    static final String API_VERSION = '0.2.0'
    static final int CREATED_YEAR = 2026
    static final int SCHEMA_VERSION = 1
    static final List ENDPOINTS = [
        [method: 'GET', path: '/', query: []],
        [method: 'GET', path: '/health', query: []],
        [method: 'GET', path: '/v1/years', query: []],
        [method: 'GET', path: '/v1/speakers', query: ['year']],
        [method: 'GET', path: '/v1/speakers/:slug', query: []],
        [method: 'GET', path: '/v1/speakers/:year/:slug', query: []],
        [method: 'GET', path: '/v1/sponsors', query: ['year']],
        [method: 'GET', path: '/v1/sponsors/:slug', query: []],
        [method: 'GET', path: '/v1/sponsors/:year/:slug', query: []]
    ]

    static final String SPEAKER_COLS =
        'slug, first_name, last_name, name, tagline, bio, company, location, ' +
        'photo_path, twitter_url, linkedin_url, website_url, github_url, featured'
    static final String YEAR_SPONSOR_COLS =
        'slug, name, website, logo_path, description, blurb, tier, featured, year, ' +
        'twitter_url, linkedin_url, youtube_url, instagram_url, facebook_url'
    static final String SPONSOR_COLS =
        'slug, name, website, logo_path, description, twitter_url, linkedin_url, ' +
        'youtube_url, instagram_url, facebook_url'
    static final String TALK_COLS =
        'slug, title, description, format, youtube_id, year, speaker_slug, languages, topics'

    // Two connections are enough for a 1 vCPU shared-cpu Fly machine and stay
    // under the idle ceiling so startup does not open the whole pool.
    static final int POOL_MAX = 2
    static final int POOL_MIN_IDLE = 0

    static Closure queryFn
    static Closure connectFn
    static int sqlCount
    static int connectCount
    private static final Object COUNT_LOCK = new Object()
    private static final AtomicBoolean REGISTRATION_STARTED = new AtomicBoolean(false)
    private static HikariDataSource pool

    static void resetCounts() {
        synchronized (COUNT_LOCK) {
            sqlCount = 0
            connectCount = 0
        }
    }

    static void resetRegistration() {
        REGISTRATION_STARTED.set(false)
    }

    static Map identity() {
        [
            language         : LANGUAGE,
            language_version : GroovySystem.version,
            api_version      : API_VERSION,
            framework        : FRAMEWORK,
            created_year     : CREATED_YEAR,
            schema_version   : SCHEMA_VERSION,
            endpoints        : ENDPOINTS
        ]
    }

    static void registerWithElixir() {
        registerWithElixir(System.getenv('CAROLINA_URL'), System.getenv('POLYGLOT_REGISTER_TOKEN'))
    }

    // Returns as soon as the daemon thread is started. A stalled CMS must not
    // add its connect/read timeout to the time until /health can be served.
    static void registerWithElixir(String url, String token) {
        if (!url || !token) {
            return
        }
        if (!REGISTRATION_STARTED.compareAndSet(false, true)) {
            return
        }
        Thread worker = new Thread({ deliverRegistration(url, token) } as Runnable, 'elixir-register')
        worker.daemon = true
        worker.start()
    }

    static void deliverRegistration(String url, String token) {
        String port = System.getenv('PORT') ?: '4020'
        String base = System.getenv('PUBLIC_BASE_URL') ?: "http://127.0.0.1:${port}"
        String body = new groovy.json.JsonBuilder(identity() + [base_url: base]).toString()
        String root = url.replaceAll(/\/$/, '')
        Executor executor = { Runnable task ->
            Thread io = new Thread(task, 'elixir-register-io')
            io.daemon = true
            io.start()
        } as Executor
        HttpClient client = HttpClient.newBuilder()
            .executor(executor)
            .connectTimeout(Duration.ofSeconds(5))
            .build()
        HttpRequest request = HttpRequest.newBuilder(URI.create("${root}/internal/api-endpoints/register"))
            .timeout(Duration.ofSeconds(5))
            .header('Authorization', "Bearer ${token}")
            .header('Content-Type', 'application/json')
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        client.sendAsync(request, HttpResponse.BodyHandlers.discarding()).whenComplete { HttpResponse response, Throwable error ->
            if (error != null) {
                System.err.println("register: ${error.message}")
            } else {
                System.err.println("registered with elixir: ${response.statusCode()}")
            }
        }
    }

    List years() {
        query('SELECT year, slug, name, status FROM v1_years ORDER BY year DESC').collect { clean(it) }
    }

    List speakers(Integer year = null) {
        if (year == null) {
            return query("SELECT ${SPEAKER_COLS} FROM v1_speakers ORDER BY last_name, first_name").collect { clean(it) }
        }
        def rows = query(
            "SELECT ${SPEAKER_COLS} FROM v1_speakers " +
            'WHERE slug IN (SELECT speaker_slug FROM v1_talks WHERE year = ?) ' +
            'ORDER BY last_name, first_name',
            [year]
        ).collect { clean(it) }
        attachYearTags(rows, year)
    }

    Map speaker(String slug) {
        def row = query("SELECT ${SPEAKER_COLS} FROM v1_speakers WHERE slug = ?", [slug])
        if (!row) {
            return null
        }
        def talks = query("SELECT ${TALK_COLS} FROM v1_talks WHERE speaker_slug = ? ORDER BY year DESC", [slug]).collect { clean(it) }
        def yearsFor = query('SELECT DISTINCT year FROM v1_talks WHERE speaker_slug = ? ORDER BY year DESC', [slug]).collect { it.year as int }
        clean(row[0]) + [talks: talks, years: yearsFor]
    }

    Map speakerForYear(int year, String slug) {
        def row = query("SELECT ${SPEAKER_COLS} FROM v1_speakers WHERE slug = ?", [slug])
        if (!row) {
            return null
        }
        def talks = query(
            "SELECT ${TALK_COLS} FROM v1_talks WHERE speaker_slug = ? AND year = ? ORDER BY year DESC",
            [slug, year]
        ).collect { clean(it) }
        if (!talks) {
            return null
        }
        def yearsFor = query('SELECT DISTINCT year FROM v1_talks WHERE speaker_slug = ? ORDER BY year DESC', [slug]).collect { it.year as int }
        clean(row[0]) + [
            year        : year,
            talks       : talks,
            years       : yearsFor,
            other_years : yearsFor.findAll { it != year },
            languages   : uniqueTags(talks, 'languages'),
            topics      : uniqueTags(talks, 'topics')
        ]
    }

    List sponsors(Integer year = null) {
        if (year == null) {
            return query("SELECT ${SPONSOR_COLS} FROM v1_sponsors ORDER BY name").collect { clean(it) }
        }
        query("SELECT ${YEAR_SPONSOR_COLS} FROM v1_year_sponsors WHERE year = ? ORDER BY name", [year]).collect { clean(it) }
    }

    Map sponsor(String slug) {
        def row = query("SELECT ${SPONSOR_COLS} FROM v1_sponsors WHERE slug = ?", [slug])
        if (!row) {
            return null
        }
        def sponsorships = query('SELECT * FROM v1_sponsorships WHERE sponsor_slug = ?', [slug]).collect { clean(it) }
        clean(row[0]) + [sponsorships: sponsorships]
    }

    Map sponsorForYear(int year, String slug) {
        def row = query("SELECT ${YEAR_SPONSOR_COLS} FROM v1_year_sponsors WHERE year = ? AND slug = ?", [year, slug])
        if (!row) {
            return null
        }
        def yearsFor = query('SELECT DISTINCT year FROM v1_sponsorships WHERE sponsor_slug = ? ORDER BY year DESC', [slug]).collect { it.year as int }
        clean(row[0]) + [years: yearsFor, other_years: yearsFor.findAll { it != year }]
    }

    List query(String sql, List params = []) {
        synchronized (COUNT_LOCK) {
            sqlCount++
        }
        if (queryFn) {
            return queryFn.call(sql, params) as List
        }
        Connection c = checkout()
        Sql db = new Sql(c)
        try {
            return params ? db.rows(sql, params) : db.rows(sql)
        } finally {
            c.close()
        }
    }

    private static final Object POOL_LOCK = new Object()

    private Connection checkout() {
        if (connectFn) {
            synchronized (COUNT_LOCK) { connectCount++ }
            return connectFn.call() as Connection
        }
        ensurePool()
        pool.connection
    }

    private static void ensurePool() {
        if (pool != null && !pool.closed) {
            return
        }
        boolean created = false
        synchronized (POOL_LOCK) {
            if (pool != null && !pool.closed) {
                return
            }
            pool = new HikariDataSource(buildPoolConfig())
            created = true
        }
        if (created) {
            synchronized (COUNT_LOCK) {
                connectCount++
            }
        }
    }

    static HikariConfig buildPoolConfig() {
        HikariConfig cfg = new HikariConfig()
        cfg.jdbcUrl = jdbcUrl()
        cfg.username = jdbcUser()
        cfg.password = jdbcPassword()
        cfg.maximumPoolSize = POOL_MAX
        cfg.minimumIdle = POOL_MIN_IDLE
        cfg.initializationFailTimeout = -1
        cfg.connectionTimeout = 5000
        cfg.poolName = 'carolina-grails-catalog'
        cfg
    }

    private static String jdbcUrl() {
        String raw = System.getenv('DATABASE_URL') ?: 'postgres://postgres:postgres@127.0.0.1:5432/carolina_dev'
        if (raw.startsWith('jdbc:')) {
            return raw.contains('sslmode=') ? raw : raw + (raw.contains('?') ? '&' : '?') + 'sslmode=disable'
        }
        URI uri = URI.create(raw.replace('postgres://', 'http://').replace('postgresql://', 'http://'))
        String host = uri.host ?: '127.0.0.1'
        int port = uri.port > 0 ? uri.port : 5432
        String db = uri.path?.replaceFirst('/', '') ?: 'carolina_dev'
        "jdbc:postgresql://${host}:${port}/${db}?sslmode=disable"
    }

    private static String jdbcUser() {
        String raw = System.getenv('DATABASE_URL') ?: 'postgres://postgres:postgres@127.0.0.1:5432/carolina_dev'
        if (raw.startsWith('jdbc:')) {
            return 'postgres'
        }
        URI uri = URI.create(raw.replace('postgres://', 'http://').replace('postgresql://', 'http://'))
        (uri.userInfo ?: 'postgres:postgres').split(':', 2)[0]
    }

    private static String jdbcPassword() {
        String raw = System.getenv('DATABASE_URL') ?: 'postgres://postgres:postgres@127.0.0.1:5432/carolina_dev'
        if (raw.startsWith('jdbc:')) {
            return 'postgres'
        }
        URI uri = URI.create(raw.replace('postgres://', 'http://').replace('postgresql://', 'http://'))
        def parts = (uri.userInfo ?: 'postgres:postgres').split(':', 2)
        parts.length > 1 ? parts[1] : 'postgres'
    }

    private List attachYearTags(List speakers, int year) {
        if (!speakers) {
            return speakers
        }
        def slugs = speakers.collect { it.slug }
        def talksBy = loadTalksForYear(year)
        def yearsBy = loadYearsForSlugs(slugs)
        speakers.collect { Map sp ->
            String slug = sp.slug
            def talks = talksBy[slug] ?: []
            def yearsFor = yearsBy[slug] ?: []
            sp + [
                year      : year,
                talks     : talks,
                languages : uniqueTags(talks, 'languages'),
                topics    : uniqueTags(talks, 'topics'),
                years     : yearsFor
            ]
        }
    }

    private Map loadTalksForYear(int year) {
        def rows = query("SELECT ${TALK_COLS} FROM v1_talks WHERE year = ? ORDER BY speaker_slug, year DESC", [year]).collect { clean(it) }
        rows.groupBy { it.speaker_slug }
    }

    private Map loadYearsForSlugs(List slugs) {
        if (!slugs) {
            return [:]
        }
        def placeholders = slugs.collect { '?' }.join(', ')
        def rows = query(
            "SELECT DISTINCT speaker_slug, year FROM v1_talks WHERE speaker_slug IN (${placeholders}) ORDER BY speaker_slug, year DESC",
            slugs
        )
        def out = [:]
        rows.each { row ->
            String slug = row.speaker_slug
            out[slug] = (out[slug] ?: []) + [row.year as int]
        }
        out
    }

    private List uniqueTags(List talks, String key) {
        def seen = [] as LinkedHashSet
        talks.each { talk ->
            pgTextArray(talk[key]).each { seen << it }
        }
        seen as List
    }

    private List pgTextArray(value) {
        if (value == null) {
            return []
        }
        if (value instanceof List) {
            return value.collect { it.toString() }.findAll { it }
        }
        if (value instanceof String) {
            String stripped = value.trim()
            if (!stripped || stripped == '{}') {
                return []
            }
            String inner = stripped.startsWith('{') && stripped.endsWith('}') ? stripped[1..-2] : stripped
            return inner.split(',').collect { it.replaceAll(/^"|"$/, '').trim() }.findAll { it }
        }
        if (value instanceof java.sql.Array) {
            return (value.array as Object[]).collect { it?.toString() }.findAll { it }
        }
        [value.toString()]
    }

    private Map clean(row) {
        if (row == null) {
            return null
        }
        def out = [:]
        row.each { k, v ->
            String name = k.toString()
            if (name in ['languages', 'topics']) {
                out[name] = pgTextArray(v)
            } else if (name == 'year' && v != null) {
                out[name] = v as int
            } else {
                out[name] = v
            }
        }
        out
    }
}
