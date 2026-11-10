package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.core.data.kv.GateNotOpen
import io.github.eslamasabry.opencode_mobile.release.RepoFiles
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * A read of the saved settings before the migration gate opens must never end a release app. On the owner's phone the
 * usage widget's refresh did exactly that on the first open after the update to 3.0.0+10020 (GateNotOpen out of
 * UsageWidgetProvider.onUpdate): the store had the throwing rule in every build.
 */
class EarlyReadsTest {
    @Test
    fun `a release counts an early read and lets it answer`() {
        val before = EarlyReads.count
        EarlyReads.policy(debuggable = false).onEarlyRead()
        EarlyReads.policy(debuggable = false).onEarlyRead()
        assertEquals(before + 2, EarlyReads.count)
    }

    @Test
    fun `a debug build still throws, so the early reader is found`() {
        assertFailsWith<GateNotOpen> { EarlyReads.policy(debuggable = true).onEarlyRead() }
    }

    @Test
    fun `the app's settings store is built with that rule`() {
        val stores = RepoFiles.text("androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/PreviewStores.kt")
        assertTrue("EarlyReads.policy(build.debuggable)" in stores, "PreviewStores must not fall back to the throwing default")
    }

    @Test
    fun `no widget callback can let an error out`() {
        val dir = "android/widgets/src/main/kotlin/io/github/eslamasabry/opencode_mobile"
        for (name in listOf("UsageWidgetProvider.kt", "SessionsWidgetProvider.kt", "StatusWidgetProvider.kt")) {
            val source = RepoFiles.text("$dir/$name")
            for (callback in listOf("override fun onUpdate(", "override fun onAppWidgetOptionsChanged(", "override fun onDeleted(")) {
                val body = source.substringAfter(callback).substringBefore("\n    }\n")
                assertTrue("widgetSafely {" in body, "$name: $callback must run inside widgetSafely")
            }
        }
    }
}
