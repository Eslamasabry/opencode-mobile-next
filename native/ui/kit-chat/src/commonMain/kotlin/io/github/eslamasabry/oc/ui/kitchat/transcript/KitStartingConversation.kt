package io.github.eslamasabry.oc.ui.kitchat.transcript

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.chip.KitChip
import io.github.eslamasabry.oc.ui.kit.frame.*
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens

/** The first chat frame appears before creation. The exact draft remains owned by its ViewModel. */
@Composable
public fun KitStartingConversation(
    text: String,
    project: String,
    model: String,
    status: String,
    backLabel: String,
    onBack: () -> Unit,
    retryLabel: String? = null,
    onRetry: (() -> Unit)? = null,
    reviewLabel: String? = null,
    onReview: (() -> Unit)? = null,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { keyboard?.hide() }
    KitScreen(
        topBar = {
            KitTopBar(
                title = text.lineSequence().firstOrNull().orEmpty().take(80),
                subtitle = project,
                exit = KitTopBarExit.Back,
                exitLabel = backLabel,
                onExit = onBack,
            )
        },
        bottom = {
            Row(
                Modifier.padding(
                    horizontal = LocalKitTokens.current.gutter.dp,
                    vertical = Space.S2.dp,
                )
            ) {
                KitChip(model)
            }
        },
    ) {
        Column(
            Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = LocalKitTokens.current.gutter.dp, vertical = Space.S4.dp),
            verticalArrangement = Arrangement.spacedBy(Space.S2.dp),
            horizontalAlignment = Alignment.End,
        ) {
            KitPromptBubble(text)
            KitText(status, role = TypeRole.Secondary)
            if (onRetry != null && retryLabel != null)
                KitButton(retryLabel, onRetry, role = KitButtonRole.Secondary)
            if (onReview != null && reviewLabel != null)
                KitButton(reviewLabel, onReview, role = KitButtonRole.Secondary)
        }
    }
}
