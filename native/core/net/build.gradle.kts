plugins { id("oc.kmp.library") }

// TODO(catalog): the catalog pins `ktor` but has no library aliases yet (S1-NET report, "catalog
// requests").
// Until the build-logic owner adds `ktor-client-core`, `ktor-client-okhttp` and `ktor-client-mock`,
// the coordinates
// are spelled here with the catalog's own version, so no second version exists anywhere.
val ktor: String = libs.versions.ktor.get()

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":core:common"))
        implementation(project(":core:security"))
        implementation(project(":core:model"))
        api("io.ktor:ktor-client-core:$ktor")
        api("io.ktor:ktor-websockets:$ktor")
        api("io.ktor:ktor-client-websockets:$ktor")
    }
    sourceSets.commonTest.dependencies {
        implementation("io.ktor:ktor-client-mock:$ktor")
    }
    // The OkHttp engine binding is the same code for Android and desktop. It lives once in
    // src/jvmShared and is
    // compiled into both JVM-family source sets (no custom hierarchy, so the default template stays
    // applied).
    sourceSets.androidMain {
        kotlin.srcDir("src/jvmShared/kotlin")
        dependencies {
            api("io.ktor:ktor-client-okhttp:$ktor")
            implementation("io.ktor:ktor-websockets:$ktor")
        }
        dependencies {
            api("io.ktor:ktor-client-okhttp:$ktor")
            implementation(project(":core:net-okhttp"))
        }
    }
    sourceSets.getByName("desktopMain") {
        kotlin.srcDir("src/jvmShared/kotlin")
        dependencies {
            api("io.ktor:ktor-client-okhttp:$ktor")
            implementation("io.ktor:ktor-websockets:$ktor")
        }
        dependencies {
            api("io.ktor:ktor-client-okhttp:$ktor")
            implementation(project(":core:net-okhttp"))
        }
    }
}
