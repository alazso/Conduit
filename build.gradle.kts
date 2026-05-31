plugins {
    java
    jacoco
}

val paperApiVersion = "26.1.2.build.66-stable"
val annotationsVersion = "24.1.0"
val junitVersion = "5.11.3"
val assertjVersion = "3.26.3"
val faststatsVersion = "0.23.0"
val playerPointsVersion = "3.3.3"

// Expose shared versions to subprojects via extra properties.
extra["paperApiVersion"] = paperApiVersion
extra["annotationsVersion"] = annotationsVersion
extra["junitVersion"] = junitVersion
extra["assertjVersion"] = assertjVersion
extra["faststatsVersion"] = faststatsVersion
extra["playerPointsVersion"] = playerPointsVersion

allprojects {
    group = rootProject.group
    version = rootProject.version
}

// The root project is an aggregator with no sources; suppress its empty jar so
// it is not mistaken for a deliverable.
tasks.named<Jar>("jar") {
    enabled = false
}

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "jacoco")

    java {
        toolchain {
            // Minecraft/Paper 26.1+ require Java 25 (year.drop.hotfix versioning).
            languageVersion.set(JavaLanguageVersion.of(25))
        }
        withSourcesJar()
        withJavadocJar()
    }

    dependencies {
        "compileOnly"("org.jetbrains:annotations:$annotationsVersion")
        "testCompileOnly"("org.jetbrains:annotations:$annotationsVersion")
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.compilerArgs.add("-Xlint:all,-processing,-serial")
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
        }
        finalizedBy(tasks.withType<JacocoReport>())
    }

    tasks.withType<JacocoReport>().configureEach {
        dependsOn(tasks.withType<Test>())
        reports {
            xml.required.set(true)
            html.required.set(true)
        }
    }

    tasks.withType<Javadoc>().configureEach {
        (options as StandardJavadocDocletOptions).apply {
            addStringOption("Xdoclint:none", "-quiet")
            encoding = "UTF-8"
        }
    }

    tasks.withType<Jar>().configureEach {
        from(rootProject.file("LICENSE")) {
            into("META-INF")
        }
    }
}

// Runtime-only classes need a live Paper/Folia server and are excluded from the
// coverage gate; the unit-testable runtime core is held to a high bar.
val coverageExclusions = listOf(
    "so/alaz/conduit/core/ConduitPlugin.class",
    "so/alaz/conduit/core/ConduitPlugin\$*.class",
    "so/alaz/conduit/core/ConduitBootstrapper.class",
    "so/alaz/conduit/core/ConduitLoader.class",
    "so/alaz/conduit/core/scheduler/SchedulerAdapterImpl*.class",
    "so/alaz/conduit/core/events/BukkitEventPublisher*.class",
    "so/alaz/conduit/core/metrics/**",
    "so/alaz/conduit/core/papi/**",
    "so/alaz/conduit/core/command/**",
    "so/alaz/conduit/core/update/UpdateChecker*.class",
)

project(":conduit-core") {
    tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
        dependsOn(tasks.named("test"))
        classDirectories.setFrom(
            files(classDirectories.files.map { dir -> fileTree(dir) { exclude(coverageExclusions) } })
        )
        violationRules {
            rule {
                limit {
                    counter = "INSTRUCTION"
                    minimum = "0.80".toBigDecimal()
                }
            }
        }
    }
    tasks.named("check") {
        dependsOn(tasks.named("jacocoTestCoverageVerification"))
    }
}
