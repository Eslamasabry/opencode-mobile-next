// Versions mirror native/gradle/libs.versions.toml (Kotlin 2.4.21, OkHttp 5.5.1, Ktor 3.6.1, coroutines 1.11.0).
// Spike-only additions (catalog requests in docs/native/units/S0-NETP.md): okhttp-tls, mockwebserver3-junit5,
// ktor-websockets, conscrypt-openjdk-uber.
plugins {
    kotlin("jvm") version "2.4.21"
}

val okhttp = "5.5.1"
val ktor = "3.6.1"

dependencies {
    implementation("com.squareup.okhttp3:okhttp:$okhttp")
    implementation("io.ktor:ktor-client-core:$ktor")
    implementation("io.ktor:ktor-client-okhttp:$ktor")
    implementation("io.ktor:ktor-websockets:$ktor")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation("org.conscrypt:conscrypt-openjdk-uber:2.5.2")

    testImplementation(kotlin("test-junit5"))
    testImplementation("org.junit.jupiter:junit-jupiter-params")
    testImplementation("com.squareup.okhttp3:mockwebserver3:$okhttp")
    testImplementation("com.squareup.okhttp3:okhttp-tls:$okhttp")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
}

kotlin { jvmToolchain(21) }

tasks.test {
    useJUnitPlatform { excludeTags("live") }
    testLogging { events("passed", "failed", "skipped"); showStandardStreams = true; exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
}

// Live proofs against a scratch OpenCode 2 / Paseo on localhost. Never part of `test`.
// Env: NETP_OC2_URL, NETP_OC2_URL_NEXT (2.2.5), NETP_PASEO_URL, NETP_PASSWORD_FILE (a path; the secret is never on argv).
tasks.register<Test>("liveTest") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("live") }
    testLogging { events("passed", "failed", "skipped"); showStandardStreams = true; exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
    outputs.upToDateWhen { false }
}
