package io.github.eslamasabry.oc.engine.settings.teaching

import kotlin.jvm.JvmInline
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/** Stable: the storage name inside `oc.tips` and the Help › Tips row key. */
@JvmInline public value class TipId(public val raw: String)

/** A one-sentence tip, or a 2–4 step mini tutorial. */
public enum class TeachingKind {
    Tip,
    Tour,
}

/** One step of a mini tutorial: what it points at and the copy KEY of its sentence. */
public data class TourStepDefinition(val anchors: ImmutableList<String>, val messageKey: String)

/**
 * One registered tip or mini tutorial (owner rule "teach what is hidden",
 * docs/ux-system/teaching.md). Engines hold no copy: [titleKey], [messageKey] and the steps' keys
 * are `:ui:words` keys (the Flutter ARB names), and [copyKeys] lists every key the entry shows so
 * `:ui:words` / `:quality` can check en and ar (B-L10N-020).
 *
 * @property anchors kit anchor ids (`Modifier.kitTipAnchor`) of the controls it points at.
 * @property trigger when it shows, in plain words, for people reading the registry.
 * @property priority a higher priority replaces a lower one on screen.
 */
public data class TipDefinition(
    val id: TipId,
    val kind: TeachingKind,
    val titleKey: String,
    val messageKey: String,
    val copyKeys: ImmutableList<String>,
    val anchors: ImmutableList<String>,
    val trigger: String,
    val maxCount: Int = TipController.DEFAULT_MAX_COUNT,
    val priority: Int = 0,
    val steps: ImmutableList<TourStepDefinition> = persistentListOf(),
    /**
     * A tour that runs in its own sheet rather than as a highlight tour over one screen has no
     * steps.
     */
    val runsInOwnSheet: Boolean = false,
)

public object TeachingIds {
    public val TerminalSelection: TipId = TipId("terminal-selection")
    public val StopAfterStep: TipId = TipId("stop-after-step")
    public val SendLater: TipId = TipId("send-later")
    public val HoldMic: TipId = TipId("hold-mic")
    public val VoiceConversation: TipId = TipId("voice-conversation")
    public val MakeItYours: TipId = TipId("make-it-yours")
    public val WhatsNew: TipId = TipId("whats-new")
    public val TerminalLinks: TipId = TipId("terminal-links")

    /**
     * The earlier Appearance tour "Make it yours" replaced: an answer given to it stands for the
     * new one.
     */
    public val LegacyAppearanceTour: TipId = TipId("appearance-studio")
}

/** The Appearance chips the "Make it yours" steps point at. */
public object MakeItYoursAnchors {
    public const val COLOURS: String = "appearance-group-colours"
    public const val STYLE: String = "appearance-group-style"
    public const val BACKGROUND: String = "appearance-group-background"
    public const val COMFORT: String = "appearance-group-comfort"
    public val all: ImmutableList<String> = persistentListOf(COLOURS, STYLE, BACKGROUND, COMFORT)
}

/**
 * Every tip and mini tutorial the app has, in the order Help › Tips lists them (plan 03 §11.2:
 * features contribute their [TipDefinition]s; the coordinator wires the multibinding and keeps
 * [defaults] as the S2 base set).
 */
public class TeachingRegistry(tips: Iterable<TipDefinition>) {
    public val tips: ImmutableList<TipDefinition> = tips.toImmutableList()

    public fun entry(id: TipId): TipDefinition? = tips.firstOrNull { it.id == id }

    /**
     * The registry's promises (B-L10N-020 minus the en/ar check, which `:ui:words` owns). Empty
     * when sound.
     */
    public fun problems(): List<String> = buildList {
        val ids = tips.map { it.id.raw }
        ids.groupingBy { it }
            .eachCount()
            .filterValues { it > 1 }
            .keys
            .forEach { add("$it: id is not unique") }
        for (tip in tips) {
            val id = tip.id.raw
            if (id.isBlank()) add("blank id")
            if (tip.maxCount !in 1..2) add("$id: maxCount ${tip.maxCount} is outside 1..2")
            if (tip.anchors.isEmpty()) add("$id: no anchor")
            if (tip.trigger.isBlank()) add("$id: no trigger")
            if (tip.copyKeys.isEmpty()) add("$id: no copy keys")
            if (tip.copyKeys.toSet().size != tip.copyKeys.size)
                add("$id: a copy key is listed twice")
            if (tip.titleKey !in tip.copyKeys) add("$id: title key is not declared")
            if (tip.messageKey !in tip.copyKeys) add("$id: message key is not declared")
            for (step in tip.steps) {
                if (step.messageKey !in tip.copyKeys)
                    add("$id: step key ${step.messageKey} is not declared")
                if (step.anchors.isEmpty()) add("$id: a step has no anchor")
            }
            when (tip.kind) {
                TeachingKind.Tip -> if (tip.steps.isNotEmpty()) add("$id: a tip has no steps")
                TeachingKind.Tour ->
                    if (!tip.runsInOwnSheet && tip.steps.size !in 2..4)
                        add("$id: a tutorial has 2 to 4 steps, not ${tip.steps.size}")
            }
        }
    }

    public companion object {
        /**
         * The Flutter registry (`lib/ui/teaching/teaching.dart`), ids and ARB key names unchanged.
         */
        public val defaults: TeachingRegistry =
            TeachingRegistry(
                listOf(
                    TipDefinition(
                        id = TeachingIds.TerminalLinks,
                        kind = TeachingKind.Tip,
                        titleKey = "tipTerminalLinksTitle",
                        messageKey = "tipTerminalLinks",
                        copyKeys = persistentListOf("tipTerminalLinksTitle", "tipTerminalLinks"),
                        anchors = persistentListOf("terminal-output"),
                        trigger =
                            "the live terminal first contains a detected link, while the keyboard is clear; done when a link is opened",
                        maxCount = 2,
                    ),
                    TipDefinition(
                        id = TeachingIds.TerminalSelection,
                        kind = TeachingKind.Tip,
                        titleKey = "terminal40TipTitle",
                        messageKey = "terminal40Tip",
                        copyKeys = persistentListOf("terminal40TipTitle", "terminal40Tip"),
                        anchors = persistentListOf("terminal-live"),
                        trigger =
                            "a connected terminal grid is visible, with no question open; used when Select text is chosen",
                        maxCount = 2,
                    ),
                    TipDefinition(
                        id = TeachingIds.StopAfterStep,
                        kind = TeachingKind.Tip,
                        titleKey = "tipStopAfterStepTitle",
                        messageKey = "tipStopAfterStep",
                        copyKeys = persistentListOf("tipStopAfterStepTitle", "tipStopAfterStep"),
                        anchors = persistentListOf("chat-stop-button"),
                        trigger =
                            "a reply has run for 10 s and Stop offers \"Stop after this step\"; " +
                                "done once the person stops after a step",
                        priority = 2,
                    ),
                    TipDefinition(
                        id = TeachingIds.SendLater,
                        kind = TeachingKind.Tip,
                        titleKey = "tipSendLaterTitle",
                        messageKey = "tipSendLater",
                        copyKeys = persistentListOf("tipSendLaterTitle", "tipSendLater"),
                        anchors = persistentListOf("chat-send-button"),
                        trigger =
                            "words wait in the box and Send offers \"Send later or repeat\"; done once the person opens it",
                        priority = 1,
                    ),
                    TipDefinition(
                        id = TeachingIds.HoldMic,
                        kind = TeachingKind.Tip,
                        titleKey = "tipHoldMicTitle",
                        messageKey = "tipHoldMic",
                        copyKeys = persistentListOf("tipHoldMicTitle", "tipHoldMic"),
                        anchors = persistentListOf("composer-voice-button"),
                        trigger =
                            "the mic was used with a tap and the box is empty again; done once the person holds the mic to talk",
                        priority = 1,
                    ),
                    TipDefinition(
                        id = TeachingIds.VoiceConversation,
                        kind = TeachingKind.Tour,
                        titleKey = "voiceConversationTitle",
                        messageKey = "voiceConversationNativeTourSpeak",
                        copyKeys =
                            persistentListOf(
                                "voiceConversationTitle",
                                "voiceConversationNativeTourSpeak",
                                "voiceConversationNativeTourReplies",
                                "voiceConversationNativeTourLeave",
                            ),
                        anchors = persistentListOf("voice-conversation"),
                        trigger =
                            "the person first starts a voice conversation; a nonblocking three-step explanation beside its controls",
                        steps =
                            persistentListOf(
                                TourStepDefinition(
                                    persistentListOf("voice-conversation"),
                                    "voiceConversationNativeTourSpeak",
                                ),
                                TourStepDefinition(
                                    persistentListOf("voice-conversation"),
                                    "voiceConversationNativeTourReplies",
                                ),
                                TourStepDefinition(
                                    persistentListOf("voice-conversation"),
                                    "voiceConversationNativeTourLeave",
                                ),
                            ),
                    ),
                    TipDefinition(
                        id = TeachingIds.MakeItYours,
                        kind = TeachingKind.Tour,
                        titleKey = "tourMakeItYoursTitle",
                        messageKey = "tourMakeItYoursOffer",
                        copyKeys =
                            persistentListOf(
                                "tourMakeItYoursTitle",
                                "tourMakeItYoursOffer",
                                "tourAppearanceStart",
                                "tourNotNow",
                                "tourMakeItYoursColours",
                                "tourMakeItYoursStyle",
                                "tourMakeItYoursBackground",
                                "tourMakeItYoursComfort",
                            ),
                        anchors = MakeItYoursAnchors.all,
                        trigger =
                            "after the first reply: the first visit to Settings › This app › Appearance, " +
                                "or a screen that offers it (offered once)",
                        maxCount = 1,
                        steps =
                            persistentListOf(
                                TourStepDefinition(
                                    persistentListOf(MakeItYoursAnchors.COLOURS),
                                    "tourMakeItYoursColours",
                                ),
                                TourStepDefinition(
                                    persistentListOf(MakeItYoursAnchors.STYLE),
                                    "tourMakeItYoursStyle",
                                ),
                                TourStepDefinition(
                                    persistentListOf(MakeItYoursAnchors.BACKGROUND),
                                    "tourMakeItYoursBackground",
                                ),
                                TourStepDefinition(
                                    persistentListOf(MakeItYoursAnchors.COMFORT),
                                    "tourMakeItYoursComfort",
                                ),
                            ),
                    ),
                    TipDefinition(
                        id = TeachingIds.WhatsNew,
                        kind = TeachingKind.Tour,
                        titleKey = "whatsNewTipsTitle",
                        messageKey = "whatsNewRowDetail",
                        copyKeys = persistentListOf("whatsNewTipsTitle", "whatsNewRowDetail"),
                        anchors = persistentListOf("about-whats-new"),
                        trigger =
                            "once after an update (never on a fresh install): the walkthrough of what is new; " +
                                "replayed from About › What's new",
                        maxCount = 1,
                        runsInOwnSheet = true,
                    ),
                )
            )
    }
}

/**
 * Offers a registered tip at its moment with the registry's count and priority (Flutter
 * `offerTip`).
 */
public fun TipController.offer(
    registry: TeachingRegistry,
    id: TipId,
    visit: TipVisit? = null,
    moment: TipMoment = TipMoment(),
): Boolean {
    val entry = registry.entry(id) ?: return false
    return offer(
        id,
        visit = visit,
        maxCount = entry.maxCount,
        priority = entry.priority,
        moment = moment,
    )
}

/** "Make it yours" was answered: taken, declined or done, or the Appearance tour before it. */
public fun TipController.makeItYoursAnswered(): Boolean =
    record(TeachingIds.MakeItYours).done || record(TeachingIds.LegacyAppearanceTour).done
