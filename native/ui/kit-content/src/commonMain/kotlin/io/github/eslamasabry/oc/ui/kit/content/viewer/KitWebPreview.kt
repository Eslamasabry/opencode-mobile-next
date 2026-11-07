package io.github.eslamasabry.oc.ui.kit.content.viewer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.motion.KitPace
import io.github.eslamasabry.oc.ui.kit.motion.pace
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.devPreviewFailedBody
import io.github.eslamasabry.oc.ui.words.res.devPreviewFailedTitle
import io.github.eslamasabry.oc.ui.words.res.devPreviewRetry
import io.github.eslamasabry.oc.ui.words.res.rb23_devPreviewUnsupportedBody
import io.github.eslamasabry.oc.ui.words.res.rb23_devPreviewUnsupportedTitle
import org.jetbrains.compose.resources.stringResource

/** What the preview shows at the moment (Dart `KitWebPreviewPhase`, lib/ui/kit/kit_web_preview.dart). */
public enum class KitWebPreviewPhase { Loading, Ready, Failed }

/** True when the failed answer covers the page (Dart `failed = controller.phase == KitWebPreviewPhase.failed`). */
internal fun webPreviewShowsFailure(phase: KitWebPreviewPhase): Boolean = phase == KitWebPreviewPhase.Failed

/**
 * The frame of a web page from a dev server inside the app (a port of the drawing half of Dart `KitWebPreview`,
 * lib/ui/kit/kit_web_preview.dart). The platform web view and its hardening stay in `:android:webview`; the screen hands
 * it in as [page]. While [phase] is [KitWebPreviewPhase.Failed] the page is covered by the ground and a plain answer,
 * "Nothing is answering yet", with Try again ([onRetry]); the technical reason is never shown. The answer fades in on
 * the standard pace (the kit specs snap under reduced motion). Where the platform has no web view ([supported] false) it shows a
 * plain explanation with [unsupportedAction].
 *
 * States: error.
 */
@Composable
public fun KitWebPreview(
    phase: KitWebPreviewPhase,
    port: Int,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    supported: Boolean = true,
    unsupportedAction: KitAction? = null,
    page: @Composable () -> Unit = {},
) {
    if (!supported) {
        KitStateView(
            kind = KitStateKind.Empty,
            title = stringResource(Res.string.rb23_devPreviewUnsupportedTitle),
            body = stringResource(Res.string.rb23_devPreviewUnsupportedBody),
            icon = KitIcons.browser,
            primary = unsupportedAction,
            modifier = modifier.kitMaxWidth(CapRole.Reading),
        )
        return
    }
    val specs = KitMotion.specs
    Box(modifier.kitMaxWidth(CapRole.Reading).fillMaxSize()) {
        page()
        AnimatedVisibility(
            visible = webPreviewShowsFailure(phase),
            enter = fadeIn(specs.pace(KitPace.Standard)),
            exit = fadeOut(specs.pace(KitPace.Quick)),
            label = "kit.webPreview.failed",
        ) {
            val ground = LocalKitColors.current.ground
            // Dart ColoredBox(color: roles.ground): the page is covered, not framed.
            Box(Modifier.fillMaxSize().drawBehind { drawRect(ground) }) {
                KitStateView(
                    kind = KitStateKind.Empty,
                    title = stringResource(Res.string.devPreviewFailedTitle),
                    body = stringResource(Res.string.devPreviewFailedBody, port.toString()),
                    icon = KitIcons.browser,
                    primary = KitAction(stringResource(Res.string.devPreviewRetry), onRetry, icon = KitIcons.retry),
                )
            }
        }
    }
}
