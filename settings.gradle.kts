rootProject.name = "conduit"

plugins {
    // Enables automatic provisioning of the Java 25 toolchain when it is not
    // installed locally.
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/") { name = "papermc" }
        maven("https://repo.extendedclip.com/content/repositories/placeholderapi/") { name = "placeholderapi" }
        maven("https://repo.essentialsx.net/releases/") { name = "essentialsx" }
        maven("https://repo.faststats.dev/releases") { name = "faststats" }
        maven("https://repo.rosewooddev.io/repository/public/") { name = "rosewood" }
    }
}

include(
    "conduit-api",
    "conduit-core",
    "conduit-test-fixtures",
    "bridges:bridge-essentialsx",
    "bridges:bridge-playerpoints",
    "bridges:bridge-template",
    "examples:conduit-economy",
    "examples:conduit-enderfee",
    "examples:conduit-points",
    "examples:conduit-shop",
)
