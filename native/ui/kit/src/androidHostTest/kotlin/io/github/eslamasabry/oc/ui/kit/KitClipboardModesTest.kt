package io.github.eslamasabry.oc.ui.kit

import android.app.Application
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.view.View
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.test.junit4.createComposeRule
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.kit.foundation.KitClipboardMode
import io.github.eslamasabry.oc.ui.kit.foundation.KitClipboardSelection
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.input.KitFieldKind
import io.github.eslamasabry.oc.ui.kit.input.KitSecretSink
import io.github.eslamasabry.oc.ui.kit.input.LocalKitSecretSink
import io.github.eslamasabry.oc.ui.kit.platform.AndroidKitPlatform
import io.github.eslamasabry.oc.ui.kit.platform.KitPlatform
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class KitClipboardModesTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `B-SEC-145 reply selection keeps the ordinary preview and exact copied text`() = runTest {
        val app = RuntimeEnvironment.getApplication()
        var selection: Clipboard? = null
        compose.setContent {
            KitTheme(KitAppearance(), platform = AndroidKitPlatform(View(app))) {
                KitClipboardSelection(KitClipboardMode.Public) {
                    selection = LocalClipboard.current
                    KitText("A reply to copy")
                }
            }
        }
        compose.waitForIdle()
        selection!!.setClipEntry(ClipEntry(ClipData.newPlainText("", "A reply to copy")))
        val clip = app.getSystemService(ClipboardManager::class.java).primaryClip!!
        assertEquals("A reply to copy", clip.getItemAt(0).text.toString())
        assertFalse("Reply selection must stay Public", clip.description.extras!!.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE))
    }

    @Test
    fun `B-SEC-145 a password or key field keeps selection and cut Sensitive inside a Public page`() = runTest {
        val app = RuntimeEnvironment.getApplication()
        val android = AndroidKitPlatform(View(app))
        var field: Clipboard? = null
        var page: Clipboard? = null
        val platform = object : KitPlatform by android {
            @Composable
            override fun ClipboardBoundary(mode: KitClipboardMode, content: @Composable () -> Unit) {
                android.ClipboardBoundary(mode) {
                    if (mode == KitClipboardMode.Sensitive) field = LocalClipboard.current
                    content()
                }
            }
        }
        compose.setContent {
            KitTheme(KitAppearance(), platform = platform) {
                page = LocalClipboard.current
                CompositionLocalProvider(LocalKitSecretSink provides KitSecretSink { }) {
                    KitField(TextFieldState(), "API key", kind = KitFieldKind.Secret)
                }
            }
        }
        compose.waitForIdle()
        assertTrue("A secret field must install its own Sensitive boundary", field != null)
        val text = "sample field value"
        field!!.setClipEntry(ClipEntry(ClipData.newPlainText("", text)))
        val manager = app.getSystemService(ClipboardManager::class.java)
        assertEquals(text, manager.primaryClip!!.getItemAt(0).text.toString())
        assertTrue("The Public page must not overwrite the secret field's flag", manager.primaryClip!!.description.extras!!.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE))
        page!!.setClipEntry(ClipEntry(ClipData.newPlainText("", "Ordinary text")))
        assertFalse("A Sensitive child must not make sibling copies Sensitive", manager.primaryClip!!.description.extras!!.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE))
    }
}
