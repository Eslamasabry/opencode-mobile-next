package io.github.eslamasabry.oc.quality.sec

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Owner decision 2026-11-07: no Termux app in native (see [NoTermuxApp]). */
class NoTermuxAppTest {
    @Test
    fun `B-SEC-194 no Termux module, no Termux run-command permission and no Termux words except the notice`() {
        val modules = NoTermuxApp.modules(Live.root)
        assertTrue(":androidApp" in modules && ":android:terminal" in modules, "the scan saw $modules")
        assertEquals(emptyList(), NoTermuxApp.check(Live.root))
    }

    @Test
    fun `B-SEC-194 a planted Termux module, permission, bridge call or string fails, the notice and terminal pass`() {
        val t =
            Planted()
                .file("engine/termuxhost/build.gradle.kts", "plugins { }")
                .file("androidApp/build.gradle.kts", "dependencies { implementation(project(\":android:termux\")) }")
                .file(
                    "androidApp/src/main/AndroidManifest.xml",
                    "<uses-permission android:name=\"com.termux.permission.RUN_COMMAND\" />",
                )
                .file("android/x/src/main/kotlin/Run.kt", "val a = \"com.termux.RUN_COMMAND_PATH\"")
                .file(
                    "ui/words/src/commonMain/composeResources/values-es/strings_x.xml",
                    "<resources><string name=\"setupTermux\">Configurar Termux</string></resources>",
                )
                .file(
                    "androidApp/src/main/res/values/strings_n.xml",
                    "<resources><string name=\"andr_termuxRetiredNotice\">Termux is no longer supported.</string>" +
                        "<string name=\"andr_termuxRetiredConnectComputer\">Connect a computer</string></resources>",
                )
                .file("android/terminal/build.gradle.kts", "plugins { }")
                .file(
                    "android/terminal/src/main/java/com/termux/terminal/TerminalSession.java",
                    "package com.termux.terminal;",
                )
        val found = NoTermuxApp.check(t.root).map { it.id to it.where.substringBefore(':') }
        assertEquals(
            listOf(
                "TERMUX-MODULE" to "",
                "TERMUX-MODULE" to "androidApp/build.gradle.kts",
                "TERMUX-BRIDGE" to "androidApp/src/main/AndroidManifest.xml",
                "TERMUX-BRIDGE" to "android/x/src/main/kotlin/Run.kt",
                "TERMUX-STRING" to "ui/words/src/commonMain/composeResources/values-es/strings_x.xml",
            ).sortedBy { it.toString() },
            found.sortedBy { it.toString() },
        )
    }
}
