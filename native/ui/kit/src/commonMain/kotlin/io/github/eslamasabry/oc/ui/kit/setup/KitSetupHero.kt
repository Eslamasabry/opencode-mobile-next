package io.github.eslamasabry.oc.ui.kit.setup

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.action.KitActionBlock
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens

public enum class KitSetupPhoneMood {
    Fresh,
    Ready,
    Stopped,
    Termux,
    Starting,
    Celebration,
}

/** Top-anchored PhoneSetupHero, following phone_setup_hero.dart's slot order and state measure. */
@Composable
public fun KitSetupHero(
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    mood: KitSetupPhoneMood = KitSetupPhoneMood.Fresh,
    primary: KitAction? = null,
    secondary: KitAction? = null,
    tertiary: List<KitAction> = emptyList(),
    content: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
) {
    val tokens = LocalKitTokens.current
    Box(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(
                start = tokens.gutter.dp,
                end = tokens.gutter.dp,
                top = Space.S2.dp,
                bottom = Space.S6.dp,
            ),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(Modifier.kitMaxWidth(CapRole.State).fillMaxWidth()) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                KitSetupPhone(mood)
            }
            Spacer(Modifier.height(Space.S5.dp))
            KitText(title, role = TypeRole.Title)
            if (body != null) {
                Spacer(Modifier.height(Space.S2.dp))
                KitText(body, tone = TextTone.Secondary)
            }
            content?.let {
                Spacer(Modifier.height(Space.S4.dp))
                it()
            }
            if (primary != null || secondary != null || tertiary.isNotEmpty()) {
                Spacer(Modifier.height(Space.S6.dp))
                primary?.let { KitButton(it, fullWidth = true, hero = true) }
                if (secondary != null || tertiary.isNotEmpty()) {
                    Spacer(Modifier.height(Space.S2.dp))
                    KitActionBlock(secondary = secondary, tertiary = tertiary)
                }
            }
            footer?.let {
                Spacer(Modifier.height(Space.S4.dp))
                it()
            }
        }
    }
}
