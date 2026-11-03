package io.github.eslamasabry.oc.ui.kit

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.input.KitFieldKind
import io.github.eslamasabry.oc.ui.kit.input.KitSecretSink
import io.github.eslamasabry.oc.ui.kit.input.LocalKitSecretSink
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

/**
 * QA wave 4 N6 (S2 privacy): the add-server password showed dots on screen but its real value in the accessibility
 * tree, so any accessibility service could read it. A secret field is a password node that exposes only dots.
 */
@OptIn(ExperimentalTestApi::class)
class SecretSemanticsTest {
    @Test
    fun `N6 B-SEC-204 a secret field is a password node and exposes only dots`() = runComposeUiTest {
        val state = TextFieldState()
        setContent {
            KitTheme(appearance = KitAppearance(motion = MotionLevel.Off), platform = FakeKitPlatform(), localeTag = "en") {
                CompositionLocalProvider(LocalKitSecretSink provides KitSecretSink {}) {
                    KitField(state, "Password", kind = KitFieldKind.Secret)
                }
            }
        }
        onNodeWithTag("kit-field-secret").performTextReplacement("synthetic-pass-42")
        waitForIdle()
        val config = onNodeWithTag("kit-field-secret").fetchSemanticsNode().config
        assertNotNull(config.getOrNull(SemanticsProperties.Password), "the node is marked as a password")
        val exposed = buildList {
            config.getOrNull(SemanticsProperties.EditableText)?.let { add(it.text) }
            config.getOrNull(SemanticsProperties.Text)?.forEach { add(it.text) }
            config.getOrNull(SemanticsProperties.ContentDescription)?.forEach { add(it) }
            config.getOrNull(SemanticsProperties.InputText)?.let { add(it.text) }
        }
        assertFalse(exposed.any { "synthetic" in it || "42" in it }, "exposed: $exposed")
        assertEquals("synthetic-pass-42", state.text.toString())
    }
}
