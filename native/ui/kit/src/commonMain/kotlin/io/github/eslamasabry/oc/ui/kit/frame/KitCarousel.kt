package io.github.eslamasabry.oc.ui.kit.frame

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitCarouselPlace
import org.jetbrains.compose.resources.stringResource

/**
 * A row of cards to swipe through, one page at a time, with where you are shown under it (Dart `KitCarousel`). The next
 * card peeks in. Up to [KitCarouselRules.DOTS_UP_TO] cards the place is dots; more shows a bar along a track.
 *
 * Cap: reading
 */
@Composable
public fun KitCarousel(
    semanticsLabel: String,
    itemCount: Int,
    itemHeight: Float,
    modifier: Modifier = Modifier,
    initialIndex: Int = 0,
    share: Float = KitCarouselRules.CARD_SHARE,
    showPlace: Boolean = true,
    growPerTextStep: Float = 140f, // F: lib/ui/kit/kit_carousel.dart:31
    item: @Composable (Int) -> Unit,
) {
    require(itemCount > 0) { "itemCount" }
    require(share > 0f && share <= 1f) { "share" }
    val tokens = LocalKitTokens.current
    val colors = LocalKitColors.current
    val scale = LocalDensity.current.fontScale
    val height = itemHeight + (scale - 1f).coerceIn(0f, 1.5f) * growPerTextStep // F: lib/ui/kit/kit_carousel.dart:110
    val state = rememberPagerState(initialPage = initialIndex.coerceIn(0, itemCount - 1)) { itemCount }
    val nearest = state.currentPage.coerceIn(0, itemCount - 1)
    val place = stringResource(Res.string.kitCarouselPlace, nearest + 1, itemCount)
    val fraction = share
    val pageSize = object : PageSize {
        override fun Density.calculateMainAxisPageSize(availableSpace: Int, pageSpacing: Int): Int =
            (availableSpace * fraction).toInt()
    }
    Column(
        modifier.kitMaxWidth(CapRole.Reading).semantics { contentDescription = semanticsLabel; stateDescription = place },
    ) {
        HorizontalPager(
            state = state,
            pageSize = pageSize,
            modifier = Modifier.fillMaxWidth().height(height.dp).padding(start = tokens.gutter.dp),
        ) { i ->
            Box(Modifier.padding(end = Space.S3.dp, top = Space.S1.dp, bottom = Space.S1.dp)) { item(i) }
        }
        if (showPlace) {
            Box(Modifier.height(Space.S3.dp))
            if (itemCount <= KitCarouselRules.DOTS_UP_TO) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    for (i in 0 until itemCount) {
                        Box(
                            Modifier.padding(horizontal = Space.S1.dp)
                                .width((if (i == nearest) Space.S5 else Space.S2).dp)
                                .height(Space.S2.dp)
                                .background(if (i == nearest) colors.accent else colors.surface3, RoundedCornerShape(Space.S2.dp)),
                        )
                    }
                }
            } else {
                val track = (Space.S6 * 3).dp // F: lib/ui/kit/kit_carousel.dart:138
                val bar = track / itemCount * 2 // F: lib/ui/kit/kit_carousel.dart:139
                val t = if (itemCount <= 1) 0f else nearest.toFloat() / (itemCount - 1)
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    Box(Modifier.width(track).height(Space.S2.dp).background(colors.surface3, RoundedCornerShape(Space.S2.dp))) {
                        Box(
                            Modifier.padding(start = (track - bar) * t).width(bar).height(Space.S2.dp)
                                .background(colors.accent, RoundedCornerShape(Space.S2.dp)),
                        )
                    }
                }
            }
        }
    }
}

public object KitCarouselRules {
    /** More cards than this show the place as a bar, not dots. */
    public const val DOTS_UP_TO: Int = 8 // F: lib/ui/kit/kit_carousel.dart:64

    /** How much of the width one card takes (the rest shows the next). */
    public const val CARD_SHARE: Float = .62f // F: lib/ui/kit/kit_carousel.dart:67
}
