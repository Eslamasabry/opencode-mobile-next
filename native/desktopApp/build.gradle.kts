plugins { id("oc.desktop.application") }

dependencies {
    listOf(
        ":core:common", ":core:security", ":core:model", ":core:domain",
        ":engine:connection", ":engine:transcript",
        ":ui:design", ":ui:words", ":ui:routes", ":ui:kit", ":ui:shell",
        ":feature:chats", ":feature:projects", ":feature:desktopupdates", ":engine:updates",
        ":ui:kit-chat",
        ":engine:agents", // dev gallery: the canned conversation feed (dev/DevGallery.kt)
    ).forEach { implementation(project(it)) }
    implementation(libs.compose.ui) // CMP 1.13 ships one desktop artifact for every OS (currentOs is deprecated)
    implementation(libs.compose.foundation)
    implementation(libs.compose.resources)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.metrox.viewmodel.compose)
}

compose.desktop.application { mainClass = "io.github.eslamasabry.oc.desktop.MainKt" }

// Dev loop (tool/dev/README.md): `./gradlew :desktopApp:hotRunGallery -Poc.hotReload=true` opens the dev gallery (real
// screens, canned data) under Compose Hot Reload; saved edits in any Compose module redraw it in place.
if (providers.gradleProperty("oc.hotReload").orNull == "true") {
    tasks.register<org.jetbrains.compose.reload.gradle.ComposeHotRun>("hotRunGallery") {
        group = "dev loop"
        description = "Dev gallery under Compose Hot Reload (tool/dev/README.md)."
        mainClass.set("io.github.eslamasabry.oc.desktop.dev.DevGalleryKt")
        compilation.set(kotlin.target.compilations.getByName("main"))
        providers.gradleProperty("oc.galleryScene").orNull?.let { args(it) }
    }
}
