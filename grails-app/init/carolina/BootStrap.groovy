package carolina

class BootStrap {

    def init = {
        String env = System.getProperty('grails.env') ?: System.getenv('GRAILS_ENV')
        if (env == 'test') {
            return
        }
        CatalogService.registerWithElixir()
    }

    def destroy = {
    }
}
