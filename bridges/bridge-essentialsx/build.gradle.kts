val paperApiVersion: String by rootProject.extra
val junitVersion: String by rootProject.extra
val assertjVersion: String by rootProject.extra

description = "Native EssentialsX economy bridge for Conduit."

dependencies {
    compileOnly(project(":conduit-api"))
    compileOnly("io.papermc.paper:paper-api:$paperApiVersion")
    compileOnly("net.essentialsx:EssentialsX:2.21.2")

    testImplementation(project(":conduit-api"))
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
