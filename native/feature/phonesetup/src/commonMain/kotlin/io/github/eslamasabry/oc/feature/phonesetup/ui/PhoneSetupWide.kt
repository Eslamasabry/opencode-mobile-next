package io.github.eslamasabry.oc.feature.phonesetup.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.Redacted
import io.github.eslamasabry.oc.core.common.text.Numbers
import io.github.eslamasabry.oc.engine.linuxsetup.SetupComponentIds
import io.github.eslamasabry.oc.ui.design.layout.CapAlign
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.foundation.KitDivider
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurface
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfacePadding
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfacePanel
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.frame.kitPageScrollState
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowLeading
import io.github.eslamasabry.oc.ui.kit.row.KitRowState
import io.github.eslamasabry.oc.ui.kit.row.KitRowStateTone
import io.github.eslamasabry.oc.ui.kit.setup.KitSetupPhone
import io.github.eslamasabry.oc.ui.kit.setup.KitSetupPhoneMood
import io.github.eslamasabry.oc.ui.kit.state.KitDetailsFold
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitCopyDetails
import io.github.eslamasabry.oc.ui.words.res.kitDetails
import io.github.eslamasabry.oc.ui.words.res.kitMarkDone
import io.github.eslamasabry.oc.ui.words.res.kitMarkFailed
import io.github.eslamasabry.oc.ui.words.res.kitMarkWaiting
import io.github.eslamasabry.oc.ui.words.res.kitMarkWorking
import io.github.eslamasabry.oc.ui.words.res.phoneSetupEssentialsWhy
import io.github.eslamasabry.oc.ui.words.res.phoneSetupLinuxWhy
import io.github.eslamasabry.oc.ui.words.res.phoneSetupNodeWhy
import io.github.eslamasabry.oc.ui.words.res.phoneSetupOpenCodeWhy
import io.github.eslamasabry.oc.ui.words.res.phoneSetupRingDetailsTitle
import io.github.eslamasabry.oc.ui.words.res.phoneSetupRingStoppedNote
import io.github.eslamasabry.oc.ui.words.res.phoneSetupStartListSeparator
import io.github.eslamasabry.oc.ui.words.res.phoneSetupStartStepsNoTime
import io.github.eslamasabry.oc.ui.words.res.phoneSetupStartWhy
import io.github.eslamasabry.oc.ui.words.res.teamUiHomeRunProgress
import io.github.eslamasabry.oc.ui.words.res.voiceComponentSummary
import org.jetbrains.compose.resources.stringResource

/**
 * Shared wide-window (Expanded+, >= 840 dp) two-column frame for [PhoneSetupScreen] and [SetupJourneyScreen]:
 * places the page title directly above the two reading-capped columns in one vertically centred composition.
 */
@Composable
internal fun WideTwoColumnFrame(
    pageTitle: String,
    verticalAlignment: Alignment.Vertical,
    start: @Composable () -> Unit,
    end: @Composable () -> Unit,
    viewportModifier: Modifier = Modifier,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val viewportHeight = maxHeight
        Box(Modifier.fillMaxWidth().verticalScroll(kitPageScrollState())) {
            Box(
                Modifier.fillMaxWidth()
                    .heightIn(min = viewportHeight)
                    .then(viewportModifier)
                    .padding(top = Space.S2.dp, bottom = Space.S6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.S5.dp)) {
                    KitText(
                        pageTitle,
                        Modifier.semantics { heading() },
                        role = TypeRole.LargeTitle,
                        kind = KitTextKind.Frame,
                        pageTitle = true,
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Space.S6.dp),
                        verticalAlignment = verticalAlignment,
                    ) {
                        Box(Modifier.weight(1f).kitMaxWidth(CapRole.Reading, CapAlign.Start)) { start() }
                        Box(Modifier.weight(1f).kitMaxWidth(CapRole.Reading, CapAlign.Start)) { end() }
                    }
                }
            }
        }
    }
}

@Composable
internal fun WideArtworkBay(mood: KitSetupPhoneMood) {
    KitSurface(
        modifier = Modifier.fillMaxWidth(),
        level = SurfaceLevel.Surface1,
        shape = KitShapeId.Panel,
        padding = KitSurfacePadding.Panel,
    ) {
        Box(Modifier.fillMaxWidth().padding(vertical = Space.S2.dp), contentAlignment = Alignment.Center) {
            KitSetupPhone(mood = mood, modifier = Modifier.size(ARTWORK_WIDTH_DP.dp, ARTWORK_HEIGHT_DP.dp))
        }
    }
}

/** Three numbered future steps ("1 Install", "2 Name a project", "3 Start a conversation") from the promise line. */
@Composable
internal fun WideStartStepList() {
    val sep = stringResource(Res.string.phoneSetupStartListSeparator).trim()
    val steps =
        stringResource(Res.string.phoneSetupStartStepsNoTime)
            .split(sep)
            .map { part -> part.trim().replaceFirstChar { ch -> ch.uppercase() } }
            .filter { it.isNotEmpty() }
    Column(verticalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
        steps.forEachIndexed { index, step ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.S3.dp),
            ) {
                KitSurface(level = SurfaceLevel.Surface2, shape = KitShapeId.Circle, padding = KitSurfacePadding.None) {
                    Box(Modifier.size(STEP_BADGE_DP.dp), contentAlignment = Alignment.Center) {
                        KitText(Numbers.format(index + 1), role = TypeRole.Caption, tone = TextTone.Accent)
                    }
                }
                KitText(step, role = TypeRole.Secondary, tone = TextTone.Secondary)
            }
        }
    }
}

internal enum class WidePartStatus { Start, Done, Working, Failed, Waiting }

internal data class WidePartItem(
    val id: String,
    val title: String,
    val status: WidePartStatus,
    val progressNote: String? = null,
)

@Composable
internal fun WidePartsPanel(
    items: List<WidePartItem>,
    showDoneCount: Boolean,
    showStoppedNote: Boolean = false,
    details: Redacted? = null,
    modifier: Modifier = Modifier,
) {
    val doneCount = items.count { it.status == WidePartStatus.Done }
    val headerMeta =
        if (showDoneCount && items.isNotEmpty()) {
            stringResource(Res.string.teamUiHomeRunProgress, doneCount, items.size)
        } else {
            null
        }
    KitSurfacePanel(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = Space.S2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KitText(stringResource(Res.string.phoneSetupRingDetailsTitle), role = TypeRole.Headline)
                headerMeta?.let { KitText(it, role = TypeRole.Secondary, tone = TextTone.Secondary) }
            }
            KitDivider()
            items.forEachIndexed { index, item ->
                if (index > 0) KitDivider()
                WidePartRow(item)
            }
            if (showStoppedNote) {
                KitDivider()
                Spacer(Modifier.height(Space.S3.dp))
                KitText(
                    stringResource(Res.string.phoneSetupRingStoppedNote),
                    role = TypeRole.Secondary,
                    tone = TextTone.Secondary,
                )
            }
            if (details != null) {
                KitDivider()
                KitDetailsFold(
                    label = stringResource(Res.string.kitDetails),
                    copyLabel = stringResource(Res.string.kitCopyDetails),
                    text = details,
                )
            }
        }
    }
}

@Composable
private fun WidePartRow(item: WidePartItem) {
    val leading =
        when (item.status) {
            WidePartStatus.Start -> KitRowLeading.Glyph(componentIcon(item.id), tile = true)
            WidePartStatus.Done -> KitRowLeading.Glyph(KitIcons.checkCircle)
            WidePartStatus.Working -> null
            WidePartStatus.Failed -> KitRowLeading.Glyph(KitIcons.error)
            WidePartStatus.Waiting -> KitRowLeading.Glyph(KitIcons.clock)
        }
    val leadingTone =
        when (item.status) {
            WidePartStatus.Done -> TextTone.Success
            WidePartStatus.Failed -> TextTone.Primary
            WidePartStatus.Waiting -> TextTone.Tertiary
            WidePartStatus.Start, WidePartStatus.Working -> TextTone.Secondary
        }
    val state =
        if (item.status == WidePartStatus.Working) KitRowState(word = null, tone = KitRowStateTone.Running) else null
    val meta = widePartMeta(item)
    KitRow(
        title = item.title,
        supporting = componentWhy(item.id),
        leading = leading,
        leadingTone = leadingTone,
        state = state,
        meta = meta,
        supportingMaxLines = 2,
    )
}

/** The trailing mark of a wide part: its done/working/failed/waiting word, with any progress note. */
@Composable
private fun widePartMeta(item: WidePartItem): String? =
    when (item.status) {
        WidePartStatus.Start -> null
        WidePartStatus.Done -> stringResource(Res.string.kitMarkDone)
        WidePartStatus.Working ->
            listOfNotNull(stringResource(Res.string.kitMarkWorking), item.progressNote).joinToString(" · ")
        WidePartStatus.Failed -> stringResource(Res.string.kitMarkFailed)
        WidePartStatus.Waiting -> stringResource(Res.string.kitMarkWaiting)
    }

@Composable
internal fun componentWhy(id: String): String? =
    when (id) {
        SetupComponentIds.LINUX -> stringResource(Res.string.phoneSetupLinuxWhy)
        SetupComponentIds.ESSENTIALS -> stringResource(Res.string.phoneSetupEssentialsWhy)
        SetupComponentIds.NODE -> stringResource(Res.string.phoneSetupNodeWhy)
        SetupComponentIds.OPENCODE -> stringResource(Res.string.phoneSetupOpenCodeWhy)
        SetupComponentIds.START -> stringResource(Res.string.phoneSetupStartWhy)
        SetupComponentIds.VOICE -> stringResource(Res.string.voiceComponentSummary)
        else -> null
    }

internal fun componentIcon(id: String) =
    when (id) {
        SetupComponentIds.LINUX -> KitIcons.terminal
        SetupComponentIds.ESSENTIALS -> KitIcons.branch
        SetupComponentIds.NODE -> KitIcons.code
        SetupComponentIds.OPENCODE -> KitIcons.agent
        SetupComponentIds.VOICE -> KitIcons.mic
        else -> KitIcons.tools
    }

private const val ARTWORK_WIDTH_DP = 208
private const val ARTWORK_HEIGHT_DP = 156
private const val STEP_BADGE_DP = 22
