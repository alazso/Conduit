val paperApiVersion: String by rootProject.extra

description = "Example Conduit consumer plugin that charges a fee for an in-game action."

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
