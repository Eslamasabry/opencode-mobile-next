package io.github.eslamasabry.oc.feature.chatcomposer.ui

import androidx.compose.runtime.Composable
import io.github.eslamasabry.oc.feature.chatcomposer.presentation.ComposerChip
import io.github.eslamasabry.oc.feature.chatcomposer.presentation.EffortWord
import io.github.eslamasabry.oc.feature.chatcomposer.presentation.ModelLabel
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.agentsModelLoading
import io.github.eslamasabry.oc.ui.words.res.chatNoModelSignedIn
import io.github.eslamasabry.oc.ui.words.res.modelEffortExtraHigh
import io.github.eslamasabry.oc.ui.words.res.modelEffortHigh
import io.github.eslamasabry.oc.ui.words.res.modelEffortLow
import io.github.eslamasabry.oc.ui.words.res.modelEffortMax
import io.github.eslamasabry.oc.ui.words.res.modelEffortMedium
import io.github.eslamasabry.oc.ui.words.res.modelEffortMinimal
import io.github.eslamasabry.oc.ui.words.res.modelEffortNone
import io.github.eslamasabry.oc.ui.words.res.modelNewBadge
import org.jetbrains.compose.resources.stringResource

/** The model chip's text: name, then effort as a word, then the "New" mark, joined with " · " (plan 09 §10.3). */
@Composable
internal fun modelChipWords(chip: ComposerChip.Model): String {
    val parts = mutableListOf(modelLabelWords(chip.label))
    chip.effort?.let { parts += effortWords(it) }
    if (chip.isNew) parts += stringResource(Res.string.modelNewBadge)
    return parts.joinToString(" · ")
}

@Composable
internal fun modelLabelWords(label: ModelLabel): String =
    when (label) {
        is ModelLabel.Named -> label.name
        // TODO(words): plan 09 §10.3 says "Loading…"; the closest existing key is used until :ui:words adds one.
        ModelLabel.Loading -> stringResource(Res.string.agentsModelLoading)
        ModelLabel.NoModelSignedIn -> stringResource(Res.string.chatNoModelSignedIn)
    }

@Composable
private fun effortWords(effort: EffortWord): String =
    when (effort) {
        EffortWord.Known.None -> stringResource(Res.string.modelEffortNone)
        EffortWord.Known.Minimal -> stringResource(Res.string.modelEffortMinimal)
        EffortWord.Known.Low -> stringResource(Res.string.modelEffortLow)
        EffortWord.Known.Medium -> stringResource(Res.string.modelEffortMedium)
        EffortWord.Known.High -> stringResource(Res.string.modelEffortHigh)
        EffortWord.Known.ExtraHigh -> stringResource(Res.string.modelEffortExtraHigh)
        EffortWord.Known.Max -> stringResource(Res.string.modelEffortMax)
        is EffortWord.Other -> effort.raw
    }
