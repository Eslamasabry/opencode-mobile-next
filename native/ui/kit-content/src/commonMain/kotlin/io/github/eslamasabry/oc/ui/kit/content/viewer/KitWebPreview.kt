package io.github.eslamasabry.oc.ui.kit.content.viewer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.devPreviewFailedBody
import io.github.eslamasabry.oc.ui.words.res.devPreviewFailedTitle
import io.github.eslamasabry.oc.ui.words.res.devPreviewRetry
import io.github.eslamasabry.oc.ui.words.res.devPreviewUnsupportedBody
import io.github.eslamasabry.oc.ui.words.res.devPreviewUnsupportedTitle
import org.jetbrains.compose.resources.stringResource

/** What the preview shows at the moment (Dart `KitWebPreviewPhase`). */
public enum class KitWebPreviewPhase { Loading, Ready, Failed }

/**
 * The drawn part of a dev-server page inside the app (Dart `KitWebPreview`, lib/ui/kit/kit_web_preview.dart). The page
 * itself is the platform web view the caller passes as [page] (`:android:webview` `KitWebPreview`, which keeps every
 * rule of B-SEC-058: one origin, no bridge, cleared storage, no file access); this part never builds a web view. While
 * the page loads the screen shows its loading bar from [phase]; when nothing answers ([KitWebPreviewPhase.Failed]) the
 * part covers the page with plain words naming the [port] and Try again ([onRetry]), cross-faded in over STANDARD
 * (instantly under reduced motion). The technical reason is never shown. With [page] null (no web view on this
 * platform) it shows a plain explanation with [unsupportedAction].
 *
 * States: error
 * Text: reading
 * Cap: Reading (720)
 * Motion: the failed state fades in over STANDARD on Enter; Off = instant
 * Replaces: KitWebPreview's drawn part (lib/ui/kit/kit_web_preview.dart)
 */
@Composable
public fun KitWebPreview(
    port: Int,
    phase: KitWebPreviewPhase,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    unsupportedAction: KitAction? = null,
    page: (@Composable () -> Unit)?,
) {
    if (page == null) {
        KitStateView(
            KitStateKind.Empty,
            stringResource(Res.string.devPreviewUnsupportedTitle),
            modifier.kitMaxWidth(CapRole.Reading),
            body = stringResource(Res.string.devPreviewUnsupportedBody),
            icon = KitIcons.browser,
            primary = unsupportedAction,
        )
        return
    }
    val specs = KitMotion.specs
    Box(modifier.kitMaxWidth(CapRole.Reading).fillMaxSize()) {
        page()
        AnimatedVisibility(
            visible = phase == KitWebPreviewPhase.Failed,
            modifier = Modifier.fillMaxSize(),
            enter = fadeIn(specs.enter()),
            exit = fadeOut(specs.enter()),
            label = "kit.webPreview.failed",
        ) {
            Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground), contentAlignment = Alignment.Center) {
                KitStateView(
                    KitStateKind.Empty,
                    stringResource(Res.string.devPreviewFailedTitle),
                    body = stringResource(Res.string.devPreviewFailedBody, port.toString()),
                    icon = KitIcons.browser,
                    primary = KitAction(stringResource(Res.string.devPreviewRetry), onRetry, icon = KitIcons.retry),
                )
            }
        }
    }
}
