package io.github.eslamasabry.oc.ui.kit

import android.os.Build
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.FrostSpec
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Frost below API 31: the platform gives no effect and the header draws solid (plan 06 K12 fallback). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class KitFrostFallbackGoldenTest {
    @get:Rule val compose = createComposeRule()

    private val spec = FrostSpec(sigmaDp = 10f, saturation = 1.4f, tintAlpha = 0.8f)

    @Test
    @Config(sdk = [30], qualifiers = KitRobolectric.PHONE)
    fun `B-KIT-018 below API 31 frost is solid`() {
        assertNull(KitRobolectric.platform().frostEffect(spec, 2.625f))
        compose.setContent {
            KitTheme(
                appearance = KitAppearance(mode = AppearanceMode.Light, motion = MotionLevel.Full),
                platform = KitRobolectric.platform(),
                localeTag = "en",
                stillFrame = true,
            ) { FrostScene(SceneWords.En) }
        }
        compose.onNodeWithTag("header").captureRoboImage("src/screenshots/KitFrost/header-api30-light-en.png")
    }

    @Test
    @Config(sdk = [KitRobolectric.SDK], qualifiers = KitRobolectric.PHONE)
    fun `B-KIT-018 on API 31 and later the platform gives a blur effect`() {
        check(Build.VERSION.SDK_INT >= 31)
        assertNotNull(KitRobolectric.platform().frostEffect(spec, 2.625f))
    }
}
