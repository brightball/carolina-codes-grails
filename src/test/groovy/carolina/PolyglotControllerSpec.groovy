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
        when:
        controller.health()

        then:
        response.status == 200
        response.text.contains('"status"')
        response.text.contains('"ok"')
        json.status == 'ok'
        CatalogService.sqlCount == 0
        CatalogService.connectCount == 0
    }

    void 'GET identity is Groovy and Grails without SQL'() {
        when:
        controller.identity()

        then:
        response.status == 200
        json.language == 'Groovy'
        json.framework == 'Grails'
        CatalogService.sqlCount == 0
    }

    void 'unknown speaker slug returns 404'() {
        given:
        ensureCatalog()

        when:
        params.slug = 'no-such-slug'
        controller.speaker()

        then:
        response.status == 404
        response.text.contains('not_found')
    }

    void 'year-scoped speakers wrap data and include languages and topics from v1_talks'() {
        given:
        ensureCatalog()
        CatalogService.resetCounts()

        when:
        params.year = '2026'
        controller.speakers()

        then:
        response.status == 200
        json.data instanceof List
        json.data
        json.data[0].containsKey('languages')
        json.data[0].containsKey('topics')
        json.data[0].languages instanceof List
        json.data[0].topics instanceof List
        CatalogService.sqlCount > 0
    }

    void 'year-scoped sponsors wrap data and include tier'() {
        given:
        ensureCatalog()

        when:
        params.year = '2026'
        controller.sponsors()

        then:
        response.status == 200
        json.data instanceof List
        json.data
        json.data[0].containsKey('tier')
    }

    private Map getJson() {
        new JsonSlurper().parseText(response.text) as Map
    }

    private File file(String path) {
        new File(System.getProperty('user.dir'), path)
    }

    private void ensureCatalog() {
        try {
            catalog.query('SELECT 1 AS ok')
        } catch (Exception ignored) {
            CatalogService.queryFn = { String sql, List args ->
                if (sql.contains('FROM v1_speakers WHERE slug =')) {
                    return []
                }
                if (sql.contains('FROM v1_speakers')) {
                    return [[slug: 'diana-pham', first_name: 'Diana', last_name: 'Pham', name: 'Diana Pham']]
                }
                if (sql.contains('FROM v1_talks')) {
                    return [[slug: 'talk', title: 'Talk', speaker_slug: 'diana-pham', year: 2026, languages: '{groovy}', topics: '{development}']]
                }
                if (sql.contains('FROM v1_year_sponsors')) {
                    return [[slug: 'flywheel', name: 'Flywheel', tier: 'platinum', year: 2026]]
                }
                if (sql.contains('FROM v1_sponsors WHERE slug')) {
                    return []
                }
                []
            }
        }
    }
}
