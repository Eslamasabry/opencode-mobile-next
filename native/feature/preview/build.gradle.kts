// :feature:preview (unit PREVIEW-QR): the in-app preview of a dev server and "Preview a running
// app". The web view
// itself is a platform slot (WebPreviewSlot): Android binds :android:webview in the app wiring,
// desktop says plainly
// that it cannot show the page here.
plugins { id("oc.kmp.feature") }

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":ui:kit-chat"))
        implementation(project(":engine:connection"))
    }
}
