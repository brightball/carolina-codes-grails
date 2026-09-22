package carolina

import grails.testing.web.controllers.ControllerUnitTest
import groovy.json.JsonSlurper
import spock.lang.Specification

class PolyglotControllerSpec extends Specification implements ControllerUnitTest<PolyglotController> {

    CatalogService catalog

    def setup() {
        CatalogService.resetCounts()
        CatalogService.queryFn = null
        CatalogService.connectFn = null
        catalog = new CatalogService()
        controller.catalogService = catalog
    }

    void 'shipped app is Grails not a Java HttpServer relabel'() {
        expect:
        file('grails-app/controllers/carolina/PolyglotController.groovy').text.contains('class PolyglotController')
        file('build.gradle').text.contains('org.apache.grails')
        file('gradle.properties').text.contains('7.2.3')
        !file('grails-app/controllers/carolina/PolyglotController.groovy').text.contains('com.sun.net.httpserver')
        CatalogService.LANGUAGE == 'Groovy'
        CatalogService.FRAMEWORK == 'Grails'
        file('grails-app/conf/application.yml').text.contains('dbCreate: none')
        file('grails-app/conf/application.yml').text.contains('grails_not_catalog')
        !(file('grails-app/domain').listFiles()?.any { it.name.endsWith('.groovy') })
    }

    void 'GET health is ok JSON without SQL or Postgres'() {
        given:
        CatalogService.queryFn = { String sql, List args ->
            throw new IllegalStateException("health touched SQL: ${sql}")
        }

        when:
        controller.health()

        then:
        response.status == 200
        json.status == 'ok'
        headersArePolyglot()
        CatalogService.sqlCount == 0
        CatalogService.connectCount == 0
    }

    void 'GET identity is Groovy and Grails without SQL'() {
        given:
        CatalogService.queryFn = { String sql, List args ->
            throw new IllegalStateException("identity touched SQL: ${sql}")
        }

        when:
        controller.identity()

        then:
        response.status == 200
        json.language == 'Groovy'
        json.framework == 'Grails'
        headersArePolyglot()
        CatalogService.sqlCount == 0
        CatalogService.connectCount == 0
    }

    void 'GET years returns a data array'() {
        given:
        stubCatalog(1, 1)

        when:
        controller.years()

        then:
        response.status == 200
        headersArePolyglot()
        json.data instanceof List
        json.data
        (json.data[0].year as int) == 2026
    }

    void 'GET speakers returns a data array'() {
        given:
        stubCatalog(2, 1)

        when:
        controller.speakers()

        then:
        response.status == 200
        headersArePolyglot()
        json.data instanceof List
        json.data.size() == 2
    }

    void 'GET sponsors returns a data array'() {
        given:
        stubCatalog(1, 2)

        when:
        controller.sponsors()

        then:
        response.status == 200
        headersArePolyglot()
        json.data instanceof List
        json.data.size() == 2
    }

    void 'known speaker returns data and an unknown slug is 404 not_found'() {
        given:
        stubCatalog(1, 1)

        when:
        params.slug = 'diana-pham'
        controller.speaker()

        then:
        response.status == 200
        headersArePolyglot()
        json.data.slug == 'diana-pham'
        json.data.talks instanceof List
        json.data.talks

        when:
        response.reset()
        params.slug = 'no-such-slug'
        controller.speaker()

        then:
        response.status == 404
        headersArePolyglot()
        json.error == 'not_found'
    }

    void 'known speaker year returns data and an unknown slug is 404 not_found'() {
        given:
        stubCatalog(1, 1)

        when:
        params.year = '2026'
        params.slug = 'diana-pham'
        controller.speakerYear()

        then:
        response.status == 200
        headersArePolyglot()
        json.data.slug == 'diana-pham'
        (json.data.year as int) == 2026
        json.data.languages instanceof List
        json.data.topics instanceof List

        when:
        response.reset()
        params.slug = 'no-such-slug'
        controller.speakerYear()

        then:
        response.status == 404
        headersArePolyglot()
        json.error == 'not_found'
    }

    void 'known sponsor returns data and an unknown slug is 404 not_found'() {
        given:
        stubCatalog(1, 1)

        when:
        params.slug = 'flywheel'
        controller.sponsor()

        then:
        response.status == 200
        headersArePolyglot()
        json.data.slug == 'flywheel'
        json.data.sponsorships instanceof List

        when:
        response.reset()
        params.slug = 'no-such-slug'
        controller.sponsor()

        then:
        response.status == 404
        headersArePolyglot()
        json.error == 'not_found'
    }

    void 'known sponsor year returns data and an unknown slug is 404 not_found'() {
        given:
        stubCatalog(1, 1)

        when:
        params.year = '2026'
        params.slug = 'flywheel'
        controller.sponsorYear()

        then:
        response.status == 200
        headersArePolyglot()
        json.data.slug == 'flywheel'
        json.data.tier == 'platinum'

        when:
        response.reset()
        params.slug = 'no-such-slug'
        controller.sponsorYear()

        then:
        response.status == 404
        headersArePolyglot()
        json.error == 'not_found'
    }

    void 'unmapped path is 404 JSON not_found'() {
        when:
        controller.notFound()

        then:
        response.status == 404
        headersArePolyglot()
        json.error == 'not_found'
        CatalogService.sqlCount == 0
        CatalogService.connectCount == 0
    }

    void 'year-scoped speakers wrap data and include languages and topics from v1_talks'() {
        given:
        stubCatalog(1, 1)
        CatalogService.resetCounts()

        when:
        params.year = '2026'
        controller.speakers()

        then:
        response.status == 200
        headersArePolyglot()
        json.data instanceof List
        json.data
        json.data[0].languages instanceof List
        json.data[0].topics instanceof List
        json.data[0].languages.contains('groovy')
        json.data[0].topics.contains('development')
        CatalogService.sqlCount > 0
    }

    void 'year-scoped sponsors wrap data and include tier'() {
        given:
        stubCatalog(1, 1)

        when:
        params.year = '2026'
        controller.sponsors()

        then:
        response.status == 200
        headersArePolyglot()
        json.data instanceof List
        json.data
        json.data[0].tier == 'platinum'
    }

    void 'year-scoped speaker and sponsor SQL counts stay flat as rows grow'() {
        when:
        int speakersSmall = sqlCountForSpeakers(2)
        int speakersLarge = sqlCountForSpeakers(8)
        int sponsorsSmall = sqlCountForSponsors(2)
        int sponsorsLarge = sqlCountForSponsors(8)

        then:
        speakersSmall > 0
        speakersSmall == speakersLarge
        speakersLarge < 8
        sponsorsSmall > 0
        sponsorsSmall == sponsorsLarge
        sponsorsLarge < 8
    }

    private boolean headersArePolyglot() {
        assert response.getHeader('X-Polyglot-Language') == 'Groovy'
        assert response.getHeader('X-Polyglot-Framework') == 'Grails'
        assert response.contentType?.contains('application/json')
        true
    }

    private int sqlCountForSpeakers(int rows) {
        response.reset()
        CatalogService.resetCounts()
        stubCatalog(rows, rows)
        params.year = '2026'
        controller.speakers()
        assert response.status == 200
        assert json.data.size() == rows
        CatalogService.sqlCount
    }

    private int sqlCountForSponsors(int rows) {
        response.reset()
        CatalogService.resetCounts()
        stubCatalog(rows, rows)
        params.year = '2026'
        controller.sponsors()
        assert response.status == 200
        assert json.data.size() == rows
        CatalogService.sqlCount
    }

    private Map getJson() {
        new JsonSlurper().parseText(response.text) as Map
    }

    private File file(String path) {
        new File(System.getProperty('user.dir'), path)
    }

    private void stubCatalog(int speakerRows, int sponsorRows) {
        CatalogService.queryFn = { String sql, List args ->
            rowsFor(sql, args, speakerRows, sponsorRows)
        }
    }

    private List rowsFor(String sql, List args, int speakerRows, int sponsorRows) {
        String compact = sql.replaceAll(/\s+/, ' ').trim()
        if (compact.contains('FROM v1_years')) {
            return [[year: 2026, slug: '2026', name: 'Carolina Code Conference 2026', status: 'announced']]
        }
        if (compact.contains('FROM v1_speakers WHERE slug =')) {
            String slug = args ? args[0] as String : ''
            return slug == 'diana-pham' ? [speakerRow('diana-pham')] : []
        }
        if (compact.contains('FROM v1_speakers')) {
            return (1..speakerRows).collect { int i -> speakerRow("speaker-${i}") }
        }
        if (compact.contains('speaker_slug IN')) {
            return (1..speakerRows).collect { int i -> [speaker_slug: "speaker-${i}", year: 2026] }
        }
        if (compact.contains('FROM v1_talks WHERE speaker_slug = ? AND year = ?')) {
            String slug = args[0] as String
            int year = args[1] as int
            return (slug == 'diana-pham' && year == 2026) ? [talkRow('diana-pham')] : []
        }
        if (compact.contains('SELECT DISTINCT year FROM v1_talks')) {
            return [[year: 2026]]
        }
        if (compact.contains('FROM v1_talks WHERE speaker_slug =')) {
            String slug = args ? args[0] as String : ''
            return slug == 'diana-pham' ? [talkRow('diana-pham')] : []
        }
        if (compact.contains('FROM v1_talks WHERE year')) {
            return (1..speakerRows).collect { int i -> talkRow("speaker-${i}") }
        }
        if (compact.contains('FROM v1_year_sponsors WHERE year = ? AND slug =')) {
            String slug = args[1] as String
            return slug == 'flywheel' ? [yearSponsorRow('flywheel')] : []
        }
        if (compact.contains('FROM v1_year_sponsors')) {
            return (1..sponsorRows).collect { int i -> yearSponsorRow("sponsor-${i}") }
        }
        if (compact.contains('SELECT DISTINCT year FROM v1_sponsorships')) {
            return [[year: 2026], [year: 2025]]
        }
        if (compact.contains('FROM v1_sponsorships')) {
            return [[sponsor_slug: args ? args[0] : 'flywheel', year: 2026, tier: 'platinum']]
        }
        if (compact.contains('FROM v1_sponsors WHERE slug')) {
            String slug = args ? args[0] as String : ''
            return slug == 'flywheel' ? [sponsorRow('flywheel')] : []
        }
        if (compact.contains('FROM v1_sponsors')) {
            return (1..sponsorRows).collect { int i -> sponsorRow(i == 1 ? 'flywheel' : "sponsor-${i}") }
        }
        throw new IllegalStateException("unexpected SQL: ${compact}")
    }

    private Map speakerRow(String slug) {
        [
            slug       : slug,
            first_name : 'Diana',
            last_name  : 'Pham',
            name       : 'Diana Pham',
            company    : 'Example',
            featured   : false
        ]
    }

    private Map talkRow(String speakerSlug) {
        [
            slug         : "talk-${speakerSlug}",
            title        : 'Talk',
            description  : 'A talk',
            format       : 'talk',
            youtube_id   : 'abc123',
            year         : 2026,
            speaker_slug : speakerSlug,
            languages    : '{groovy,java}',
            topics       : '{development}'
        ]
    }

    private Map sponsorRow(String slug) {
        [
            slug        : slug,
            name        : slug == 'flywheel' ? 'Flywheel' : slug,
            website     : 'https://example.test',
            logo_path   : '/logo.png',
            description : 'Sponsor'
        ]
    }

    private Map yearSponsorRow(String slug) {
        sponsorRow(slug) + [tier: 'platinum', blurb: 'Thanks', featured: true, year: 2026]
    }
}
