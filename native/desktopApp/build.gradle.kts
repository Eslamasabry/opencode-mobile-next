plugins { id("oc.desktop.application") }

dependencies {
    listOf(
        ":core:common", ":core:security", ":core:model", ":core:domain",
        ":engine:connection", ":engine:transcript",
        ":ui:design", ":ui:words", ":ui:routes", ":ui:kit", ":ui:shell",
        ":feature:chats", ":feature:projects",
    ).forEach { implementation(project(it)) }
    implementation(libs.compose.ui) // CMP 1.13 ships one desktop artifact for every OS (currentOs is deprecated)
    implementation(libs.compose.foundation)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.metrox.viewmodel.compose)
}

compose.desktop.application { mainClass = "io.github.eslamasabry.oc.desktop.MainKt" }
