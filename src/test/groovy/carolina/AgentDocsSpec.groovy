package carolina

import spock.lang.Specification

/**
 * Reads the agent docs from the project directory and checks them against
 * the starter contract, the JDK pin, and the Groovy runtime under test.
 */
class AgentDocsSpec extends Specification {

    void 'AGENTS.md keeps the starter contract, JDK 27 gates, and decision-log rule'() {
        given:
        String agents = file('AGENTS.md').text

        expect:
        agents.contains('v1_*')
        agents.contains('Never query Ash')
        agents.contains('ordinary JSON')
        agents.contains('application/vnd.api+json')
        agents.contains('Register once on boot')
        agents.contains('no heartbeat')
        agents.contains('CAROLINA_URL')
        agents.contains('log and keep serving')
        agents.contains('GET /health` does not need the database')
        agents.contains('read-only')
        agents.contains('workspace root')
        agents.contains('github.com/brightball/carolina-codes')
        agents.contains('JDK 27')
        agents.contains('java@27.0.0')
        agents.contains('./gradlew --no-daemon test')
        agents.contains('spotbugsMain')
        agents.contains('dependencyAudit')
        agents.contains('codenarcMain')
        agents.contains('gitleaks detect --source . --verbose')
        agents.contains('./scripts/gradle-java27')
        agents.contains('pre-commit')
        agents.contains('Gitea')
        agents.contains('DECISIONS.md')
        agents.contains('MEMORY.md')
        agents.contains('Add or update a record when a durable decision changes')
        agents.contains('Supersede a record instead of deleting it')
        !agents.contains('test_catalog.py')
        !agents.contains('Postgres 18')
        !agents.contains('forkable starter')
        !(agents =~ /(?i)\breplace\b[^\n]{0,80}Dockerfile/)
        file('DECISIONS.md').exists()
        file('MEMORY.md').exists()
    }

    void 'decision log records the catalog choice and the JDK 27 quality gates'() {
        given:
        String decisions = file('DECISIONS.md').text
        String memory = file('MEMORY.md').text

        expect:
        decisions.contains('Add or update a record when a durable decision changes')
        decisions.contains('JDBC')
        decisions.contains('Hikari')
        decisions.contains('v1_*')
        decisions.contains('GORM')
        decisions.contains('dbCreate: none')
        decisions.contains('in-memory H2')
        decisions.contains('JDK 27')
        decisions.contains('java@27.0.0')
        decisions.contains('fail-closed')
        decisions.contains('./gradlew --no-daemon test')
        decisions.contains('spotbugsMain')
        decisions.contains('dependencyAudit')
        decisions.contains('codenarcMain')
        decisions.contains('gitleaks detect --source . --verbose')
        decisions.contains('groovyOptions.configurationScript')
        decisions.contains('daemon thread')
        memory.contains('DECISIONS.md')
        memory.contains('JDBC')
        memory.contains('Hikari')
        memory.contains('GORM')
        memory.contains('dbCreate: none')
        memory.contains('JDK 27')
        memory.contains('java@27.0.0')
        memory.contains('gitleaks detect --source . --verbose')
        memory.contains('./scripts/gradle-java27')
    }

    void 'README names resolved Groovy, Grails, JDK, and the gate packages'() {
        given:
        String readme = file('README.md').text
        String grailsVersion = (file('gradle.properties').text =~ /(?m)^grailsVersion=(\S+)/)[0][1]
        String javaPin = (file('mise.toml').text =~ /(?m)^java = "([^"]+)"/)[0][1]
        String groovyVersion = GroovySystem.version
        String origin = GroovySystem.class.protectionDomain?.codeSource?.location?.toString() ?: ''

        expect:
        grailsVersion == '7.2.3'
        readme.contains(grailsVersion)
        readme.contains(javaPin)
        readme.contains('JDK 27')
        readme.contains("Groovy ${groovyVersion}")
        readme.contains('org.apache.groovy:groovy')
        !groovyVersion.isEmpty()
        !origin || origin.contains(groovyVersion)
        readme.contains('SpotBugs')
        readme.contains('FindSecBugs')
        readme.contains('CodeNarc')
        readme.contains('CycloneDX')
        readme.contains('osv-scanner')
        readme.contains('gitleaks')
        !readme.toLowerCase().contains('crac')
    }

    void 'agent docs do not carry private paths, tailnet hosts, or real credentials'() {
        expect:
        ['AGENTS.md', 'README.md', 'DECISIONS.md', 'MEMORY.md'].each { String path ->
            assertNoPrivateData(file(path).text, path)
        }
    }

    private void assertNoPrivateData(String text, String path) {
        assert !text.contains('zebra-hydra'), path
        assert !text.contains('/home/barry'), path
        assert !text.contains('.ts.net'), path
        assert !(text =~ /ghp_[A-Za-z0-9]/), path
        assert !(text =~ /github_pat_[A-Za-z0-9]/), path
        assert !(text =~ /AKIA[0-9A-Z]{16}/), path
        assert !(text =~ /-----BEGIN [A-Z ]*PRIVATE KEY-----/), path
        assert !(text =~ /(?i)bearer\s+(?!\{)(?!dev\b)[A-Za-z0-9_\-.]{16,}/), path
    }

    private File file(String path) {
        new File(System.getProperty('user.dir'), path)
    }
}
