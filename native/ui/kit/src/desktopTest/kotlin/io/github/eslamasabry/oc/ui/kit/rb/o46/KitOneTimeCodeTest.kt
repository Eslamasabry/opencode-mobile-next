package io.github.eslamasabry.oc.ui.kit.rb.o46

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.input.KitOneTimeCode
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class KitOneTimeCodeTest {
    @Suppress("DEPRECATION")
    @Test
    fun `B-SEC-084 one time code copies the exact displayed code in an Arabic interface`() =
        runComposeUiTest {
            var copied: String? = null
            val clipboard =
                object : androidx.compose.ui.platform.ClipboardManager {
                    override fun setText(
                        annotatedString: androidx.compose.ui.text.AnnotatedString
                    ) {
                        copied = annotatedString.text
                    }

                    override fun getText(): androidx.compose.ui.text.AnnotatedString? =
                        copied?.let {
                            androidx.compose.ui.text.AnnotatedString(it)
                        }
                }
            setContent {
                CompositionLocalProvider(LocalClipboardManager provides clipboard) {
                    KitTheme(platform = FakeKitPlatform(), localeTag = "ar") {
                        KitOneTimeCode("WDJB-MJHT", "Copy code")
                    }
                }
            }
            onNodeWithText("WDJB-MJHT").assertExists()
            onNodeWithContentDescription("Copy code").performClick()
            assertEquals("WDJB-MJHT", copied)
        }
}
