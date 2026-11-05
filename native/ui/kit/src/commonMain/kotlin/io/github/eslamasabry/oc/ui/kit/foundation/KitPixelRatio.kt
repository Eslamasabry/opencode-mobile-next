package io.github.eslamasabry.oc.ui.kit.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens

/**
 * The device pixel ratio the kit's physical-pixel tokens follow: Flutter's `MediaQuery.devicePixelRatioOf(context)`.
 * Null (every real device) means the composition's own density. The golden gate sets the ratio of the Flutter test's
 * view (3 in the kit gallery) while it renders at the picture's density (1), as Flutter rasterises a DPR-3 scene into
 * a 1x golden: a hairline is then a third of a pixel there too.
 */
public val LocalKitPixelRatio: ProvidableCompositionLocal<Float?> = staticCompositionLocalOf { null }

/** The ratio in force: [LocalKitPixelRatio], else the density. */
@Composable
@ReadOnlyComposable
public fun kitPixelRatio(): Float = LocalKitPixelRatio.current ?: LocalDensity.current.density

/** `KitTokens.hairlineWidth`: one physical pixel, in dp. */
@Composable
@ReadOnlyComposable
public fun kitHairline(): Dp = (FlutterKitTokens.HAIRLINE_WIDTH_PHYSICAL_PX / kitPixelRatio()).dp // F: lib/ui/kit/kit_tokens.dart:315-318

/** `KitTokens.focusRingWidth`: two physical pixels, in dp. */
@Composable
@ReadOnlyComposable
public fun kitFocusRingWidth(): Dp = (FlutterKitTokens.FOCUS_RING_WIDTH_PHYSICAL_PX / kitPixelRatio()).dp // F: lib/ui/kit/kit_tokens.dart:321-325
