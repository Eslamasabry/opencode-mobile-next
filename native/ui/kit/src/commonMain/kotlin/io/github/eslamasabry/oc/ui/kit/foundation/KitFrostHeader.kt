package io.github.eslamasabry.oc.ui.kit.foundation

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.FrostRole
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens

/**
 * The chat header's glass (owner, 2026-10-30): solid at rest, frosted while the conversation is scrolled under it.
 * lib/ui/kit/kit_frost_header.dart.
 *
 * A page with a bar ([KitFrostHeader.Frame]) paints its content first and its bar over it, and offers this host to its
 * body. A floating composer layer in that body measures how far its own top is from the bar's; when nothing sits
 * between them (no status line, no loading bar running) it lets its body run up under the bar by that much
 * ([extendUp], in px) and says whether content is scrolled under it ([under]).
 */
@Stable
public class KitFrostHeaderHost {
    /** How far the body under the bar is run up under it, in px; 0 keeps it below. */
    public var extendUp: Float by mutableFloatStateOf(0f)

    /** Content is scrolled under the bar now. */
    public var under: Boolean by mutableStateOf(false)

    /** The page's loading bar runs (between the bar and the body): no glass. */
    public var loading: Boolean = false

    /** The bar's place in the page frame, set while the frame lays it out. */
    internal var bar: LayoutCoordinates? = null

    /** The glass is shown: the body runs up under the bar and content is under it (kit_frost_header.dart:176). */
    public val frosted: Boolean
        get() = extendUp > 0f && under

    /** A composer layer leaving the page: back to solid (Dart runs it after the frame; Compose state is snapshot-safe). */
    public fun release() {
        extendUp = 0f
        under = false
    }
}

/** Set when a body reads [KitFrostHeader.topClearance] under a [KitFrostHeader.Clearance]; one per body. */
public class KitFrostTopClaim {
    public var honoured: Boolean = false
        internal set
}

private class TopClearance(val value: Dp, val claim: KitFrostTopClaim)

private val LocalKitFrostTopClearance = compositionLocalOf<TopClearance?> { null }
private val LocalKitFrostHeaderHost = staticCompositionLocalOf<KitFrostHeaderHost?> { null }

public object KitFrostHeader {
    /**
     * A page's [bar] over its [content] (the page frame), frosted while content is under it; [extendTop] is the status
     * bar above it, through which the glass runs up. [state] holds what scrolls behind the glass ([kitFrostSource]).
     */
    @Composable
    public fun Frame(
        host: KitFrostHeaderHost,
        state: KitFrostState,
        bar: @Composable () -> Unit,
        modifier: Modifier = Modifier,
        extendTop: Dp = 0.dp,
        content: @Composable () -> Unit,
    ) {
        Scope(host) {
            BarOverContent(
                modifier = modifier,
                bar = {
                    FrostBand(host, state, extendTop) {
                        Box(Modifier.onGloballyPositioned { host.bar = it }) { bar() }
                    }
                },
                content = content,
            )
        }
    }

    /** Publishes the top clearance [value] to a body (the composer layer around its body); [claim] records the read. */
    @Composable
    public fun Clearance(value: Dp, claim: KitFrostTopClaim, content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalKitFrostTopClearance provides TopClearance(value, claim), content = content)
    }

    /** The host of the page this composition is on, or null (no bar). */
    @Composable
    public fun hostOf(): KitFrostHeaderHost? = LocalKitFrostHeaderHost.current

    /**
     * How much of the top of this body is under the frosted bar: a scroll view adds it to its top padding so its first
     * item clears the bar. Reading it is the body's opt-in to running under the bar. 0 outside a [Clearance].
     */
    @Composable
    public fun topClearance(): Dp {
        val scope = LocalKitFrostTopClearance.current ?: return 0.dp
        scope.claim.honoured = true
        return scope.value
    }

    /** [host] around [content] (the page frame). */
    @Composable
    public fun Scope(host: KitFrostHeaderHost, content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalKitFrostHeaderHost provides host, content = content)
    }

    /** Whether the body under [claim] has read [topClearance]. */
    public fun honoured(claim: KitFrostTopClaim): Boolean = claim.honoured

    /**
     * How far [body] (a composer layer's box) may run up under [host]'s bar, in px: the distance from the bar's top to
     * its own when it sits straight under the bar; else 0. [loadingBarPx] is the idle loading bar's height in px.
     */
    public fun extendUpFor(host: KitFrostHeaderHost, body: LayoutCoordinates, loadingBarPx: Float): Float {
        val bar = host.bar ?: return 0f
        if (!bar.isAttached || !body.isAttached) return 0f
        val barTop = bar.localToRoot(androidx.compose.ui.geometry.Offset.Zero).y
        val bodyTop = body.localToRoot(androidx.compose.ui.geometry.Offset.Zero).y
        return extendUp(barTop, bar.size.height.toFloat(), bodyTop, loadingBarPx)
    }

    /**
     * The measure behind [extendUpFor] (kit_frost_header.dart:264-279): straight under the bar, with nothing between
     * them but the idle loading bar, the body runs up by the distance between the two tops.
     */
    public fun extendUp(barTop: Float, barHeight: Float, bodyTop: Float, loadingBarPx: Float): Float {
        val gap = bodyTop - (barTop + barHeight)
        return if (gap > -.5f && gap <= loadingBarPx + .5f) bodyTop - barTop else 0f // F: lib/ui/kit/kit_frost_header.dart:276
    }

    /** The idle loading bar's height in dp (Dart `KitTokens.loadingBarHeight`). */
    public val loadingBarHeight: Dp = FlutterKitTokens.LOADING_BAR_HEIGHT.dp
}

/** The bar on its frost pane: the pane runs [extendTop] up through the status bar; the bar keeps its own height. */
@Composable
private fun FrostBand(host: KitFrostHeaderHost, state: KitFrostState, extendTop: Dp, bar: @Composable () -> Unit) {
    val frosted = host.frosted
    Layout(
        content = {
            Box(Modifier.kitFrost(FrostRole.ChatHeader, state, shown = frosted))
            Box { bar() }
        }
    ) { measurables, constraints ->
        val top = extendTop.roundToPx()
        val barPlaceable = measurables[1].measure(constraints.copy(minHeight = 0))
        val pane =
            measurables[0].measure(Constraints.fixed(barPlaceable.width, barPlaceable.height + top))
        layout(barPlaceable.width, barPlaceable.height) {
            pane.place(0, -top)
            barPlaceable.place(0, 0)
        }
    }
}

/**
 * A column of [bar] then [content] (the bar at its own height, the content filling the rest), in that order for
 * semantics and focus, but painted content first, so a body run up under the bar passes behind it
 * (kit_frost_header.dart:193-261). Compose draws and hit-tests children in placement order, last on top.
 */
@Composable
private fun BarOverContent(modifier: Modifier, bar: @Composable () -> Unit, content: @Composable () -> Unit) {
    Layout(content = { Box { bar() }; Box { content() } }, modifier = modifier) { measurables, constraints ->
        val width = constraints.maxWidth
        val barPlaceable = measurables[0].measure(Constraints(minWidth = width, maxWidth = width))
        val height = if (constraints.hasBoundedHeight) constraints.maxHeight else barPlaceable.height
        val below = (height - barPlaceable.height).coerceIn(0, height)
        val contentPlaceable = measurables[1].measure(Constraints.fixed(width, below))
        layout(width, height) {
            contentPlaceable.place(0, barPlaceable.height)
            barPlaceable.place(0, 0)
        }
    }
}
