@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.feature.phone.storage

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test

/** S11: the page names the permission the row asks for (All files access) and says whether it is allowed. */
class SharedFoldersWordsTest {
    private object Actions : SharedFoldersActions {
        override fun refresh() {}

        override fun allowAndroidAccess() {}

        override fun allowAllFolders(value: Boolean) {}

        override fun openThisPhone() {}

        override fun back() {}
    }

    private fun show(granted: Boolean, block: androidx.compose.ui.test.ComposeUiTest.() -> Unit) =
        runComposeUiTest {
            setContent {
                KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                    SharedFoldersScreen(
                        SharedFoldersUi(
                            state =
                                SharedFolderState(
                                    permissionGranted = granted,
                                    allFolders = false,
                                    canAllowAllFolders = true,
                                    restartRequired = false,
                                )
                        ),
                        Actions,
                    )
                }
            }
            block()
        }

    @Test
    fun `S11 not allowed says so and names All files access`() =
        show(granted = false) {
            onNodeWithText("Not allowed. Allow All files access", substring = true).assertExists()
            onNodeWithText("Android asks you to give this app All files access", substring = true).assertExists()
            onNodeWithText("it does not share your whole storage", substring = true).assertDoesNotExist()
        }

    @Test
    fun `S11 allowed says so and where to turn it off`() =
        show(granted = true) {
            onNodeWithText("Allowed: this app has All files access", substring = true).assertExists()
        }
}
