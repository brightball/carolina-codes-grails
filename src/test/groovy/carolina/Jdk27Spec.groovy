package carolina

import spock.lang.Specification

class Jdk27Spec extends Specification {

    void 'build, wrapper helper, Docker, Gitea, mise, and docs pin JDK 27 not 21'() {
        expect:
        file('build.gradle').text.contains('options.release = 27')
        file('build.gradle').text.contains("sourceCompatibility = '27'")
        !file('build.gradle').text.contains('options.release = 21')
        !file('build.gradle').text.contains("sourceCompatibility = '21'")
        file('gradle/wrapper/gradle-wrapper.properties').text.contains('gradle-9.8.0-rc-1')
        !file('gradle/wrapper/gradle-wrapper.properties').text.contains('gradle-8.14.5')

        file('mise.toml').text.contains('java = "27.0.0"')
        !file('mise.toml').text.contains('21.0.2')

        String docker = file('Dockerfile').text
        docker.contains('openjdk:27-rc-bookworm') || docker.contains('openjdk:27')
        docker.contains('27')
        !docker.contains('temurin:21')
        !docker.contains(':21-jdk')
        !docker.contains(':21-jre')

        String workflow = file('.gitea/workflows/ci.yml').text
        workflow.contains('openjdk:27-rc-bookworm')
        !workflow.contains('temurin:21')
        !workflow.contains(':21-jdk')

        String helper = file('scripts/gradle-java27').text
        helper.contains('version "27([".]|$)')
        helper.contains('java@27')
        !helper.contains('version "21')
        !helper.contains('java@21')
        !file('scripts/gradle-java21').exists()

        file('README.md').text.contains('java@27.0.0')
        file('README.md').text.contains('gradle-java27')
        !file('README.md').text.contains('java@21.0.2')
        !file('README.md').text.contains('gradle-java21')

        file('AGENTS.md').text.contains('JDK 27')
        file('AGENTS.md').text.contains('gradle-java27')
    }

    void 'runtime under test is JDK 27'() {
        expect:
        System.getProperty('java.specification.version') == '27'
    }

    void 'gradle-java27 helper accepts JAVA_HOME of this JDK 27 without mise on PATH'() {
        given:
        File helper = file('scripts/gradle-java27')
        String javaHome = jdkHome()
        String javaVersion = versionOf(javaHome)
        assert javaVersion.contains('version "27'), "test JVM is not JDK 27: ${javaVersion}"

        when:
        ProcessBuilder pb = new ProcessBuilder('bash', helper.absolutePath, '--version')
        pb.directory(file('.'))
        pb.redirectErrorStream(true)
        Map env = pb.environment()
        env.put('JAVA_HOME', javaHome)
        env.put('PATH', '/usr/bin:/bin')
        Process proc = pb.start()
        String output = proc.inputStream.getText('UTF-8')
        int code = proc.waitFor()

        then:
        code == 0
        !output.contains('Java 27 is required')
        output.contains('Gradle')
        output.contains('Launcher JVM:  27')
    }

    private String jdkHome() {
        String home = System.getProperty('java.home')
        if (new File(home, 'bin/java').canExecute()) {
            return home
        }
        new File(home).parentFile.absolutePath
    }

    private String versionOf(String javaHome) {
        Process proc = new ProcessBuilder("${javaHome}/bin/java", '-version')
            .redirectErrorStream(true)
            .start()
        proc.waitFor()
        proc.inputStream.getText('UTF-8')
    }

    private File file(String path) {
        new File(System.getProperty('user.dir'), path)
    }
}
