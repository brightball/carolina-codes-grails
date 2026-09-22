package carolina

import spock.lang.Specification

class QualityGatesSpec extends Specification {

    static final List HOOK_IDS = ['test', 'sast', 'audit', 'gitleaks', 'style']
    static final List JOB_NAMES = ['test', 'sast', 'audit', 'gitleaks', 'style']
    static final String CLONE_CMD = 'git clone --depth 1 --no-checkout "https://x-access-token:${token}@${host}/${GITHUB_REPOSITORY}" .'

    void 'pre-commit config has five independent hooks, one per check'() {
        given:
        String text = file('.pre-commit-config.yaml').text
        List ids = hookIds(text)

        expect:
        ids == HOOK_IDS
        text.contains('entry: ./scripts/gradle-java27 test')
        text.contains('entry: ./scripts/gradle-java27 spotbugsMain')
        text.contains('entry: ./scripts/gradle-java27 dependencyAudit')
        text.contains('entry: ./scripts/gitleaks-detect')
        file('scripts/gitleaks-detect').text.contains('gitleaks detect --source . --verbose')
        text.contains('entry: ./scripts/gradle-java27 codenarcMain')
        !text.contains('id: run-all')
        text.contains('SKIP=test,sast,audit,gitleaks,style')
    }

    void 'Gitea workflow has one parallel job per check and no needs between them'() {
        given:
        String workflow = file('.gitea/workflows/ci.yml').text
        Map jobs = workflowJobs(workflow)

        expect:
        jobs.keySet().toList() == JOB_NAMES
        jobs.every { name, body -> !body.contains('needs:') }
        !(workflow =~ /(?m)^\s*git init\b/)
        !workflow.contains('init.defaultBranch')
        workflow.contains('GITHUB_TOKEN: ${{ github.token }}')
        !workflow.contains('./gradlew --no-daemon check')
        !workflow.contains('pre-commit run --all-files')
    }

    void 'each Gitea job clones the SHA with the job token and runs only its check'() {
        given:
        Map jobs = workflowJobs(file('.gitea/workflows/ci.yml').text)

        expect:
        jobs.each { name, body ->
            assert body.contains(CLONE_CMD), "${name} must clone without git init"
            assert body.contains('git fetch --depth 1 origin "${GITHUB_SHA}"'), "${name} must fetch GITHUB_SHA"
            assert body.contains('missing job token for git fetch'), "${name} must fail closed without a token"
        }
        jobs.test.contains('./gradlew --no-daemon test')
        !jobs.test.contains('spotbugsMain')
        !jobs.test.contains('dependencyAudit')
        !jobs.test.contains('codenarcMain')
        !jobs.test.contains('gitleaks detect')

        jobs.sast.contains('./gradlew --no-daemon spotbugsMain')
        !jobs.sast.contains('./gradlew --no-daemon test')
        !jobs.sast.contains('dependencyAudit')
        !jobs.sast.contains('codenarcMain')
        !jobs.sast.contains('gitleaks detect')

        jobs.audit.contains('./gradlew --no-daemon dependencyAudit')
        !jobs.audit.contains('./gradlew --no-daemon test')
        !jobs.audit.contains('spotbugsMain')
        !jobs.audit.contains('codenarcMain')
        !jobs.audit.contains('gitleaks detect')

        jobs.gitleaks.contains('gitleaks detect --source . --verbose')
        !jobs.gitleaks.contains('./gradlew')
        !jobs.gitleaks.contains('spotbugsMain')
        !jobs.gitleaks.contains('dependencyAudit')
        !jobs.gitleaks.contains('codenarcMain')

        jobs.style.contains('./gradlew --no-daemon codenarcMain')
        !jobs.style.contains('./gradlew --no-daemon test')
        !jobs.style.contains('spotbugsMain')
        !jobs.style.contains('dependencyAudit')
        !jobs.style.contains('gitleaks detect')
    }

    void 'Gradle build wires fail-closed CodeNarc, SpotBugs FindSecBugs, and OSV audit'() {
        given:
        String gradle = file('build.gradle').text

        expect:
        gradle.contains('id "codenarc"')
        gradle.contains('id "com.github.spotbugs"')
        gradle.contains('tasks.register("dependencyAudit")')
        gradle.contains('findsecbugs-plugin')
        gradle.contains('ignoreFailures = false')
        gradle.contains('osv-scanner')
        gradle.contains('writeDependencyBom')
        gradle.contains('maxPriority1Violations = 0')
        !gradle.contains('ignoreFailures = true')
        file('config/codenarc/codenarc.groovy').exists()
        file('config/spotbugs/include.xml').exists()
        file('scripts/gradle-java27').exists()
        file('scripts/gitleaks-detect').exists()
    }

    private File file(String path) {
        new File(System.getProperty('user.dir'), path)
    }

    private List hookIds(String text) {
        (text =~ /(?m)^\s+- id: (\S+)/).collect { it[1] }
    }

    private Map workflowJobs(String workflow) {
        def matcher = workflow =~ /(?ms)^jobs:\n(.*)/
        assert matcher.find(): 'workflow has no jobs: block'
        String body = matcher.group(1)
        def names = (body =~ /(?m)^  ([a-z0-9-]+):\s*$/).collect { it[1] }
        Map out = [:]
        names.eachWithIndex { String name, int i ->
            int start = body.indexOf("\n  ${name}:")
            if (start < 0) {
                start = body.startsWith("  ${name}:") ? 0 : -1
            }
            assert start >= 0: "missing job ${name}"
            int end = i + 1 < names.size() ? body.indexOf("\n  ${names[i + 1]}:") : body.length()
            out[name] = body.substring(start, end)
        }
        out
    }
}
