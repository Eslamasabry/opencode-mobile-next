package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.opencode_mobile.release.RepoFiles
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** E00-S07 hooks H1 (per-app language) and H2 (tiles, pinned shortcuts): the pure decisions. */
class FirstLaunchRulesTest {
    @Test
    fun `E00-S07 H1 mirrors a 2_1_4 language choice only when the system holds none`() {
        // 2.1.4 AppLocaleStore saves the bare language code, or removes the key for "Use system language".
        assertTrue(FirstLaunchRules.mirrorLocale("ar", ""))
        assertTrue(FirstLaunchRules.mirrorLocale("zh", ""))
        assertFalse(FirstLaunchRules.mirrorLocale(null, ""), "system language: nothing to mirror")
        assertFalse(FirstLaunchRules.mirrorLocale("ar", "es"), "a language set in system settings is kept")
        assertFalse(FirstLaunchRules.mirrorLocale("xx", ""), "not a 2.1.4 language")
        assertFalse(FirstLaunchRules.mirrorLocale("", ""))
    }

    @Test
    fun `E00-S07 H2 pinned shortcut ids are the 2_1_4 ids`() {
        assertEquals("1730000000000" to "ses_abc", FirstLaunchRules.pinnedSession("oc.session:1730000000000:ses_abc"))
        assertNull(FirstLaunchRules.pinnedSession("connect"), "a manifest shortcut is not touched")
        assertNull(FirstLaunchRules.pinnedSession("oc.session:"))
        assertNull(FirstLaunchRules.pinnedSession("oc.session::ses_abc"))
        assertNull(FirstLaunchRules.pinnedSession("oc.session:123:"))
    }

    @Test
    fun `E00-S07 H2 redraws both tiles the app declares`() {
        val manifest = RepoFiles.text("android/tiles/src/main/AndroidManifest.xml")
        for (tile in FirstLaunchRules.TILES) assertTrue("android:name=\"$tile\"" in manifest, tile)
        assertEquals(2, FirstLaunchRules.TILES.size)
    }
}
