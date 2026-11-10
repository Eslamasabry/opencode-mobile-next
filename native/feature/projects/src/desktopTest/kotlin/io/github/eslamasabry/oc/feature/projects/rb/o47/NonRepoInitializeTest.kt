package io.github.eslamasabry.oc.feature.projects.rb.o47

import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.model.VersionControlHealth
import io.github.eslamasabry.oc.core.model.VersionControlSetupState
import io.github.eslamasabry.oc.feature.projects.presentation.ProjectWorkspaceActions
import io.github.eslamasabry.oc.feature.projects.presentation.ProjectWorkspaceUi
import io.github.eslamasabry.oc.feature.projects.ui.ProjectWorkspaceSheet
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.collections.immutable.persistentListOf

/** B-107: a folder with no Git offers Initialize instead of "Could not inspect this copy". */
@OptIn(ExperimentalTestApi::class)
class NonRepoInitializeTest {
    @Test
    fun `B-107 a non-repo shows Initialize Git and not the inspection error`() = runComposeUiTest {
        val state =
            ProjectWorkspaceUi(
                available = true,
                visible = true,
                canInspect = true,
                canInitGit = true,
                health = VersionControlHealth(null, null, persistentListOf(), VersionControlSetupState.Absent),
                inspectionFailure = Failure.NotFound(),
            )
        val calls = mutableListOf<String>()
        val actions = object : ProjectWorkspaceActions {
            override fun initializeGit() {
                calls += "init"
            }
        }
        setContent {
            KitTheme(platform = DesktopKitPlatform(), appearance = KitAppearance(motion = MotionLevel.Off), stillFrame = true, localeTag = "en") {
                KitSheetHost { ProjectWorkspaceSheet(state, actions) }
            }
        }
        onNodeWithText("Could not inspect this copy. Its state is unknown.", useUnmergedTree = true).assertDoesNotExist()
        onNodeWithText("Initialize Git", useUnmergedTree = true).performClick()
        assertEquals(listOf("init"), calls)
    }
}
