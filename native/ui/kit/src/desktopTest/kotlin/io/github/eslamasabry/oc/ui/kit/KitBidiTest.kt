package io.github.eslamasabry.oc.ui.kit

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.bidi.KitBidi
import io.github.eslamasabry.oc.ui.kit.bidi.KitLtrText
import io.github.eslamasabry.oc.ui.kit.bidi.kitIsolatedValues
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class KitBidiTest {
    @Test
    fun `B-L10N-006 ltr wraps in LRI and PDI, auto in FSI and PDI`() {
        assertEquals("⁦lib/main.dart⁩", KitBidi.ltr("lib/main.dart"))
        assertEquals("⁨مشروع⁩", KitBidi.auto("مشروع"))
    }

    @Test
    fun `B-L10N-006 never wraps twice and leaves empty text alone`() {
        val once = KitBidi.ltr("a/b")
        assertEquals(once, KitBidi.ltr(once))
        assertEquals(once, KitBidi.auto(once))
        assertEquals(KitBidi.auto("x"), KitBidi.ltr(KitBidi.auto("x")))
        assertEquals("", KitBidi.ltr(""))
        assertEquals("", KitBidi.auto(""))
    }

    @Test
    fun `B-L10N-006 a path inside an Arabic sentence stays one isolated run`() {
        val sentence = "تم حفظ ${KitBidi.ltr("/home/me/app.dart")} بنجاح"
        val start = sentence.indexOf('⁦')
        val end = sentence.indexOf('⁩')
        assertEquals("/home/me/app.dart", sentence.substring(start + 1, end))
    }

    @Test
    fun `B-L10N-006 only a right-to-left layout gets marks, and amounts, cards, times and addresses are one run each`() {
        val text = "الرسوم [AMOUNT] CUR لبطاقة [Bank A] Visa ••42 عند 10:00."
        assertEquals(text, KitBidi.isolateValues(text, rtl = false))
        val isolated = KitBidi.isolateValues(text, rtl = true)
        assertTrue(isolated.contains("⁦[AMOUNT] CUR⁩"), isolated)
        assertTrue(isolated.contains("⁦[Bank A] Visa ••42⁩"), isolated)
        assertTrue(isolated.contains("⁦10:00⁩."), isolated)
        val address = KitBidi.isolateValues("اكتب إلى billing@provider-a.example اليوم", rtl = true)
        assertTrue(address.contains("⁦billing@provider-a.example⁩"), address)
    }

    @Test
    fun `B-L10N-006 the ltr text shows the isolated value in an Arabic screen, and values isolate only there`() =
        runComposeUiTest {
            var shown = ""
            setContent {
                KitTheme(KitAppearance(motion = MotionLevel.Off), FakeKitPlatform(), "ar", stillFrame = true) {
                    KitLtrText("src/a.dart", maxLines = 1)
                    shown = kitIsolatedValues("عند 10:00")
                }
            }
            onNodeWithText("⁦src/a.dart⁩").assertIsDisplayed()
            assertEquals("عند ⁦10:00⁩", shown)
        }

    @Test
    fun `B-L10N-006 an English screen keeps values as written`() = runComposeUiTest {
        var shown = ""
        setContent {
            KitTheme(KitAppearance(motion = MotionLevel.Off), FakeKitPlatform(), "en", stillFrame = true) {
                shown = kitIsolatedValues("at 10:00")
            }
        }
        waitForIdle()
        assertEquals("at 10:00", shown)
    }
}
