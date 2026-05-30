val paperApiVersion: String by rootProject.extra

description = "Case B example: a shop that lets a buyer choose among all registered Conduit economies."

dependencies {
    compileOnly(project(":conduit-api"))
    compileOnly("io.papermc.paper:paper-api:$paperApiVersion")
}

tasks.named<ProcessResources>("processResources") {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filesMatching("paper-plugin.yml") {
        expand(props)
    }
}
