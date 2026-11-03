package io.github.eslamasabry.opencode_mobile.graph

import java.io.File
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** B-ANDR-035 (Flutter test/manage_space/manage_space_manifest_test.dart): Clear storage opens the guard page first. */
class ManageSpaceManifestTest {
    private fun source(path: String): String = File(path).readText()

    private val kotlin = "src/main/kotlin/io/github/eslamasabry/opencode_mobile"

    @Test
    fun `B-ANDR-035 the application declares the manage-space activity`() {
        val manifest = source("src/main/AndroidManifest.xml")
        val application = Regex("""<application\b[^>]*>""").find(manifest)?.value
        assertNotNull(application)
        assertTrue("""android:manageSpaceActivity=".ManageSpaceActivity"""" in application)
        assertTrue("""android:name=".ManageSpaceActivity"""" in manifest, "the named activity must be declared")
    }

    @Test
    fun `B-ANDR-035 the activity runs the manage-space page, not the app`() {
        val activity = source("$kotlin/ManageSpaceActivity.kt")
        assertTrue("class ManageSpaceActivity : ComponentActivity()" in activity)
        assertTrue("ManageSpaceScreen(" in activity)
        assertTrue("App(" !in activity, "it must not start the app's navigation")
    }

    @Test
    fun `B-ANDR-035 both halves of the clear agree`() {
        val wiring = source("$kotlin/graph/BuildProjectEntryChatsWiring.kt")
        assertTrue("clearApplicationUserData()" in wiring)
        assertTrue(": ManageSpacePort" in wiring)
        for (method in listOf("measure", "clearCache", "clearAllData")) {
            assertTrue("override suspend fun $method(" in wiring, method)
        }
    }
}
