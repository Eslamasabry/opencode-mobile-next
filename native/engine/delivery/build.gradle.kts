plugins { id("oc.kmp.library") }

// :engine:delivery (plan 09 §4, plan 04 §13, plan 15 S2-DLV, roadmap C15-1). Bubble-first send, the steer table, Stop,
// the live line, and the offline queue with dispatch markers and command receipts, reading Flutter's `oc.offlineQueue`
// and `oc.pendingCommands.<profileId>` unchanged. Engine #8 in ADR §6.2: it may use the engines above it, but in this
// slice it reads the connection and the transcript through the ports in `port/`, bound by the app graph, so it compiles
// on core modules alone.
kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":core:common"))
        implementation(project(":core:model"))
        implementation(project(":core:domain"))
        api(project(":core:data")) // DeliveryStorage exposes the adopted store and writer lanes
    }
    sourceSets.getByName("desktopTest").dependencies {
        implementation(kotlin("test-junit"))
    }
}

// The real Flutter blobs (native/tools/flutter-migration-readers fixture and this module's own fixture) are read on the JVM.
val repoRoot: String = rootProject.layout.projectDirectory.asFile.parentFile.absolutePath
tasks.withType<Test>().configureEach {
    systemProperty("oc.repo.root", repoRoot)
}
