val paperApiVersion: String by rootProject.extra
val junitVersion: String by rootProject.extra
val assertjVersion: String by rootProject.extra

description = "Conduit runtime — provider registry, dispatch, events, commands, ops."

dependencies {
    api(project(":conduit-api"))

    compileOnly("io.papermc.paper:paper-api:$paperApiVersion")
    // Optional soft dependencies — guarded at runtime via class presence checks.
    compileOnly("me.clip:placeholderapi:2.11.6")
    compileOnly("org.bstats:bstats-bukkit:3.0.2")

    testImplementation("io.papermc.paper:paper-api:$paperApiVersion")
    testImplementation(project(":conduit-test-fixtures"))
    testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion")
    testImplementation("org.assertj:assertj-core:$assertjVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.named<ProcessResources>("processResources") {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filesMatching("paper-plugin.yml") {
        expand(props)
    }
}

// Conduit ships as a single installable plugin jar. Paper isolates plugin
// classloaders, so the API classes the runtime (and dependent bridges) load must
// live inside this jar — bundle conduit-api's compiled output here.
val conduitApiJar = project(":conduit-api").tasks.named<Jar>("jar")

tasks.named<Jar>("jar") {
    dependsOn(conduitApiJar)
    from(conduitApiJar.flatMap { it.archiveFile }.map { zipTree(it) }) {
        // Keep only the API classes; this jar provides its own manifest/LICENSE.
        exclude("META-INF/**")
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
