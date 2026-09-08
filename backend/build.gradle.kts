import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.springframework.boot") version "3.3.5"
    id("io.spring.dependency-management") version "1.1.6"
    kotlin("jvm") version "1.9.25"
    kotlin("plugin.spring") version "1.9.25"
    kotlin("plugin.jpa") version "1.9.25"
}

group = "org.kodewerks.pollsystem"
version = "0.0.1-SNAPSHOT"

// Override the Testcontainers version managed by the Spring Boot BOM
// (Boot 3.3.5 pins 1.19.8). 1.19.x bundles a docker-java too old for
// Docker Engine 29 (MinAPIVersion 1.40) — its client is rejected with HTTP
// 400 and Testcontainers reports "Could not find a valid Docker environment".
// 1.20.x ships docker-java 3.4, which negotiates the newer API and reads the
// active docker context (so Docker Desktop's socket is found without a
// /var/run/docker.sock symlink).
extra["testcontainers.version"] = "1.20.6"

java {
    // Gradle provisions a JDK 17 to compile and run the build, regardless of
    // which JVM launched the wrapper. With the foojay-resolver plugin in
    // settings.gradle.kts, Gradle auto-downloads JDK 17 from Foojay if no
    // suitable installation is detected locally.
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-mail")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Hot reload during local dev. The `developmentOnly` configuration
    // keeps devtools out of production builds. Recompiling Kotlin (IDE
    // auto-build, or `./gradlew compileKotlin` in a second terminal)
    // triggers a fast restart of the application context.
    developmentOnly("org.springframework.boot:spring-boot-devtools")

    // Kotlin
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // Database
    runtimeOnly("org.postgresql:postgresql")

    // Database Migration
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")

    // JWT for authentication
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

    // Stripe SDK — used server-side to create Checkout + Customer Portal
    // sessions (the purchase flow). The webhook handler stays SDK-free and
    // verifies signatures by hand; this is the one place we call Stripe's API,
    // gated behind app.stripe.api-key.
    implementation("com.stripe:stripe-java:33.4.0")

    // In-process cache for role_assignments authorization lookups —
    // see RoleAuthCache. We pull Caffeine directly rather than enabling
    // Spring's @Cacheable abstraction so eviction stays explicit at the
    // write sites instead of hiding behind annotations.
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")

    // Actuator surface — currently used only to expose the RoleAuthCache
    // stats endpoint at /actuator/rolecache. Lock-down lives in
    // SecurityConfig (hasRole SUPER) + application.yml exposure include.
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("com.fasterxml.jackson.module:jackson-module-kotlin")
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs += "-Xjsr305=strict"
        jvmTarget = "17"
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    // Testcontainers talks to Docker via docker-java, which defaults to API
    // version 1.32. Docker Engine 29 enforces MinAPIVersion 1.40 and rejects
    // 1.32 ("client version is too old"), which surfaces as Testcontainers'
    // "Could not find a valid Docker environment". Pin a version supported
    // across Docker 20.10–29. Overridable via the DOCKER_API_VERSION env var.
    val dockerApiVersion = System.getenv("DOCKER_API_VERSION") ?: "1.41"
    environment("DOCKER_API_VERSION", dockerApiVersion)
    // docker-java (used by Testcontainers) resolves its API version from the
    // `api.version` system property before the env var, so set both.
    systemProperty("api.version", dockerApiVersion)
    // DOCKER_HOST: honor an explicit value; otherwise, on Docker Desktop for
    // Linux (no /var/run/docker.sock), fall back to the RAW engine socket. The
    // default ~/.docker/desktop/docker.sock is a proxy that rejects the Java
    // client with HTTP 400; docker.raw.sock is the real engine socket. This
    // keeps `./gradlew test` turnkey without the developer exporting DOCKER_HOST.
    val dockerHost = System.getenv("DOCKER_HOST") ?: run {
        val rawSock = file("${System.getProperty("user.home")}/.docker/desktop/docker.raw.sock")
        if (!file("/var/run/docker.sock").exists() && rawSock.exists()) "unix://$rawSock" else null
    }
    dockerHost?.let { environment("DOCKER_HOST", it) }
    // Ryuk (Testcontainers' reaper) fails to start on Docker Desktop for Linux
    // (its socket bind-mount dies → "404 No such container"). AbstractIntegrationTest
    // already relies on the JVM shutdown hook to stop its singleton container, so
    // Ryuk is redundant here. Overridable via TESTCONTAINERS_RYUK_DISABLED.
    environment("TESTCONTAINERS_RYUK_DISABLED", System.getenv("TESTCONTAINERS_RYUK_DISABLED") ?: "true")
}
