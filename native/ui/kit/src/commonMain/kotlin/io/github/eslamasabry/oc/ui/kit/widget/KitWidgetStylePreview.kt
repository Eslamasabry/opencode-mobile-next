package io.github.eslamasabry.oc.ui.kit.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.foundation.toShape

/**
 * A cosmetic widget preview, using the RemoteViews renderer's resolved palette. The bars are deliberately a
 * drawing, not invented conversation or usage data. Wide and square show how the same colours read at both sizes.
 */
@Composable
public fun KitWidgetStylePreview(
    background: Int,
    text: Int,
    muted: Int,
    accent: Int,
    wide: Boolean,
    semanticsLabel: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = if (wide) 360.dp else 180.dp)
                .fillMaxWidth()
                .clip(KitShapeId.Panel.toShape())
                .background(Color(background))
                .padding(Space.S4.dp)
                .testTag("widget-style-preview")
                .semantics { contentDescription = semanticsLabel },
            verticalArrangement = Arrangement.spacedBy(Space.S3.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
                Box(Modifier.size(10.dp).clip(KitShapeId.Circle.toShape()).background(Color(accent)))
                Box(Modifier.fillMaxWidth(0.7f).height(8.dp).clip(KitShapeId.Pill.toShape()).background(Color(text)))
            }
            Box(Modifier.fillMaxWidth(0.9f).height(6.dp).clip(KitShapeId.Pill.toShape()).background(Color(muted)))
            Box(Modifier.fillMaxWidth(0.6f).height(6.dp).clip(KitShapeId.Pill.toShape()).background(Color(muted)))
            Box(Modifier.fillMaxWidth().height(4.dp).clip(KitShapeId.Pill.toShape()).background(Color(accent)))
        }
    }
}
