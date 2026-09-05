package carolina

import groovy.json.JsonOutput

class PolyglotController {

    CatalogService catalogService

    def identity() {
        json CatalogService.identity()
    }

    def health() {
        json([status: 'ok'])
    }

    def years() {
        json([data: catalogService.years()])
    }

    def speakers() {
        Integer year = params.year ? params.int('year') : null
        json([data: catalogService.speakers(year)])
    }

    def speaker() {
        def row = catalogService.speaker(params.slug as String)
        if (!row) {
            response.status = 404
            json([error: 'not_found'])
            return
        }
        json([data: row])
    }

    def speakerYear() {
        def row = catalogService.speakerForYear(params.int('year'), params.slug as String)
        if (!row) {
            response.status = 404
            json([error: 'not_found'])
            return
        }
        json([data: row])
    }

    def sponsors() {
        Integer year = params.year ? params.int('year') : null
        json([data: catalogService.sponsors(year)])
    }

    def sponsor() {
        def row = catalogService.sponsor(params.slug as String)
        if (!row) {
            response.status = 404
            json([error: 'not_found'])
            return
        }
        json([data: row])
    }

    def sponsorYear() {
        def row = catalogService.sponsorForYear(params.int('year'), params.slug as String)
        if (!row) {
            response.status = 404
            json([error: 'not_found'])
            return
        }
        json([data: row])
    }

    def notFound() {
        response.status = 404
        json([error: 'not_found'])
    }

    private void json(Object payload) {
        response.setHeader('X-Polyglot-Language', CatalogService.LANGUAGE)
        response.setHeader('X-Polyglot-Framework', CatalogService.FRAMEWORK)
        response.contentType = 'application/json;charset=UTF-8'
        render JsonOutput.toJson(payload)
    }
}
