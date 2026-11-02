package oc.buildlogic

internal object ExternalRules {
    /** group (or group:artifact) → module paths that may declare it. Test configurations are exempt for "-mock" and "test" artifacts. */
    val allowed: Map<String, (String) -> Boolean> =
        mapOf(
            "org.jetbrains.compose.material3:material3" to { p -> p in setOf(":ui:kit", ":ui:kit-chat", ":ui:kit-content") },
            "org.jetbrains.compose.material3.adaptive" to { p -> p.startsWith(":ui:kit") || p == ":ui:shell" },
            "io.ktor" to { p ->
                p == ":core:net" || p.startsWith(":protocol:") || p == ":core:testing" || p == ":androidApp" ||
                    p == ":desktopApp"
            },
            "com.squareup.okhttp3" to { p -> p == ":core:net-okhttp" },
            "androidx.room3" to { p -> p == ":core:db" },
            "androidx.sqlite" to { p -> p == ":core:db" },
            "co.touchlab" to { p -> p == ":core:common" }, // Kermit stays behind AppLog (plan 03 §18)
            "com.github.termux.termux-app" to { p -> p == ":android:terminal" || p == ":ui:kit-content" },
            // :android:camera holds the CameraX + zxing-cpp adapters (QR-CAMERA §6); the app wiring renders its preview.
            "androidx.camera" to { p -> p == ":ui:kit-content" || p == ":android:media" || p == ":android:camera" || p == ":androidApp" },
            "io.github.zxing-cpp" to { p -> p == ":ui:kit-content" || p == ":android:media" || p == ":android:camera" },
            "com.k2fsa.sherpa.onnx" to { p -> p == ":android:media" },
            "org.jetbrains:markdown" to { p -> p == ":core:markdown" },
        )

    /** null = allowed. */
    fun violation(from: String, group: String?, name: String, inTest: Boolean): String? {
        val key = allowed.keys.firstOrNull { k -> "$group:$name" == k || group == k } ?: return null
        val isTestTool = inTest && (name.endsWith("-mock") || name.contains("test"))
        if (isTestTool || allowed.getValue(key)(from)) return null
        return "Module rule: $from may not declare $group:$name (ADR §6.1, plan 03 §19.4). " +
            "Use the kit / :core:net / the owning module instead."
    }
}
