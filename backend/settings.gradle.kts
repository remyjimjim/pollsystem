// Auto-resolves and (if needed) downloads JDK 17 for the toolchain declared in
// build.gradle.kts. Without this plugin, Gradle would error out when it can't
// detect a local JDK 17 install instead of fetching one.
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "pollsystem"
