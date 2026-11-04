package io.github.eslamasabry.oc.feature.chat.composer

import androidx.compose.runtime.Composable
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.stateProviderZaiChina
import io.github.eslamasabry.oc.ui.words.res.stateProviderZaiGlobal
import io.github.eslamasabry.oc.ui.words.res.stateProviderZaiPlanChina
import io.github.eslamasabry.oc.ui.words.res.stateProviderZaiPlanGlobal
import org.jetbrains.compose.resources.stringResource

/** Keep regional routes explicit while preserving their distinct wire provider ids. */
@Composable
internal fun providerRouteName(id: String, fallback: String): String = when (id.lowercase()) {
    "zai" -> stringResource(Res.string.stateProviderZaiGlobal)
    "zhipuai" -> stringResource(Res.string.stateProviderZaiChina)
    "zai-coding-plan" -> stringResource(Res.string.stateProviderZaiPlanGlobal)
    "zhipuai-coding-plan" -> stringResource(Res.string.stateProviderZaiPlanChina)
    else -> fallback.trim().ifEmpty { id }
}
