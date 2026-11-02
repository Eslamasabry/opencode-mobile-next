plugins { id("oc.kmp.library") }

// :engine:files (P12-FILES): the project file tree, the file preview rules, the never-share-secrets guard and the
// Wi-Fi share engine (ports of lib/ui/screens/files*, lib/platform/wifi_file_share.dart and
// lib/domain/file_share_gateway.dart). No UI and no platform code: the socket server, Open with and PDF pages are
// ports the Android app binds (androidApp graph/FilesWiring.kt).
kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":core:common"))
        api(project(":core:model"))
        api(project(":core:domain")) // FileGateway is in the public API
    }
}
