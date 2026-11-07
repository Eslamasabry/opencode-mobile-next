package io.github.eslamasabry.oc.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.analytics.AnalyticsSink
import io.github.eslamasabry.oc.engine.analytics.Delivery
import io.github.eslamasabry.oc.engine.analytics.SinkId
import io.github.eslamasabry.oc.engine.analytics.SinkKind
import io.github.eslamasabry.oc.engine.analytics.WireBatch
import io.github.eslamasabry.oc.feature.settings.presentation.NotificationChange
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsActions
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsOutside
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPage
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsRowId
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsSearchEntry
import io.github.eslamasabry.oc.feature.settings.presentation.SurfaceStyle
import io.github.eslamasabry.oc.ui.design.color.PackId
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor

/** The real kit theme on the desktop platform over the theme's ground, still frame, motion off, at plan 06 §15.3's phone size. */
@Composable
fun SettingsThemed(dark: Boolean = false, locale: String = "en", fontScale: Float = 1f, content: @Composable () -> Unit) {
    // The person's text size (1.3 = Android's "Large"): the kit reads it from the density's font scale.
    CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
    KitTheme(
        appearance = KitAppearance(mode = if (dark) AppearanceMode.Dark else AppearanceMode.Light, motion = MotionLevel.Off),
        platform = DesktopKitPlatform(),
        localeTag = locale,
        stillFrame = true,
    ) {
        // The app provides its redactor around every screen; the Library's mono parts refuse to draw without it.
        CompositionLocalProvider(LocalKitRedactor provides Redactor()) {
            Box(Modifier.size(412.dp, 915.dp).background(LocalKitColors.current.ground)) { content() }
        }
    }
    }
}

/** A sink that would send, so the DETAILED switch shows (plan 16 §5.8). Never called in these tests. */
object RemoteSink : AnalyticsSink {
    override val id: SinkId = SinkId.Custom
    override val kind: SinkKind = SinkKind.Remote
    override val host: String = "collect.invalid"

    override suspend fun send(batch: WireBatch): Outcome<Delivery> = Outcome.Ok(Delivery(""))
}

/** Actions that only record, for screens rendered from canned state. */
class RecordingActions : SettingsActions {
    val calls = mutableListOf<String>()

    override fun onOpen(page: SettingsPage) { calls += "open:$page" }

    override fun onBack(): Boolean { calls += "back"; return true }

    override fun onRow(row: SettingsRowId) { calls += "row:$row" }

    override fun onQuery(text: String) { if (text.isNotEmpty()) calls += "query:$text" }

    override fun onSearchEntries(entries: List<SettingsSearchEntry>) = Unit

    override fun onOutside(place: SettingsOutside) { calls += "outside:$place" }

    override fun onMode(mode: AppearanceMode) { calls += "mode:$mode" }

    override fun onPack(pack: PackId) { calls += "pack:$pack" }

    override fun onSeeAllPacks() { calls += "seeAll" }

    override fun onStyle(style: SurfaceStyle) { calls += "style:$style" }

    override fun onMotion(motion: MotionLevel) { calls += "motion:$motion" }

    override fun onReadAloud(on: Boolean) { calls += "readAloud:$on" }

    override fun onNotification(change: NotificationChange) { calls += "notify:$change" }

    override fun onSendTestNotification() { calls += "test" }

    override fun onCheckForUpdates() { calls += "check" }

    override fun onUpdateChecks(on: Boolean) { calls += "checks:$on" }

    override fun onUpdateAction() { calls += "act" }

    override fun onDetailedUsage(on: Boolean) { calls += "detailed:$on" }

    override fun onNoteSeen() { calls += "noteSeen" }
}
