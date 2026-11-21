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
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.feature.settings.presentation.*
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

    private val thisAppRows =
        persistentListOf(
            SettingsRowUi(SettingsRowId.Notifications, SettingsDoor.Page(SettingsPage.Notifications)),
            SettingsRowUi(SettingsRowId.Appearance, SettingsDoor.Page(SettingsPage.Appearance)),
            SettingsRowUi(SettingsRowId.Language, SettingsDoor.Page(SettingsPage.Language)),
            SettingsRowUi(SettingsRowId.Privacy, SettingsDoor.Page(SettingsPage.Privacy)),
            SettingsRowUi(SettingsRowId.Updates, SettingsDoor.Page(SettingsPage.Updates)),
            SettingsRowUi(SettingsRowId.Storage, SettingsDoor.Page(SettingsPage.Storage)),
        )
    private val helpRows =
        persistentListOf(
            SettingsRowUi(SettingsRowId.Guide, SettingsDoor.Outside(SettingsOutside.Guide)),
            SettingsRowUi(SettingsRowId.Tips, SettingsDoor.Outside(SettingsOutside.Tips)),
            SettingsRowUi(SettingsRowId.ReportProblem, SettingsDoor.Outside(SettingsOutside.ReportProblem)),
            SettingsRowUi(SettingsRowId.About, SettingsDoor.Page(SettingsPage.About)),
        )
    private val localData =
        LocalDataFacts(
            queued = 3,
            queueBytes = 18 * 1024L,
            drafts = 2,
            draftBytes = 3 * 1024L,
            servers = persistentListOf(ProfileId("laptop")),
        )

    private val groups =
        persistentListOf(
            SettingsGroupId.Server,
            SettingsGroupId.Agent,
            SettingsGroupId.Conversations,
            SettingsGroupId.ThisApp,
            SettingsGroupId.Help,
        )

    /** Real rows: the page's own facts filled, the group's rows under it, as the presenter hands them over. */
    private fun scene(name: String): SettingsUi {
        val thisApp = GroupUi(SettingsGroupId.ThisApp, thisAppRows)
        val help = GroupUi(SettingsGroupId.Help, helpRows)
        val hub = HubUi(groups = groups, server = ServerLine("Laptop", false))
        fun under(group: GroupUi, page: SettingsPage) =
            SettingsUi(
                stack = persistentListOf(SettingsPage.Hub, SettingsPage.Group(group.id), page),
                hub = hub,
                listGroup = group,
                firstGroup = GroupUi(SettingsGroupId.Server, persistentListOf()),
            )
        return when (name) {
            "hub" -> SettingsUi(hub = hub, firstGroup = GroupUi(SettingsGroupId.Server, persistentListOf()))
            "group" ->
                SettingsUi(
                    stack = persistentListOf(SettingsPage.Hub, SettingsPage.Group(SettingsGroupId.ThisApp)),
                    hub = hub,
                    group = thisApp,
                )
            "help" ->
                SettingsUi(
                    stack = persistentListOf(SettingsPage.Hub, SettingsPage.Group(SettingsGroupId.Help)),
                    hub = hub,
                    group = help,
                )
            "notifications" ->
                under(thisApp, SettingsPage.Notifications).copy(
                    notifications =
                        NotificationFacts(
                            blocked = true,
                            usageAlertsShown = true,
                            profiles = persistentListOf(NotificationProfile(ProfileId("laptop"), "Laptop", false, true)),
                            background = BackgroundState.Off,
                        )
                )
            "privacy" -> under(thisApp, SettingsPage.Privacy).copy(localData = LocalDataUi(localData))
            "privacy-confirm" ->
                under(thisApp, SettingsPage.Privacy).copy(
                    localData = LocalDataUi(localData, confirm = LocalDataKind.entries.first())
                )
            "storage" ->
                under(thisApp, SettingsPage.Storage).copy(
                    localModels = LocalModelStorageFacts(loading = false, present = true, size = "1.2 GB")
                )
            "about" -> under(help, SettingsPage.About).copy(about = AboutFacts("1.0.44", "52"))
            "updates" -> under(thisApp, SettingsPage.Updates)
            else -> under(thisApp, SettingsPage.Appearance)
        }
    }

    private val pages =
        listOf("hub", "group", "help", "notifications", "privacy", "privacy-confirm", "storage", "about", "updates", "appearance")

    private fun shoot(name: String, w: Int, h: Int, density: Float, key: String) {
        val state = scene(name)
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
        for (name in pages) {
            shoot(name, 360, 780, 2f, "phone")
            shoot(name, 412, 915, 2f, "phone412")
            shoot(name, 1280, 800, 1f, "tabl")
            shoot(name, 800, 1280, 1f, "tabp")
        }
    }
}
