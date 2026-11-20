package io.github.eslamasabry.oc.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.feature.settings.presentation.GroupUi
import io.github.eslamasabry.oc.feature.settings.presentation.HubUi
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsGroupId
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPage
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsUi
import io.github.eslamasabry.oc.feature.settings.ui.SettingsScreen
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.KitWindowInfo
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitWindow
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlinx.collections.immutable.persistentListOf

/**
 * Pictures only (asserts nothing; BS-SHARED-FRAME): the real Settings screen with demo data, ten pages at a phone
 * (360, 412), a tablet (1280 x 800) and a tablet standing up (800 x 1280), dark, into `~/render-out/shared-frame/`.
 * The phone sizes are the before/after pixel proof; the tablet sizes show the bands.
 */
@OptIn(ExperimentalTestApi::class)
class SharedFrameRenders {
    private val out =
        File(System.getProperty("user.home"), "render-out").takeIf { it.isDirectory }?.resolve("shared-frame")?.also(File::mkdirs)

    private val pages =
        listOf(
            "hub" to SettingsPage.Hub,
            "group" to SettingsPage.Group(SettingsGroupId.ThisApp),
            "notifications" to SettingsPage.Notifications,
            "privacy" to SettingsPage.Privacy,
            "collect" to SettingsPage.WhatWeCollect,
            "about" to SettingsPage.About,
            "updates" to SettingsPage.Updates,
            "storage" to SettingsPage.Storage,
            "phrases" to SettingsPage.PhraseChips,
            "appearance" to SettingsPage.Appearance,
        )

    private val groups =
        persistentListOf(
            SettingsGroupId.Server,
            SettingsGroupId.Agent,
            SettingsGroupId.Conversations,
            SettingsGroupId.ThisApp,
            SettingsGroupId.Help,
        )

    private fun shoot(name: String, page: SettingsPage, w: Int, h: Int, density: Float, key: String) {
        val state =
            SettingsUi(
                stack = if (page == SettingsPage.Hub) persistentListOf(SettingsPage.Hub) else persistentListOf(SettingsPage.Hub, page),
                hub = HubUi(groups = groups),
                firstGroup = GroupUi(SettingsGroupId.Server, persistentListOf()),
            )
        runDesktopComposeUiTest((w * density).toInt(), (h * density).toInt()) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(density)) {
                    KitTheme(
                        appearance = KitAppearance(mode = AppearanceMode.Dark, motion = MotionLevel.Off),
                        platform = DesktopKitPlatform(),
                        localeTag = "en",
                        stillFrame = true,
                    ) {
                        CompositionLocalProvider(
                            LocalKitRedactor provides Redactor(),
                            LocalKitWindow provides KitWindowInfo(w.toFloat(), h.toFloat()),
                        ) {
                            Box(Modifier.size(w.dp, h.dp)) { SettingsScreen(state, RecordingActions()) }
                        }
                    }
                }
            }
            waitForIdle()
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(out, "$name-$key.png"))
        }
    }

    @Test
    fun `shared frame renders`() {
        if (out == null) return
        Locale.setDefault(Locale.ENGLISH)
        for ((name, page) in pages) {
            shoot(name, page, 360, 780, 2f, "phone")
            shoot(name, page, 412, 915, 2f, "phone412")
            shoot(name, page, 1280, 800, 1f, "tabl")
            shoot(name, page, 800, 1280, 1f, "tabp")
        }
    }
}
