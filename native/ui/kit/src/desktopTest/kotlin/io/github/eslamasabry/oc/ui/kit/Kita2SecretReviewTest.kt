package io.github.eslamasabry.oc.ui.kit

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.AnnotatedString
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.input.KitFieldKind
import io.github.eslamasabry.oc.ui.kit.input.KitSecretSink
import io.github.eslamasabry.oc.ui.kit.input.LocalKitSecretSink
import io.github.eslamasabry.oc.ui.kit.modal.KitInputDialog
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Counterexamples from the KITA2 review; all input values are synthetic. */
@OptIn(ExperimentalTestApi::class)
class Kita2SecretReviewTest {
    @Composable
    private fun Theme(content: @Composable () -> Unit) {
        KitTheme(
            appearance = KitAppearance(motion = MotionLevel.Off),
            platform = FakeKitPlatform(),
            localeTag = "en",
            content = content,
        )
    }

    @Test
    fun `KITA2-3 secret dialog text never enters saved state or recreation`() = runComposeUiTest {
        val marker = "synthetic-input-marker"
        var registry = SaveableStateRegistry(null) { true }
        var mounted by mutableStateOf(true)
        setContent {
            Theme {
                CompositionLocalProvider(LocalKitSecretSink provides KitSecretSink {}) {
                    KitSheetHost {
                        if (mounted) {
                            CompositionLocalProvider(LocalSaveableStateRegistry provides registry) {
                                KitInputDialog(
                                    true,
                                    "Enter value",
                                    "Value",
                                    "Apply",
                                    kind = KitFieldKind.Secret,
                                    onResult = {},
                                )
                            }
                        }
                    }
                }
            }
        }
        onNodeWithTag("kit-field-secret").performTextReplacement(marker)
        waitForIdle()
        runOnIdle {
            val saved = registry.performSave()
            assertFalse(containsMarker(saved, marker), "saved state must contain no secret input")
            registry = SaveableStateRegistry(saved) { true }
            mounted = false
        }
        waitForIdle()
        runOnIdle { mounted = true }
        assertTrue(
            onNodeWithTag("kit-field-secret")
                .fetchSemanticsNode()
                .config[androidx.compose.ui.semantics.SemanticsProperties.EditableText]
                .text
                .isEmpty()
        )
        // A fresh registry contains no secret even after the recreated field registers its savers.
        runOnIdle { assertFalse(containsMarker(registry.performSave(), marker)) }
    }

    @Test
    fun `B-SEC-204 KITA2-2 typing registers each accepted edit before publishing the state`() =
        runComposeUiTest {
            val state = TextFieldState()
            val before = mutableListOf<String>()
            val accepted = mutableListOf<String>()
            setContent {
                Theme {
                    CompositionLocalProvider(
                        LocalKitSecretSink provides
                            KitSecretSink {
                                before += state.text.toString()
                                accepted += it
                            }
                    ) {
                        KitField(state, "Value", kind = KitFieldKind.Secret, maxLength = 3)
                    }
                }
            }
            onNodeWithTag("kit-field-secret").performTextReplacement("one")
            onNodeWithTag("kit-field-secret").performTextReplacement("two")
            assertEquals(listOf("", "one"), before)
            assertEquals(listOf("one", "two"), accepted)
            onNodeWithTag("kit-field-secret").performTextReplacement("longer")
            assertEquals(
                listOf("one", "two"),
                accepted,
                "rejected edits are not published or registered",
            )
        }

    @Test
    @Suppress("DEPRECATION")
    fun `B-SEC-204 KITA2-2 Paste registers the accepted value before publishing it`() =
        runComposeUiTest {
            val state = TextFieldState()
            val before = mutableListOf<String>()
            val accepted = mutableListOf<String>()
            setContent {
                Theme {
                    val clipboard = remember {
                        object : androidx.compose.ui.platform.ClipboardManager {
                            private var text = AnnotatedString("synthetic-paste")

                            override fun getText(): AnnotatedString = text

                            override fun setText(annotatedString: AnnotatedString) {
                                text = annotatedString
                            }
                        }
                    }
                    CompositionLocalProvider(
                        LocalClipboardManager provides clipboard,
                        LocalKitSecretSink provides
                            KitSecretSink {
                                before += state.text.toString()
                                accepted += it
                            },
                    ) {
                        KitField(state, "Value", kind = KitFieldKind.Secret)
                    }
                }
            }
            onNodeWithText("Paste").performClick()
            waitForIdle()
            assertEquals(listOf(""), before)
            assertEquals(listOf("synthetic-paste"), accepted)
        }

    @Test
    fun `KITA2-3 secret dialogs reject a prefilled initial value`() {
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            runComposeUiTest {
                setContent {
                    Theme {
                        KitSheetHost {
                            KitInputDialog(
                                true,
                                "Enter value",
                                "Value",
                                "Apply",
                                initial = "synthetic-prefill",
                                kind = KitFieldKind.Secret,
                                onResult = {},
                            )
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `KITA2-2 a secret field requires the app redactor sink`() {
        kotlin.test.assertFailsWith<IllegalStateException> {
            runComposeUiTest {
                setContent {
                    Theme {
                        KitField(remember { TextFieldState() }, "Value", kind = KitFieldKind.Secret)
                    }
                }
            }
        }
    }

    private fun containsMarker(value: Any?, marker: String): Boolean =
        when (value) {
            is String -> value.contains(marker)
            is Map<*, *> -> value.values.any { containsMarker(it, marker) }
            is Iterable<*> -> value.any { containsMarker(it, marker) }
            else -> false
        }
}
