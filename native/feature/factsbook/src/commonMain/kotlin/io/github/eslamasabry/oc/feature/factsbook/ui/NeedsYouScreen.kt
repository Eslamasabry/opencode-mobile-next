package io.github.eslamasabry.oc.feature.factsbook.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.factsbook.ReviewItem
import io.github.eslamasabry.oc.feature.factsbook.presentation.NeedsYouActions
import io.github.eslamasabry.oc.feature.factsbook.presentation.NeedsYouUi
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.foundation.KitPanel
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowLeading
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun classWords(value: String): String =
    stringResource(
        when (value) {
            "fact_update" -> Res.string.fbCareClassFactUpdate
            "fact_supersede" -> Res.string.fbCareClassFactSupersede
            "merge" -> Res.string.fbCareClassMerge
            "link" -> Res.string.fbCareClassLink
            "archive" -> Res.string.fbCareClassArchive
            "forget" -> Res.string.fbCareClassForget
            "watch" -> Res.string.fbCareClassWatch
            "reminder" -> Res.string.fbCareClassReminder
            "import_candidate" -> Res.string.fbCareClassImport
            "promotion" -> Res.string.fbCareClassPromotion
            else -> Res.string.fbCareClassFactAdd
        }
    )

/**
 * Needs you (F: memory_care_screen.dart): the changes waiting for the person's OK and the notes not saved yet. A
 * decision is signed by this phone's owner key; the native app has no owner signer yet, so Approve and Reject say why
 * they are off (Flutter shows the same while the phone cannot approve) and nothing is decided here.
 */
@Composable
public fun NeedsYouScreen(state: NeedsYouUi, actions: NeedsYouActions) {
    val title = stringResource(Res.string.fbNeedsYouTitle)
    val gutter = LocalKitTokens.current.gutter.dp
    KitScreen(
        topBar = {
            KitTopBar(
                title,
                subtitle = if (state.review.isEmpty()) null else pluralStringResource(Res.plurals.fbCareWaiting, state.review.size, state.review.size),
                exit = KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.kitTopBarBack),
                onExit = actions::back,
            )
        },
        width = KitScreenWidth.Reading,
    ) {
        if (state.review.isEmpty() && state.notSaved == 0) {
            KitStateView(
                KitStateKind.Calm,
                stringResource(Res.string.fbNeedsYouNone),
                body = stringResource(Res.string.fbNeedsYouNoneBody),
                icon = KitIcons.checkCircle,
            )
            return@KitScreen
        }
        LazyColumn(Modifier.fillMaxSize()) {
            if (state.review.isNotEmpty() && !state.canApprove) {
                item("owner") {
                    KitNotice(
                        stringResource(Res.string.fbCareNeedsOwner),
                        Modifier.padding(horizontal = gutter, vertical = Space.S2.dp),
                        icon = KitIcons.info,
                        actions = listOf(KitLineAction(stringResource(Res.string.fbCareSetupApproval), actions::setUpApproval)),
                    )
                }
            }
            state.review.forEach { item -> item(item.id) { Card(item, state, gutter) } }
            if (state.notSaved > 0) {
                item("notSaved") {
                    KitRowGroup(label = stringResource(Res.string.fbNotSavedTitle)) {
                        KitRow(
                            title = pluralStringResource(Res.plurals.fbMemoryNotSaved, state.notSaved, state.notSaved),
                            supporting = stringResource(Res.string.fbMemoryNotSavedDetail),
                            leading = KitRowLeading.Glyph(KitIcons.notificationImportant),
                            trailing = KitRowTrailing.Chevron,
                            onClick = actions::openNotSaved,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Card(item: ReviewItem, state: NeedsYouUi, gutter: androidx.compose.ui.unit.Dp) {
    val kind = classWords(item.reviewClass)
    val claim = item.text?.let { bidiName(it) } ?: kind
    val off = if (state.canApprove) KitEnabled.Enabled else KitEnabled.Disabled(stringResource(Res.string.fbCareNeedsOwnerShort))
    KitPanel(Modifier.padding(horizontal = gutter, vertical = Space.S2.dp), title = claim) {
        if (item.text != null) KitText(kind, role = TypeRole.Secondary, tone = TextTone.Secondary)
        if (item.area.isNotEmpty()) KitText(areaName(item.area), role = TypeRole.Caption, tone = TextTone.Secondary)
        Row(horizontalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
            KitButton(
                KitAction(stringResource(Res.string.fbCareApprove), {}, enabled = off, semanticsLabel = stringResource(Res.string.fbCareApproveNamed, claim)),
                role = KitButtonRole.Secondary,
            )
            KitButton(
                KitAction(stringResource(Res.string.fbCareReject), {}, enabled = off, semanticsLabel = stringResource(Res.string.fbCareRejectNamed, claim)),
                role = KitButtonRole.Secondary,
            )
        }
    }
}
