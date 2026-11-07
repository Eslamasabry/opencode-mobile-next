package io.github.eslamasabry.oc.ui.kit.rb.o31

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.live.KitLiveLookPreview
import io.github.eslamasabry.oc.ui.kit.live.LiveMockChat
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test

/**
 * lib/ui/kit/live_background/live_mock_chat.dart at 8302de537: the mock chat (a message, four lines, the box) composes
 * at the tile's scale and the preview's, in both themes; its sizes are cited line by line in LiveMockChat.kt and its
 * look is checked by the option-card goldens of order 44.
 */
@OptIn(ExperimentalTestApi::class)
class LiveMockChatParityTest {
    @Test fun composesAtTheTilesScaleAndThePreviews() {
        for (mode in listOf(AppearanceMode.Dark, AppearanceMode.Light)) {
            runDesktopComposeUiTest(412, 915) {
                setContent {
                    KitTheme(appearance = KitAppearance(mode = mode), platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                        Box(Modifier.size(200.dp, 160.dp)) { LiveMockChat(scale = .7f) }
                        Box(Modifier.size(400.dp, 300.dp)) { LiveMockChat(scale = 1f) }
                    }
                }
                waitForIdle()
            }
        }
    }

    @Test fun thePreviewDrawsAChosenLook() {
        runDesktopComposeUiTest(412, 915) {
            setContent {
                KitTheme(appearance = KitAppearance(mode = AppearanceMode.Dark), platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                    KitLiveLookPreview(onTap = {}, look = "caustics", strength = 1.6f)
                }
            }
            waitForIdle()
        }
    }
}
