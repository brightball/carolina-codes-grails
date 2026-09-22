ruleset {
    description 'Application Groovy style for the Carolina Grails API'

    ruleset('rulesets/basic.xml') {
        'EmptyMethod' {
            enabled = false
        }
    }
    ruleset('rulesets/braces.xml')
    ruleset('rulesets/concurrency.xml')
    ruleset('rulesets/exceptions.xml') {
        'CatchException' {
            enabled = false
        }
        'CatchThrowable' {
            enabled = false
        }
    }
    ruleset('rulesets/grails.xml') {
        'GrailsStatelessService' {
            enabled = false
        }
        'GrailsPublicControllerMethod' {
            enabled = false
        }
        'GrailsMassAssignment' {
            enabled = false
        }
    }
    ruleset('rulesets/imports.xml')
    ruleset('rulesets/naming.xml') {
        'FactoryMethodName' {
            enabled = false
        }
        'ConfusingMethodName' {
            enabled = false
        }
    }
    ruleset('rulesets/security.xml')
    ruleset('rulesets/unused.xml')
}
