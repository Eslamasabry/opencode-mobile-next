package io.github.eslamasabry.oc.feature.chatcomposer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.chatcomposer.presentation.ComposerActions
import io.github.eslamasabry.oc.feature.chatcomposer.presentation.FieldValue
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors

/** The real kit theme on the desktop platform over the theme's ground, still frame, motion off, at the phone width of plan 06 §15.3. */
@Composable
fun ComposerThemed(dark: Boolean = false, locale: String = "en", content: @Composable () -> Unit) {
    KitTheme(
        appearance = KitAppearance(mode = if (dark) AppearanceMode.Dark else AppearanceMode.Light, motion = MotionLevel.Off),
        platform = DesktopKitPlatform(),
        localeTag = locale,
        stillFrame = true,
    ) {
        Box(Modifier.width(412.dp).background(LocalKitColors.current.ground)) { content() }
    }
}

/** Actions that only record, for screens rendered from canned state. */
class RecordingActions : ComposerActions {
    val calls = mutableListOf<String>()

    override fun onTextChanged(text: String) {
        calls += "text:$text"
    }

    override fun onSend() {
        calls += "send"
    }

    override fun onRetry() {
        calls += "retry"
    }

    override fun onStop() {
        calls += "stop"
    }

    override fun onHistoryKey(older: Boolean, value: FieldValue, suggestionsOpen: Boolean): Boolean {
        calls += "history"
        return false
    }

    override fun onEditApplied(id: Long) {
        calls += "applied:$id"
    }

    override fun onModelChip() {
        calls += "model"
    }

    override fun onApprovalChip() {
        calls += "approval"
    }

    override fun onMic() {
        calls += "mic"
    }
}
