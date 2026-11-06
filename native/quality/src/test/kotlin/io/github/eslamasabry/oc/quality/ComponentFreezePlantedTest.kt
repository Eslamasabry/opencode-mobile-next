package io.github.eslamasabry.oc.quality

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** E00-S13: the freeze rule itself, on a planted tree. Each change names the differing name in its failure. */
class ComponentFreezePlantedTest {
    private val p = ComponentFreeze.APP_ID

    private fun manifest(dir: File, body: String): File =
        File(dir, "AndroidManifest.xml").apply {
            writeText(
                """<manifest xmlns:android="http://schemas.android.com/apk/res/android"
                     xmlns:tools="http://schemas.android.com/tools"><application>$body</application></manifest>"""
            )
        }

    private val good =
        """
        <activity android:name=".MainActivity">
          <intent-filter><action android:name="android.intent.action.MAIN"/></intent-filter>
          <intent-filter><action android:name="android.intent.action.VIEW"/>
            <data android:scheme="opencode-mobile" android:host="session"/></intent-filter>
          <meta-data android:name="android.app.shortcuts" android:resource="@xml/shortcuts"/>
        </activity>
        <receiver android:name="$p.SessionsWidgetProvider">
          <intent-filter><action android:name="android.appwidget.action.APPWIDGET_UPDATE"/></intent-filter>
          <meta-data android:name="android.appwidget.provider" android:resource="@xml/w"/>
        </receiver>
        <service android:name="$p.PauseTileService" android:permission="android.permission.BIND_QUICK_SETTINGS_TILE">
          <intent-filter><action android:name="android.service.quicksettings.action.QS_TILE"/></intent-filter>
        </service>
        <provider android:name="androidx.core.content.FileProvider" android:authorities="${'$'}{applicationId}.files"/>
        """

    private val list =
        listOf(
            ComponentFreeze.Entry("component", "activity:$p.MainActivity", "android.intent.action.MAIN"),
            ComponentFreeze.Entry("widget", "$p.SessionsWidgetProvider", ""),
            ComponentFreeze.Entry("tile", "$p.PauseTileService", ""),
            ComponentFreeze.Entry("shortcut", "connect", ""),
            ComponentFreeze.Entry("scheme", "opencode-mobile://session", ""),
            ComponentFreeze.Entry("channel", "opencode_live_connection", ""),
            ComponentFreeze.Entry("worker", "$p.WidgetStatusWorker", ""),
            ComponentFreeze.Entry("work-name", "widget-status-{profile}-{session}", ""),
            ComponentFreeze.Entry("authority", "{applicationId}.files", ""),
        )

    private val shortcuts =
        """<shortcuts xmlns:android="http://schemas.android.com/apk/res/android">
             <shortcut android:shortcutId="connect"><intent android:targetPackage="$p"
               android:targetClass="$p.MainActivity"/></shortcut></shortcuts>"""

    private val worker =
        """
        package $p
        object Channels { const val LIVE = "opencode_live_connection" }
        class WidgetStatusWorker(context: Context, parameters: WorkerParameters) :
            CoroutineWorker(context, parameters) {
            private fun name(profile: String, session: String) = "widget-status-${'$'}profile-${'$'}{session}"
        }
        """.trimIndent()

    private fun problems(body: String = good, kotlin: String = worker, shortcutXml: String = shortcuts): List<String> {
        val dir = createTempDirectory("freeze").toFile()
        try {
            val res = File(dir, "shortcuts.xml").apply { writeText(shortcutXml) }
            val (components, meta) = ComponentFreeze.components(manifest(dir, body), p)
            val shipped = ComponentFreeze.Shipped(components, meta, { if (it == "shortcuts") res else null }, listOf("A.kt" to kotlin))
            return ComponentFreeze.problems(list, shipped)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `E00-S13 an unchanged tree passes`() = assertEquals(emptyList(), problems())

    private fun assertNamed(name: String, found: List<String>) {
        assertEquals(1, found.size, found.joinToString("\n"))
        assertTrue(name in found.single(), "the failure must name $name: ${found.single()}")
    }

    @Test
    fun `E00-S13 a renamed widget provider fails with its frozen name`() =
        assertNamed("$p.SessionsWidgetProvider", problems(good.replace("SessionsWidgetProvider", "ConversationsWidget")))

    @Test
    fun `E00-S13 a tile service without the bind permission fails`() =
        assertNamed("$p.PauseTileService", problems(good.replace("android.permission.BIND_QUICK_SETTINGS_TILE", "")))

    @Test
    fun `E00-S13 a moved worker class fails`() =
        assertNamed("$p.WidgetStatusWorker", problems(kotlin = worker.replace("package $p", "package $p.widgets")))

    @Test
    fun `E00-S13 a changed channel id fails`() =
        assertNamed("opencode_live_connection", problems(kotlin = worker.replace("opencode_live_connection", "live")))

    @Test
    fun `E00-S13 a renamed unique-work pattern fails`() =
        assertNamed("widget-status-{profile}-{session}", problems(kotlin = worker.replace("widget-status-", "widget-")))

    @Test
    fun `E00-S13 a renamed shortcut id fails`() =
        assertNamed("connect", problems(shortcutXml = shortcuts.replace("\"connect\"", "\"open\"")))

    @Test
    fun `E00-S13 a dropped deep-link host fails`() =
        assertNamed("opencode-mobile://session", problems(good.replace("android:host=\"session\"", "android:host=\"chat\"")))

    @Test
    fun `E00-S13 a fixed FileProvider authority fails`() =
        assertNamed("{applicationId}.files", problems(good.replace("${'$'}{applicationId}.files", "$p.fileprovider")))

    @Test
    fun `E00-S13 a component removed with tools-node fails`() {
        val removed = ComponentFreeze.components(
            createTempDirectory("freeze").toFile().let { d ->
                manifest(d, """<receiver android:name="$p.SessionsWidgetProvider" tools:node="remove"/>""")
            },
            p,
        ).first
        val merged = ComponentFreeze.merge(listOf(ComponentFreeze.components(
            createTempDirectory("freeze").toFile().let { manifest(it, good) }, p).first, removed))
        assertTrue(merged.none { it.name == "$p.SessionsWidgetProvider" })
    }
}
