package io.github.eslamasabry.oc.feature.chat.composer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.eslamasabry.oc.feature.chat.presentation.ChatActions
import io.github.eslamasabry.oc.feature.chat.presentation.SwitchModelRules
import io.github.eslamasabry.oc.feature.chat.presentation.SwitchUi
import io.github.eslamasabry.oc.feature.chat.world.ComposerModel
import io.github.eslamasabry.oc.ui.kit.modal.KitModelOption
import io.github.eslamasabry.oc.ui.kit.modal.KitModelSheet
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.b169Free
import io.github.eslamasabry.oc.ui.words.res.b169Suggested
import io.github.eslamasabry.oc.ui.words.res.b169SwitchModel
import io.github.eslamasabry.oc.ui.words.res.b169TooLong
import org.jetbrains.compose.resources.stringResource

/**
 * B-169 §3: "Switch model" on a failed reply. The one kit model sheet, without the model that stopped and the models
 * already used up here, one suggestion first, free models saying they may use the prompts, and models the
 * conversation is too long for shown but not choosable. A choice sends the stopped prompt again with it.
 * [readings] are plan readings (percent used) by Usage's provider id; empty when Usage has none.
 */
@Composable
internal fun SwitchModelSheet(
    switching: SwitchUi?,
    models: List<ComposerModel>,
    actions: ChatActions,
    readings: Map<String, Double> = emptyMap(),
) {
    val free = stringResource(Res.string.b169Free)
    val tooLong = stringResource(Res.string.b169TooLong)
    val sheet =
        remember(switching, models, readings) { switching?.let { SwitchModelRules.sheet(models, it, readings) } }
    val options =
        remember(sheet, free, tooLong) {
            sheet?.rows.orEmpty().map { row ->
                KitModelOption(
                    providerId = row.model.providerId,
                    modelId = row.model.modelId,
                    label = row.model.label,
                    providerName = row.model.providerName,
                    supporting = if (row.free) free else null,
                    disabledReason = if (row.tooLong) tooLong else null,
                )
            }
        }
    KitModelSheet(
        visible = switching != null,
        onDismissRequest = actions::dismissSwitch,
        options = options,
        current = null,
        onChoose = { option -> option?.let { actions.switchTo(it.providerId, it.modelId) } },
        offersServerDefault = false,
        resetKey = switching?.turnId,
        suggested = sheet?.suggestion?.let { "${it.providerId}/${it.modelId}" },
        suggestedLabel = stringResource(Res.string.b169Suggested),
        title = stringResource(Res.string.b169SwitchModel),
    )
}
