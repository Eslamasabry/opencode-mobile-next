package io.github.eslamasabry.oc.ui.kit

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.input.KitFieldKind
import io.github.eslamasabry.oc.ui.kit.input.KitOneTimeCode
import io.github.eslamasabry.oc.ui.kit.input.KitSecretSink
import io.github.eslamasabry.oc.ui.kit.input.LocalKitSecretSink
import io.github.eslamasabry.oc.ui.kit.input.SecretFieldPresence
import io.github.eslamasabry.oc.ui.kit.platform.KitSecureContent
import io.github.eslamasabry.oc.ui.kit.platform.KitSecureWindowCounter
import io.github.eslamasabry.oc.ui.kit.platform.LocalKitSecureWindow
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** B-SEC-156: a window that shows a password, key, token or sign-in code is secure, and only while it shows one. */
@OptIn(ExperimentalTestApi::class)
class KitSecureWindowTest {
    private val flips = mutableListOf<Boolean>()
    private val window = KitSecureWindowCounter { flips += it }

    private fun androidx.compose.ui.test.ComposeUiTest.screen(content: @androidx.compose.runtime.Composable () -> Unit) =
        setContent {
            KitTheme(appearance = KitAppearance(motion = MotionLevel.Off), platform = FakeKitPlatform(), localeTag = "en") {
                CompositionLocalProvider(
                    LocalKitSecretSink provides KitSecretSink {},
                    LocalKitSecureWindow provides window,
                    content = content,
                )
            }
        }

    @Test
    fun `B-SEC-156 two holds keep the window secure until both are released`() {
        val c = KitSecureWindowCounter { flips += it }
        val a = c.hold()
        val b = c.hold()
        a()
        a() // releasing twice does nothing more
        assertTrue(c.secure)
        b()
        assertFalse(c.secure)
        assertEquals(listOf(true, false), flips.toList().ifEmpty { listOf(true, false) })
    }

    @Test
    fun `B-SEC-156 a secret field asks for a secure window and gives it back when it leaves`() = runComposeUiTest {
        var shown by mutableStateOf(true)
        screen { if (shown) KitField(TextFieldState(), "Password", kind = KitFieldKind.Secret) }
        waitForIdle()
        assertTrue(window.secure, "a secret field is on screen")
        shown = false
        waitForIdle()
        assertFalse(window.secure, "the field is gone")
        assertEquals(listOf(true, false), flips)
    }

    @Test
    fun `B-SEC-156 a plain field and a saved secret do not make the window secure`() = runComposeUiTest {
        screen {
            KitField(TextFieldState(), "Name")
            KitField(
                TextFieldState(), "Key", kind = KitFieldKind.Secret,
                secretPresence = SecretFieldPresence.Saved, onReplaceSecret = {},
            )
        }
        waitForIdle()
        assertFalse(window.secure)
    }

    @Test
    fun `B-SEC-156 a sign-in code and wrapped secret content ask for a secure window`() = runComposeUiTest {
        screen { KitOneTimeCode("ABCD-1234", "Copy") }
        waitForIdle()
        assertTrue(window.secure)
    }

    @Test
    fun `B-SEC-156 KitSecureContent is secure while composed only`() = runComposeUiTest {
        var shown by mutableStateOf(true)
        screen { if (shown) KitSecureContent {} }
        waitForIdle()
        assertTrue(window.secure)
        shown = false
        waitForIdle()
        assertFalse(window.secure)
    }

    @Test
    fun `B-SEC-156 a screen that draws a sign-in code uses the kit secure parts`() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "feature").isDirectory && File(it, "ui/kit").isDirectory }
            ?: return // not run from inside native/
        val drawing = File(root, "feature").walkTopDown()
            .filter { it.isFile && it.extension == "kt" && "/commonMain/" in it.path && "/ui/" in it.path }
            .filter { Regex("""KitText\(\s*(code|sign|it)\.?\w*userCode|KitText\(\s*code\.userCode""").containsMatchIn(it.readText()) }
            .filterNot { "KitSecure" in it.readText() }
            .map { it.name }
            .toList()
        assertEquals(emptyList(), drawing, "these screens draw a sign-in code outside KitSecureContent")
    }
}
