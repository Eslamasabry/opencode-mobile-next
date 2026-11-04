package io.github.eslamasabry.oc.ui.kitchat.request

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.text.Numbers
import io.github.eslamasabry.oc.ui.design.motion.KitHaptic
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.choice.KitChoiceMark
import io.github.eslamasabry.oc.ui.kit.choice.KitChoiceRow
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitRequestMoreAnswers
import org.jetbrains.compose.resources.pluralStringResource

/**
 * A question stays in the same bounded card as a permission. Longer choice lists have a visible
 * path to all answers.
 */
@Composable
public fun KitQuestionCard(
    title: String,
    question: String,
    summary: String,
    choices: List<String>,
    onChoose: (String) -> Unit,
    answerLabel: String,
    onDetails: () -> Unit,
    inPlace: Boolean,
    modifier: Modifier = Modifier,
    other: (@Composable () -> Unit)? = null,
) {
    val shown = if (choices.size > MAX_CHOICES) choices.take(MAX_CHOICES - 1) else choices
    KitRequestFrame(
        modifier = modifier,
        answers = {
            if (!inPlace) KitButton(answerLabel, onDetails, Modifier.fillMaxWidth())
            else {
                if (choices.size > shown.size) {
                    val remaining = choices.size - shown.size
                    KitButton(
                        pluralStringResource(
                            Res.plurals.kitRequestMoreAnswers,
                            remaining,
                            Numbers.format(remaining),
                        ),
                        onDetails,
                        role = KitButtonRole.Tertiary,
                    )
                }
            }
        },
    ) {
        if (title.isNotBlank()) {
            KitText(
                title,
                Modifier.semantics {
                    heading()
                    liveRegion = LiveRegionMode.Polite
                },
                role = TypeRole.Headline,
                kind = KitTextKind.Reading,
            )
        }
        KitText(question, role = TypeRole.Body, kind = KitTextKind.Reading)
        KitText(summary, role = TypeRole.Secondary, tone = TextTone.Secondary)
        if (inPlace) {
            Column(verticalArrangement = Arrangement.spacedBy(Space.S1.dp)) {
                shown.forEach { option ->
                    KitChoiceRow(
                        title = option,
                        selected = false,
                        mark = KitChoiceMark.Radio,
                        haptic = KitHaptic.Send,
                        onClick = { onChoose(option) },
                    )
                }
                other?.invoke()
            }
        }
    }
}

private const val MAX_CHOICES: Int = 5
