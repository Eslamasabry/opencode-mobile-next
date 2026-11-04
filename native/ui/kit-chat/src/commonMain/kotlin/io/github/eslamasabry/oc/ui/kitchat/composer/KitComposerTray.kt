package io.github.eslamasabry.oc.ui.kitchat.composer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappable
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors

public enum class KitTrayKind {
    Photo,
    Camera,
    File,
    Clipboard,
}

/** The caller closes the tray before running an action, preserving the editor and its keyboard. */
public class KitTrayAction(
    public val label: String,
    public val kind: KitTrayKind,
    public val onPick: () -> Unit,
)

/** The attachment choices attached to the editor itself; whole labels wrap at large text sizes. */
@Composable
public fun KitComposerTray(actions: List<KitTrayAction>, modifier: Modifier = Modifier) {
    val colors = LocalKitColors.current
    Column(modifier.fillMaxWidth().testTag("kit-composer-tray")) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = Space.S2.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            actions.forEach { action ->
                KitTappable(action.onPick, Modifier.weight(1f), onClickLabel = action.label) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Space.S1.dp),
                    ) {
                        Box(
                            Modifier.size(48.dp).background(colors.surface3, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Canvas(Modifier.size(22.dp)) {
                                val stroke = Stroke(1.7.dp.toPx())
                                when (action.kind) {
                                    KitTrayKind.Photo -> {
                                        drawRect(
                                            colors.text1,
                                            Offset(size.width * .1f, size.height * .15f),
                                            Size(size.width * .8f, size.height * .7f),
                                            style = stroke,
                                        )
                                        drawCircle(
                                            colors.text1,
                                            size.width * .09f,
                                            Offset(size.width * .68f, size.height * .34f),
                                        )
                                        drawLine(
                                            colors.text1,
                                            Offset(size.width * .13f, size.height * .78f),
                                            Offset(size.width * .42f, size.height * .43f),
                                            stroke.width,
                                        )
                                        drawLine(
                                            colors.text1,
                                            Offset(size.width * .42f, size.height * .43f),
                                            Offset(size.width * .78f, size.height * .81f),
                                            stroke.width,
                                        )
                                    }
                                    KitTrayKind.Camera -> {
                                        drawRoundRect(
                                            colors.text1,
                                            Offset(size.width * .06f, size.height * .22f),
                                            Size(size.width * .88f, size.height * .62f),
                                            style = stroke,
                                        )
                                        drawCircle(
                                            colors.text1,
                                            size.width * .21f,
                                            center,
                                            style = stroke,
                                        )
                                        drawLine(
                                            colors.text1,
                                            Offset(size.width * .32f, size.height * .12f),
                                            Offset(size.width * .65f, size.height * .12f),
                                            stroke.width,
                                        )
                                    }
                                    KitTrayKind.File,
                                    KitTrayKind.Clipboard -> {
                                        drawRect(
                                            colors.text1,
                                            Offset(size.width * .22f, size.height * .12f),
                                            Size(size.width * .58f, size.height * .78f),
                                            style = stroke,
                                        )
                                        for (y in listOf(.42f, .59f, .75f)) drawLine(
                                            colors.text1,
                                            Offset(size.width * .35f, size.height * y),
                                            Offset(size.width * .67f, size.height * y),
                                            stroke.width,
                                        )
                                    }
                                }
                            }
                        }
                        KitText(
                            action.label,
                            role = TypeRole.Secondary,
                            tone = TextTone.Primary,
                            maxLines = 3,
                        )
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().size(1.dp).background(colors.hairline))
    }
}
