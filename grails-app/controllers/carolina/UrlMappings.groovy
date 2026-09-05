package carolina

class UrlMappings {
    static mappings = {
        "/"(controller: 'polyglot', action: 'identity')
        "/health"(controller: 'polyglot', action: 'health')
        "/v1/years"(controller: 'polyglot', action: 'years')
        "/v1/speakers"(controller: 'polyglot', action: 'speakers')
        "/v1/speakers/$year/$slug"(controller: 'polyglot', action: 'speakerYear') {
            constraints {
                year matches: /\d{4}/
            }
        }
        "/v1/speakers/$slug"(controller: 'polyglot', action: 'speaker')
        "/v1/sponsors"(controller: 'polyglot', action: 'sponsors')
        "/v1/sponsors/$year/$slug"(controller: 'polyglot', action: 'sponsorYear') {
            constraints {
                year matches: /\d{4}/
            }
        }
        "/v1/sponsors/$slug"(controller: 'polyglot', action: 'sponsor')
        "500"(controller: 'polyglot', action: 'notFound')
        "404"(controller: 'polyglot', action: 'notFound')
    }
}
