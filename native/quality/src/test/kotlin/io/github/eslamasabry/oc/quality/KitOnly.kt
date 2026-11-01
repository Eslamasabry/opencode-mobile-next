package io.github.eslamasabry.oc.quality

/** K1 (plan 03 §19.4): the kit-only import allowlist for feature files, as a pure function so P9 can test it. */
object KitOnly {
    private val allowed =
        listOf(
            "androidx.compose.runtime.",
            "androidx.compose.foundation.layout.",
            "androidx.compose.foundation.lazy.",
            "androidx.compose.foundation.rememberScrollState", "androidx.compose.foundation.ScrollState",
            "androidx.compose.foundation.verticalScroll", "androidx.compose.foundation.horizontalScroll",
            "androidx.compose.foundation.text.input.TextFieldState",
            "androidx.compose.foundation.text.input.rememberTextFieldState",
            "androidx.compose.ui.Modifier", "androidx.compose.ui.Alignment",
            "androidx.compose.ui.unit.", "androidx.compose.ui.semantics.", "androidx.compose.ui.focus.",
            "androidx.compose.ui.input.key.",
            "androidx.compose.ui.platform.LocalLayoutDirection", "androidx.compose.ui.platform.LocalDensity",
            "androidx.compose.ui.platform.LocalFocusManager", "androidx.compose.ui.platform.testTag",
            "androidx.lifecycle.", "dev.zacsweers.metro.", "dev.zacsweers.metrox.viewmodel.",
            "org.jetbrains.compose.resources.stringResource", "org.jetbrains.compose.resources.pluralStringResource",
            "io.github.eslamasabry.oc.ui.kit.", "io.github.eslamasabry.oc.ui.kitchat.",
            "io.github.eslamasabry.oc.ui.kitcontent.", "io.github.eslamasabry.oc.ui.design.",
            "io.github.eslamasabry.oc.ui.words.", "io.github.eslamasabry.oc.ui.routes.",
            "io.github.eslamasabry.oc.engine.", "io.github.eslamasabry.oc.core.domain.",
            "io.github.eslamasabry.oc.core.model.", "io.github.eslamasabry.oc.core.common.",
            "kotlinx.coroutines.flow.", "kotlinx.coroutines.launch", "kotlinx.coroutines.CoroutineScope",
            "kotlinx.collections.immutable.", "kotlinx.serialization.", "kotlin.",
        )
    private val onlyInEntries = listOf("androidx.navigation3.runtime.")
    private val onlyInPreviews = listOf("androidx.compose.ui.tooling.preview.")
    private val bannedInside =
        listOf("io.github.eslamasabry.oc.ui.kit.platform.", "io.github.eslamasabry.oc.core.common.AppDispatchers")

    /** Imports of one feature file that break K1; empty = the file is fine. */
    fun violations(packageName: String?, fileName: String, imports: List<String>): List<String> {
        val own = packageName?.split('.')?.take(6)?.joinToString(".")
        return imports.filterNot { n ->
            when {
                bannedInside.any { n.startsWith(it) } -> false
                onlyInEntries.any { n.startsWith(it) } -> fileName.endsWith("Entries")
                onlyInPreviews.any { n.startsWith(it) } -> fileName.endsWith("Previews")
                own != null && n.startsWith(own) -> true
                else -> allowed.any { n.startsWith(it) }
            }
        }
    }
}
