package io.github.eslamasabry.oc.feature.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.domain.ImportableConversation
import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationActions
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationServer
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationSetup
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationSheet
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationUi
import io.github.eslamasabry.oc.feature.chats.presentation.PHONE_STORAGE_ROOT
import io.github.eslamasabry.oc.feature.chats.presentation.PhoneFolderReach
import io.github.eslamasabry.oc.feature.chats.presentation.StorageFolder
import io.github.eslamasabry.oc.feature.chats.ui.NewConversationScreen
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.gate.flicker.kitUiTest
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Issue 147: New conversation names a folder in the phone's storage in full once, with what that storage can't do
 * behind Why, and when the folder is not ready it says why and offers the way forward instead of Start.
 */
@OptIn(ExperimentalTestApi::class)
class NewConversationPhoneFolderScreenTest {
    private val folder = "/storage/emulated/0/MyProj"

    private class Actions : NewConversationActions {
        var fixes = 0

        override fun back() {}

        override fun addServer() {}

        override fun chooseServer(profile: ProfileId) {}

        override fun draftChanged(text: String) {}

        override fun chooseDirectory(directory: String) {}

        override fun chooseModel(model: ModelRef?) {}

        override fun chooseAgent(agent: String?) {}

        override fun separateCopy(on: Boolean) {}

        override fun sheet(sheet: NewConversationSheet?) {}

        override fun start() {}

        override fun import(conversation: ImportableConversation) {}

        override fun retryLoad() {}

        override fun fixPhoneFolder() {
            fixes++
        }
    }

    private fun state(directory: String, reach: PhoneFolderReach?) =
        NewConversationUi(
            profile = ProfileId("p1"),
            servers = listOf(NewConversationServer(ProfileId("p1"), "This phone", connected = true)),
            setup = NewConversationSetup(directory = directory, projects = emptyList(), models = emptyList(), agents = emptyList()),
            directory = directory,
            text = "hello",
            phoneFolder = reach,
        )

    @Test
    fun `I147-STORAGE a ready folder in the phone's storage is named in full once, with Why opening the help`() =
        kitUiTest {
            setContent {
                KitTheme(platform = DesktopKitPlatform(), localeTag = "en", appearance = KitAppearance(motion = MotionLevel.Off)) {
                    NewConversationScreen(state(folder, PhoneFolderReach.Ready), Actions())
                }
            }
            onAllNodesWithTag("chats-new-phone-folder", useUnmergedTree = true).assertCountEquals(1)
            onAllNodesWithText(folder, substring = true).assertCountEquals(1)
            onNodeWithText("Why").performClick()
            waitForIdle()
            onAllNodesWithText("Working in the phone’s storage").assertCountEquals(1)
            onAllNodesWithText("npm install --no-bin-links", substring = true).assertCountEquals(1)
        }

    @Test
    fun `I147-STORAGE a project in the app's own folder gets no such line`() =
        kitUiTest {
            setContent {
                KitTheme(platform = DesktopKitPlatform(), localeTag = "en", appearance = KitAppearance(motion = MotionLevel.Off)) {
                    NewConversationScreen(state("/root/projects/shop", null), Actions())
                }
            }
            onAllNodesWithTag("chats-new-phone-folder", useUnmergedTree = true).assertCountEquals(0)
        }

    @Test
    fun `I147-STORAGE without access the notice offers to allow it, and the action is the page's own`() =
        kitUiTest {
            val actions = Actions()
            setContent {
                KitTheme(platform = DesktopKitPlatform(), localeTag = "en", appearance = KitAppearance(motion = MotionLevel.Off)) {
                    NewConversationScreen(state(folder, PhoneFolderReach.AppAccess), actions)
                }
            }
            onAllNodesWithTag("chats-new-phone-folder", useUnmergedTree = true).assertCountEquals(0)
            onNodeWithText("Allow access to phone storage").performClick()
            assertEquals(1, actions.fixes)
        }

    @Test
    fun `I147-STORAGE while the restart runs the notice says so and its button cannot be tapped again`() =
        kitUiTest {
            val actions = Actions()
            setContent {
                KitTheme(platform = DesktopKitPlatform(), localeTag = "en", appearance = KitAppearance(motion = MotionLevel.Off)) {
                    NewConversationScreen(
                        state(folder, PhoneFolderReach.Restart).copy(phoneAgent = "claude", fixingPhoneFolder = true),
                        actions,
                    )
                }
            }
            assertTrue(onAllNodesWithText("Restarting the phone’s agents", substring = true).fetchSemanticsNodes().isNotEmpty())
            onNodeWithText("Restart the phone’s agents").performClick()
            assertEquals(0, actions.fixes, "busy: a second tap does nothing")
        }

    @Test
    fun `I147-WAIT while OpenCode waits for its reply the notice says it restarts when the reply ends`() =
        kitUiTest {
            setContent {
                KitTheme(platform = DesktopKitPlatform(), localeTag = "en", appearance = KitAppearance(motion = MotionLevel.Off)) {
                    NewConversationScreen(
                        state(folder, PhoneFolderReach.Restart)
                            .copy(
                                phoneAgent = "claude",
                                fixingPhoneFolder = true,
                                phoneFolderBoth = true,
                                phoneFolderWaiting = setOf(io.github.eslamasabry.oc.feature.chats.presentation.PhoneFolderSide.Server),
                            ),
                        Actions(),
                    )
                }
            }
            assertTrue(onAllNodesWithText("restarts by itself when the reply ends", substring = true).fetchSemanticsNodes().isNotEmpty())
            assertEquals(0, onAllNodesWithText("about half a minute", substring = true).fetchSemanticsNodes().size)
        }

    @Test
    fun `I147-STORAGE once the restart is done the page says so`() =
        kitUiTest {
            setContent {
                KitTheme(platform = DesktopKitPlatform(), localeTag = "en", appearance = KitAppearance(motion = MotionLevel.Off)) {
                    NewConversationScreen(
                        state(folder, PhoneFolderReach.Ready).copy(phoneFolderFixed = PhoneFolderReach.Restart),
                        Actions(),
                    )
                }
            }
            onAllNodesWithText("This phone’s OpenCode restarted and can see this folder now.").assertCountEquals(1)
        }

    @Test
    fun `I147-STORAGE while the folder is being checked Start says so`() =
        kitUiTest {
            setContent {
                KitTheme(platform = DesktopKitPlatform(), localeTag = "en", appearance = KitAppearance(motion = MotionLevel.Off)) {
                    NewConversationScreen(state(folder, null).copy(checkingPhoneFolder = true), Actions())
                }
            }
            assertTrue(onAllNodesWithText("Checking this folder in the phone’s storage…").fetchSemanticsNodes().isNotEmpty())
        }

    @Test
    fun `I147-STORAGE in the phone's storage the create row makes a folder here, not a project with Git`() =
        kitUiTest {
            setContent {
                KitTheme(platform = DesktopKitPlatform(), localeTag = "en", appearance = KitAppearance(motion = MotionLevel.Off)) {
                    NewConversationScreen(
                        state(folder, null).copy(storagePath = "/storage/emulated/0/Documents"),
                        Actions(),
                    )
                }
            }
            onAllNodesWithText("New folder here").assertCountEquals(1)
            onAllNodesWithText("Makes an empty folder inside Documents and works in it").assertCountEquals(1)
            onAllNodesWithText("Makes a folder in your projects, with Git").assertCountEquals(0)
        }

    @Test
    fun `I147-STORAGE the name for a folder in the phone's storage says where it goes and adds no Git`() =
        kitUiTest {
            setContent {
                KitTheme(platform = DesktopKitPlatform(), localeTag = "en", appearance = KitAppearance(motion = MotionLevel.Off)) {
                    NewConversationScreen(
                        state(folder, null).copy(storagePath = "/storage/emulated/0/Documents", askingProjectName = true),
                        Actions(),
                    )
                }
            }
            onAllNodesWithText("New folder in Documents").assertCountEquals(1)
            onAllNodesWithText("Makes a folder in your projects, with Git").assertCountEquals(0)
        }

    @Test
    fun `COMPACT-301 storage folder create row wraps full detail without ellipsis at 360 dp`() {
        val cases =
            listOf(
                Triple("en", 1f, "Makes an empty folder inside Internal storage and works in it"),
                Triple("en", 1.3f, "Makes an empty folder inside Internal storage and works in it"),
                Triple("ar", 1f, "ينشئ مجلدًا فارغًا داخل التخزين الداخلي ويعمل فيه"),
                Triple("ar", 1.3f, "ينشئ مجلدًا فارغًا داخل التخزين الداخلي ويعمل فيه"),
            )
        val previous = Locale.getDefault()
        try {
            for ((locale, scale, expected) in cases) {
                Locale.setDefault(Locale.forLanguageTag(locale))
                kitUiTest(360 * 3, 780 * 3) {
                    setContent {
                        CompositionLocalProvider(LocalDensity provides Density(3f, scale)) {
                            KitTheme(
                                platform = DesktopKitPlatform(),
                                localeTag = locale,
                                appearance = KitAppearance(mode = AppearanceMode.Dark, motion = MotionLevel.Off),
                                stillFrame = true,
                            ) {
                                Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground)) {
                                    NewConversationScreen(
                                        state(folder, null).copy(
                                            storagePath = PHONE_STORAGE_ROOT,
                                            storageFolders =
                                                listOf(
                                                    StorageFolder("Documents", "$PHONE_STORAGE_ROOT/Documents"),
                                                    StorageFolder("Download", "$PHONE_STORAGE_ROOT/Download"),
                                                    StorageFolder("DCIM", "$PHONE_STORAGE_ROOT/DCIM"),
                                                ),
                                        ),
                                        Actions(),
                                    )
                                }
                            }
                        }
                    }
                    waitForIdle()
                    val layouts = mutableListOf<TextLayoutResult>()
                    onNodeWithText(expected, useUnmergedTree = true)
                        .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                    val layout = layouts.single()
                    assertFalse(layout.hasVisualOverflow, "[$locale/$scale] visual overflow on: $expected")
                    assertFalse((0 until layout.lineCount).any { layout.isLineEllipsized(it) }, "[$locale/$scale] ellipsized: $expected")
                }
            }
        } finally {
            Locale.setDefault(previous)
        }
    }
}
