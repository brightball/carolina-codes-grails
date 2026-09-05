package carolina

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import groovy.sql.Sql
import groovy.transform.CompileDynamic

import java.net.URI
import java.sql.Connection

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

    static Closure queryFn
    static Closure connectFn
    static int sqlCount
    static int connectCount
    private static final Object COUNT_LOCK = new Object()
    private static HikariDataSource pool

    static void resetCounts() {
        synchronized (COUNT_LOCK) {
            sqlCount = 0
            connectCount = 0
        }
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
        String url = System.getenv('CAROLINA_URL')
        String token = System.getenv('POLYGLOT_REGISTER_TOKEN')
        if (!url || !token) {
            return
        }
        String port = System.getenv('PORT') ?: '4020'
        String base = System.getenv('PUBLIC_BASE_URL') ?: "http://127.0.0.1:${port}"
        def body = new groovy.json.JsonBuilder(identity() + [base_url: base]).toString()
        def post = new URL("${url.replaceAll(/\/$/, '')}/internal/api-endpoints/register")
        try {
            HttpURLConnection conn = (HttpURLConnection) post.openConnection()
            conn.requestMethod = 'POST'
            conn.doOutput = true
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.setRequestProperty('Authorization', "Bearer ${token}")
            conn.setRequestProperty('Content-Type', 'application/json')
            conn.outputStream.withWriter('UTF-8') { it << body }
            int status = conn.responseCode
            System.err.println("registered with elixir: ${status}")
        } catch (Exception e) {
            System.err.println("register: ${e.message}")
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
        synchronized (POOL_LOCK) {
            if (pool != null && !pool.closed) {
                return
            }
            synchronized (COUNT_LOCK) { connectCount++ }
            HikariConfig cfg = new HikariConfig()
            cfg.jdbcUrl = jdbcUrl()
            cfg.username = jdbcUser()
            cfg.password = jdbcPassword()
            cfg.maximumPoolSize = 8
            cfg.poolName = 'carolina-grails-catalog'
            pool = new HikariDataSource(cfg)
        }
    }

    private static String jdbcUrl() {
        String raw = System.getenv('DATABASE_URL') ?: 'postgres://postgres:postgres@127.0.0.1:5432/carolina_dev'
        if (raw.startsWith('jdbc:')) {
            return raw.contains('sslmode=') ? raw : raw + (raw.contains('?') ? '&' : '?') + 'sslmode=disable'
        }
        URI uri = URI.create(raw.replace('postgres://', 'http://').replace('postgresql://', 'http://'))
        String userInfo = uri.userInfo ?: 'postgres:postgres'
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
