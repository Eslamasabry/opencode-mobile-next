package io.github.eslamasabry.oc.ui.kit.rb.o44

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.row.*
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class KitAboutPartsTest {
    @Suppress("DEPRECATION")
    @Test
    fun `B-KIT-051 row copy uses the exact app authored version without opening the row`() =
        runComposeUiTest {
            var copied: String? = null
            var opened = 0
            val clipboard =
                object : androidx.compose.ui.platform.ClipboardManager {
                    override fun setText(
                        annotatedString: androidx.compose.ui.text.AnnotatedString
                    ) {
                        copied = annotatedString.text
                    }

                    override fun getText(): androidx.compose.ui.text.AnnotatedString? = null
                }
            setContent {
                CompositionLocalProvider(LocalClipboardManager provides clipboard) {
                    KitTheme(platform = FakeKitPlatform()) {
                        KitRow(
                            "Build",
                            trailing =
                                KitRowTrailing.Copy("OpenCode Mobile 2.1.1+58", "Copy version"),
                            onClick = { opened++ },
                        )
                    }
                }
            }
            onNodeWithContentDescription("Copy version").performClick()
            assertEquals("OpenCode Mobile 2.1.1+58", copied)
            assertEquals(0, opened)
        }

    @Test
    fun `B-KIT-024 notice custom icon retains message and action`() = runComposeUiTest {
        var clicked = 0
        setContent {
            KitTheme(platform = FakeKitPlatform()) {
                KitNotice(
                    "Android is supported",
                    title = "About this build",
                    icon = KitIcons.experiments,
                    actions =
                        listOf(
                            io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction(
                                "Learn more",
                                { clicked++ },
                            )
                        ),
                    liveRegion = false,
                )
            }
        }
        onNodeWithText("Android is supported").assertExists()
        onNodeWithText("Learn more").performClick()
        assertEquals(1, clicked)
    }
}
