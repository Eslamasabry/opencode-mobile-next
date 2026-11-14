package io.github.eslamasabry.oc.feature.settings.ui

import io.github.eslamasabry.oc.ui.kit.frame.kitPageListState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.settings.presentation.GlassLabActions
import io.github.eslamasabry.oc.feature.settings.presentation.GlassLabUi
import io.github.eslamasabry.oc.ui.design.effects.GlassKnob
import io.github.eslamasabry.oc.ui.design.effects.GlassPaneKind
import io.github.eslamasabry.oc.ui.design.effects.GlassPreset
import io.github.eslamasabry.oc.ui.design.effects.GlassTuning
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.action.KitFab
import io.github.eslamasabry.oc.ui.kit.action.KitIconButton
import io.github.eslamasabry.oc.ui.kit.action.KitPasteButton
import io.github.eslamasabry.oc.ui.kit.action.rememberKitCopyAction
import io.github.eslamasabry.oc.ui.kit.chip.KitChip
import io.github.eslamasabry.oc.ui.kit.chip.KitChipKind
import io.github.eslamasabry.oc.ui.kit.chip.KitFilterChip
import io.github.eslamasabry.oc.ui.kit.chip.KitFilterChips
import io.github.eslamasabry.oc.ui.kit.choice.KitSlider
import io.github.eslamasabry.oc.ui.kit.choice.KitSliderWords
import io.github.eslamasabry.oc.ui.kit.effects.KitGlassTuned
import io.github.eslamasabry.oc.ui.kit.foundation.KitFrostState
import io.github.eslamasabry.oc.ui.kit.foundation.kitFrostSource
import io.github.eslamasabry.oc.ui.kit.foundation.rememberKitFrostState
import io.github.eslamasabry.oc.ui.kit.frame.KitConnectionTone
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenEdges
import io.github.eslamasabry.oc.ui.kit.frame.KitStatusPill
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.frame.KitTuningDrawer
import io.github.eslamasabry.oc.ui.kit.frame.kitScreenChromePadding
import io.github.eslamasabry.oc.ui.kit.frame.rememberKitTuningDrawerState
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.modal.KitAlert
import io.github.eslamasabry.oc.ui.kit.modal.KitMenu
import io.github.eslamasabry.oc.ui.kit.modal.KitMenuItem
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.modal.LocalKitToasts
import io.github.eslamasabry.oc.ui.kit.row.KitConversation
import io.github.eslamasabry.oc.ui.kit.row.KitConversationRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowState
import io.github.eslamasabry.oc.ui.kit.row.KitRowStateTone
import io.github.eslamasabry.oc.ui.kit.row.KitSectionLabel
import io.github.eslamasabry.oc.ui.kit.row.KitSwitchRow
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerBox
import io.github.eslamasabry.oc.ui.kitchat.header.KitChatMoreMenu
import io.github.eslamasabry.oc.ui.kitchat.markdown.KitMarkdown
import io.github.eslamasabry.oc.ui.kitchat.markdown.KitMarkdownActions
import io.github.eslamasabry.oc.ui.kitchat.markdown.KitMdSource
import io.github.eslamasabry.oc.ui.kitchat.request.KitPermissionCard
import io.github.eslamasabry.oc.ui.kitchat.transcript.KitPromptBubble
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

/** The two pages the lab's preview shows. */
internal enum class GlassLabScene { Chat, Home }

/**
 * Settings › Appearance › Glass lab (preview builds, owner 2026-11-13): a real preview (a chat with its floating bar and
 * message box, or the home list) drawn with the draft numbers, and over it a solid drawer of controls: which scene,
 * which overlay is open (the bar's menu joined to the bar, a sheet, a context menu, a dialog), which pane kind the
 * sliders move, three starting points, "Use in the whole app", Copy, Paste and Reset all, then every number.
 */
@Composable
internal fun GlassLabPage(ui: GlassLabUi, actions: GlassLabActions, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val dark = LocalKitColors.current.dark
    var scene by remember { mutableStateOf(GlassLabScene.Chat) }
    var menu by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf(false) }
    var context by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf(false) }
    val drawer = rememberKitTuningDrawerState()
    KitGlassTuned(ui.tuning) {
        KitSheetHost(modifier.fillMaxSize().testTag("glass-lab")) {
            val frost = rememberKitFrostState()
            KitScreen(
                modifier = Modifier.fillMaxSize(),
                page = true,
                overlayChrome = true,
                frost = frost,
                topBar = { GlassLabBar(scene, menu, { menu = it }, onBack) },
                bottom = {
                    Column(Modifier.fillMaxWidth()) {
                        if (scene == GlassLabScene.Chat) GlassLabComposer()
                        else
                            Box(Modifier.fillMaxWidth().padding(Space.S4.dp)) {
                                KitFab(stringResource(Res.string.glassLabSampleNew), KitIcons.add, {}, Modifier.align(Alignment.CenterEnd), backdrop = frost)
                            }
                        KitTuningDrawer(
                            handleLabel = stringResource(Res.string.glassLabHandle),
                            openWord = stringResource(Res.string.glassLabHandleOpen),
                            closedWord = stringResource(Res.string.glassLabHandleClosed),
                            state = drawer,
                            header = {
                                GlassLabOpeners(
                                    scene = scene,
                                    onScene = { scene = it },
                                    onMenu = { menu = true },
                                    onSheet = { sheet = true },
                                    onContext = { context = true },
                                    onDialog = { dialog = true },
                                )
                                GlassLabKinds(ui.kind, actions::onKind)
                            },
                        ) {
                            GlassLabControls(ui, actions, dark)
                        }
                    }
                },
                edges = KitScreenEdges.Bleed,
            ) {
                when (scene) {
                    GlassLabScene.Chat -> GlassLabChat(frost, context) { context = it }
                    GlassLabScene.Home -> GlassLabHome(frost)
                }
            }
            GlassLabSheet(sheet) { sheet = false }
            KitAlert(
                visible = dialog,
                title = stringResource(Res.string.glassLabSampleDialogTitle),
                onDismissRequest = { dialog = false },
                body = stringResource(Res.string.glassLabSampleDialogBody),
                primary = KitAction(stringResource(Res.string.glassLabSampleDelete), { dialog = false }, destructive = true),
                secondary = KitAction(stringResource(Res.string.glassLabSampleKeep), { dialog = false }),
                icon = KitIcons.delete,
            )
        }
    }
}

/** The preview's floating bar: Back leaves the lab; its "…" menu is the chat's, joined to the bar. */
@Composable
private fun GlassLabBar(scene: GlassLabScene, menu: Boolean, onMenu: (Boolean) -> Unit, onBack: () -> Unit) {
    val home = scene == GlassLabScene.Home
    KitTopBar(
        title = stringResource(if (home) Res.string.glassLabSampleHome else Res.string.glassLabSampleTitle),
        subtitle = if (home) null else stringResource(Res.string.glassLabSampleSubtitle),
        exit = KitTopBarExit.Back,
        exitLabel = stringResource(Res.string.kitTopBarBack),
        onExit = onBack,
        backBesideTitle = true,
        more = {
            KitChatMoreMenu(
                label = stringResource(Res.string.kitTopBarMore),
                expanded = menu,
                items = sampleMenu { onMenu(false) },
                onOpen = { onMenu(true) },
                onDismissRequest = { onMenu(false) },
            )
        },
    )
}

/** The home bar's status pill under its title. */
private val HomePill: @Composable () -> Unit = {
    KitStatusPill(
        label = stringResource(Res.string.glassLabSampleConnected),
        tone = KitConnectionTone.Ok,
        spoken = stringResource(Res.string.glassLabSampleConnected),
        onClick = {},
    )
}

@Composable
private fun sampleMenu(close: () -> Unit): List<KitMenuItem> =
    listOf(
        KitMenuItem(stringResource(Res.string.sessionMenuFind), close, icon = KitIcons.search),
        KitMenuItem(stringResource(Res.string.chatUiRenameSession), close, icon = KitIcons.edit),
        KitMenuItem(stringResource(Res.string.chatUiForkSession), close, icon = KitIcons.fork),
        KitMenuItem(stringResource(Res.string.chatUiCopyTranscript), close, icon = KitIcons.copy),
    )

/** The message box with its chips, as the chat draws it (a still placeholder: nothing is sent from the lab). */
@Composable
private fun GlassLabComposer() {
    KitComposerBox(
        running = false,
        field = { KitText(stringResource(Res.string.glassLabSampleAskBox), role = TypeRole.Body, tone = io.github.eslamasabry.oc.ui.design.type.TextTone.Secondary) },
        modifier = Modifier.fillMaxWidth().padding(horizontal = Space.S2.dp, vertical = Space.S2.dp),
        leading = { KitIconButton(KitIcons.add, stringResource(Res.string.glassLabSampleNew), {}) },
        chips = {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
                KitChip(stringResource(Res.string.glassLabSampleMode), kind = KitChipKind.Action, onClick = {})
                KitChip(stringResource(Res.string.glassLabSampleModel), kind = KitChipKind.Action, opensMenu = true, onClick = {})
            }
        },
        trailing = { KitIconButton(KitIcons.mic, stringResource(Res.string.glassLabSampleAskBox), {}) },
    )
}

/** A conversation that scrolls under the bar and the message box: words, a table, code and a permission card. */
@Composable
private fun GlassLabChat(frost: KitFrostState, context: Boolean, onContext: (Boolean) -> Unit) {
    val reply = stringResource(Res.string.glassLabSampleReply)
    val blocks = remember(reply) { KitMdSource.blocks("glass-lab", reply, streaming = false) }
    val links = remember { KitMarkdownActions(onLink = {}) }
    val gutter = Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp)
    val ask = stringResource(Res.string.glassLabSampleAsk)
    LazyColumn(Modifier.fillMaxSize().kitFrostSource(frost), contentPadding = kitScreenChromePadding(), state = kitPageListState()) {
        // Twice over, so there is always text to pass under the bar and the message box.
        repeat(2) { round ->
            item(key = "ask$round", contentType = "ask") { KitPromptBubble(ask, gutter.padding(vertical = Space.S3.dp)) }
            item(key = "reply$round", contentType = "reply") {
                Box(gutter) {
                    KitMenu(
                        expanded = context && round == 0,
                        items = sampleMenu { onContext(false) },
                        onDismissRequest = { onContext(false) },
                    ) { KitMarkdown(blocks, links) }
                }
            }
            item(key = "ask-two$round", contentType = "ask") {
                KitPromptBubble(stringResource(Res.string.glassLabSampleAskTwo), gutter.padding(vertical = Space.S3.dp))
            }
            item(key = "ask-run$round", contentType = "card") {
                KitPermissionCard(
                    title = stringResource(Res.string.glassLabSamplePermission),
                    summary = stringResource(Res.string.glassLabSamplePermissionWhy),
                    subject = stringResource(Res.string.glassLabSampleCommand),
                    allowOnceLabel = stringResource(Res.string.chatHomeAllow),
                    alwaysLabel = null,
                    denyLabel = stringResource(Res.string.approvalDeny),
                    onAllowOnce = {},
                    onAlways = null,
                    onDeny = {},
                    modifier = gutter.padding(vertical = Space.S3.dp),
                )
            }
        }
    }
}

/** The home list: filter chips, conversations and the + floating over them. */
@Composable
private fun GlassLabHome(frost: KitFrostState) {
    val title = stringResource(Res.string.glassLabSampleTitle)
    val ask = stringResource(Res.string.glassLabSampleAskTwo)
    val needs = KitRowState(stringResource(Res.string.glassLabSampleNeedsYou), KitRowStateTone.NeedsYou)
    val running = KitRowState(stringResource(Res.string.glassLabSampleRunning), KitRowStateTone.Running)
    LazyColumn(Modifier.fillMaxSize().kitFrostSource(frost), contentPadding = kitScreenChromePadding(), state = kitPageListState()) {
        // The home's status pill rides in the bar's second row on the real page; here it heads the list, so the bar
        // keeps one row and its Back.
        item(key = "pill", contentType = "pill") { Box(Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp)) { HomePill() } }
        item(key = "chips", contentType = "chips") {
            KitFilterChips {
                KitFilterChip(stringResource(Res.string.glassLabSampleAll), selected = true, onPressed = {})
                KitFilterChip(stringResource(Res.string.glassLabSampleNeedsYou), selected = false, onPressed = {}, needsYou = true)
                KitFilterChip(stringResource(Res.string.glassLabSampleRunning), selected = false, onPressed = {})
            }
        }
        items(24, key = { it }, contentType = { "row" }) { i ->
            KitConversationRow(
                KitConversation(
                    title = if (i % 2 == 0) title else ask,
                    project = if (i % 3 == 0) "opencode-mobile" else "parser",
                    time = "${i + 1}h",
                    state = if (i == 1) needs else if (i == 2) running else null,
                ),
                onClick = {},
            )
        }
    }
}

/** Permission details as a bottom sheet over the preview. */
@Composable
private fun GlassLabSheet(visible: Boolean, onClose: () -> Unit) {
    KitSheet(
        visible = visible,
        onDismissRequest = onClose,
        title = stringResource(Res.string.glassLabSamplePermission),
        closeLabel = stringResource(Res.string.kitSheetClose),
        primary = KitAction(stringResource(Res.string.chatHomeAllow), onClose),
        secondary = KitAction(stringResource(Res.string.approvalDeny), onClose),
    ) {
        KitText(stringResource(Res.string.glassLabSampleCommand), role = TypeRole.Body)
        KitText(stringResource(Res.string.glassLabSamplePermissionWhy), role = TypeRole.Secondary)
        KitText(stringResource(Res.string.glassLabSampleReply).substringBefore("\n"), role = TypeRole.Body)
    }
}

/** The scene and the overlays the preview can show. */
@Composable
private fun GlassLabOpeners(
    scene: GlassLabScene,
    onScene: (GlassLabScene) -> Unit,
    onMenu: () -> Unit,
    onSheet: () -> Unit,
    onContext: () -> Unit,
    onDialog: () -> Unit,
) {
    KitFilterChips(Modifier.testTag("glass-lab-openers")) {
        KitFilterChip(stringResource(Res.string.glassLabSceneChat), selected = scene == GlassLabScene.Chat, onPressed = { onScene(GlassLabScene.Chat) })
        KitFilterChip(stringResource(Res.string.glassLabSceneHome), selected = scene == GlassLabScene.Home, onPressed = { onScene(GlassLabScene.Home) })
        KitFilterChip(stringResource(Res.string.glassLabOpenMenu), selected = false, onPressed = onMenu, opens = true)
        KitFilterChip(stringResource(Res.string.glassLabOpenSheet), selected = false, onPressed = onSheet, opens = true)
        if (scene == GlassLabScene.Chat)
            KitFilterChip(stringResource(Res.string.glassLabOpenContext), selected = false, onPressed = onContext, opens = true)
        KitFilterChip(stringResource(Res.string.glassLabOpenDialog), selected = false, onPressed = onDialog, opens = true)
    }
}

/** Which pane kind the sliders move: all of them by the same step, or one. */
@Composable
private fun GlassLabKinds(kind: GlassPaneKind?, onKind: (GlassPaneKind?) -> Unit) {
    KitFilterChips(Modifier.testTag("glass-lab-kinds")) {
        KitFilterChip(stringResource(Res.string.glassLabAllLinked), selected = kind == null, onPressed = { onKind(null) })
        for (k in GlassPaneKind.entries) KitFilterChip(k.label, selected = kind == k, onPressed = { onKind(k) })
    }
}

/** The starting points, the switch, Copy / Paste / Reset all, then every number of the chosen kind in this theme. */
@Composable
private fun androidx.compose.foundation.layout.ColumnScope.GlassLabControls(ui: GlassLabUi, actions: GlassLabActions, dark: Boolean) {
    val gutter = Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp)
    val shown = ui.kind ?: GlassPaneKind.Bar
    val knobs = ui.tuning.knobs(shown, dark)
    val shipped = GlassTuning.shipped(shown, dark)
    val toasts = LocalKitToasts.current
    val copiedToast = stringResource(Res.string.glassLabCopiedToast)
    val pasted = stringResource(Res.string.glassLabPasted)
    val refused = stringResource(Res.string.glassLabPasteRefused)
    KitSectionLabel(stringResource(if (dark) Res.string.glassLabThemeDark else Res.string.glassLabThemeLight))
    KitText(stringResource(Res.string.glassLabStartFrom), gutter, role = TypeRole.Secondary)
    KitFilterChips(Modifier.testTag("glass-lab-presets")) {
        for (p in GlassPreset.entries) KitFilterChip(p.label, selected = false, onPressed = { actions.onPreset(p, dark) })
    }
    KitSwitchRow(
        title = stringResource(Res.string.glassLabInApp),
        checked = ui.inApp,
        supporting = stringResource(Res.string.glassLabInAppLine),
        switchTag = "glass-lab-in-app",
        onCheckedChange = actions::onInApp,
    )
    val copy = rememberKitCopyAction(stringResource(Res.string.glassLabCopy), stringResource(Res.string.glassLabCopied), { ui.copyBlock })
    Row(gutter.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
        KitButton(
            copy.copy(onClick = { copy.onClick?.invoke(); toasts?.show(copiedToast) }),
            Modifier.testTag("glass-lab-copy"),
            role = KitButtonRole.Secondary,
        )
        KitPasteButton(stringResource(Res.string.glassLabPaste), { text -> toasts?.show(if (actions.onPaste(text)) pasted else refused) })
    }
    Row(gutter.fillMaxWidth()) {
        KitButton(stringResource(Res.string.glassLabResetAll), actions::onResetAll, role = KitButtonRole.Tertiary)
    }
    val words =
        KitSliderWords(
            reset = stringResource(Res.string.glassLabSliderReset),
            typeTitle = stringResource(Res.string.glassLabTypeTitle),
            typeField = stringResource(Res.string.glassLabTypeField),
            typeConfirm = stringResource(Res.string.glassLabTypeConfirm),
        )
    for ((group, knobsInGroup) in GlassKnob.entries.groupBy { it.group }) {
        KitSectionLabel(group)
        for (knob in knobsInGroup) {
            KitSlider(
                label = knob.label,
                value = knob.of(knobs),
                min = knob.min,
                max = knob.max,
                step = knob.step,
                default = knob.of(shipped),
                onChange = { actions.onKnob(knob, it, dark) },
                words = words,
                modifier = gutter.padding(vertical = Space.S1.dp).testTag("glass-lab-knob-${knob.id}"),
                unit = knob.unit,
                format = { GlassTuning.number(it) },
            )
        }
    }
}
