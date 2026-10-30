/// The design kit (docs/design/design-standard.md): the few parts every
/// migrated screen is built from, each used the same way everywhere.
///
/// | Part | Standard |
/// |---|---|
/// | [KitDigest] | AI Team controlled presentation surface |
/// | [KitFindingsCard] | AI Team controlled presentation surface |
/// | [KitMergeQueue] | AI Team controlled presentation surface |
/// | [KitMilestoneRow] | AI Team controlled presentation surface |
/// | [KitPhaseCard] | AI Team controlled presentation surface |
/// | [KitPlanCard] | AI Team controlled presentation surface |
/// | [KitProjectRow] | AI Team controlled presentation surface |
/// | [KitPromoteCard] | AI Team controlled presentation surface |
/// | [KitServerLane] | AI Team controlled presentation surface |
/// | [KitSpecBlock] | AI Team controlled presentation surface |
/// | [KitTimelineDay] | AI Team controlled presentation surface |
/// | [KitScreen] | §1 screen: header, one loading bar, body, pinned bottom |
/// | [KitLayout], [KitWindow] | kit v2 §8.1 window classes: every part adapts to phone, tablet and PC |
/// | [KitSheet], [showKitSheet], [showKitFramedSheet], [KitSheetHeight], [KitDraft] | kit v2 §1.1 the one sheet frame, a body that draws its own frame, its draft and unsaved-input guard |
/// | [KitConfirmSheet], [showKitConfirm], [KitConfirmKind] | kit v2 §1.2 the one confirmation (§4.1 undo, confirm or neither) |
/// | [KitConsequences], [KitConsequence] | §5 a sheet's panel of one-line consequences |
/// | [KitLiveBackground], [KitLiveStyle] | the page ground with a slow, soft live look on it (Settings › Appearance), paused when still is wanted; never glass |
/// | [KitLiveLookPreview] | a short live tile of the chosen look behind two mock chat lines (Settings › Appearance › Effects); "Off" when the live background is off |
/// | [KitFloatingPanel] | a small card that floats over a transparent window (the home screen) on a dimmed ground, above the keyboard; tapping the ground closes it |
/// | [KitSteerBox], [KitSteerBoxPhase] | the small composer a home-screen widget opens: what sending does, one field, a microphone and Send |
/// | [KitAskField], [KitAskFieldPhase], [KitAskFieldNoteTone] | one glowing pill field that sends a request elsewhere (the preview's "Ask for a change…"): the field and one round accent button, mic while empty, Send once typed |
/// | [KitWebPreview], [KitWebPreviewController] | a dev server's page inside the app: one origin only, no bridge to the app, cookies cleared, other addresses leave through the external-link gate |
/// | [KitLinkScope] | lets a conversation take a tapped local dev-server link before it goes to the browser |
/// | [KitWidgetPreview], [KitWidgetPreviewSize], [KitWidgetPreviewBackground] | a drawing of a home-screen widget in the style being chosen (ground, accent, layout) |
/// | [KitWidgetStyleForm] | the choices that style one home-screen widget: a drawing of it, the ground, the accent |
/// | [KitTokens] | kit v2 the colours (the theme's [ThemeRoles]), radii, heights, scrim, type and spacing every part reads (a ThemeExtension) |
/// | [KitText], [KitTextRole], [KitTextTone] | visual language §2 the type roles, coloured by theme role |
/// | [KitTip], [KitTipBubble], [KitTour], [KitTourStep] | the teaching system's one-sentence tip anchored to a control, its bubble, and the 2–4 step mini tutorial with Next and Skip (docs/ux-system/teaching.md) |
/// | [KitTextHalo] | a soft halo of the ground around text2/text3 while a bright live look is behind them |
/// | [KitWordText] | words that end at a whole word with "…" when they do not fit their lines, read in full |
/// | [KitSelectable], [KitLtr] | KitText v2 a selectable region around several texts, and a left-to-right technical block |
/// | [KitTaskChip], [showKitTaskSheet] | the agent's task list as one small chip with a progress ring beside the other chips above the field, and the list it opens |
/// | [KitProjectFileList], [showKitProjectFileSheet] | a search field and the project files it finds (folders too), and the sheet that returns the chosen path |
/// | [KitOneTimeCode] | kit v2 a one-time device code shown large and monospaced, with Copy |
/// | [KitServerTrustBody], [showKitServerTrust] | explicit per-server HTTP consent and certificate fingerprint review |
/// | [KitTechnicalValue] | kit v2 §1.8 a technical value shown under Details, left to right |
/// | [KitButton], [KitActionBlock], [KitAction] | §2 one button hierarchy |
/// | [KitInkLayer], [KitInk] | the native Material ripple over a kit control's face on Android (owner rule 2026-10-26); the flat pressed fill elsewhere and under reduced motion |
/// | [KitActionStack] | §2 the same hierarchy with each rare path on its own line |
/// | [KitStateView] | §3 every not-normal state, page or inline |
/// | [KitLoadingBar], [KitSkeletonRows], [KitProgress] | §4 progress |
/// | [KitSkeletonTranscript] | §4 a conversation loading |
/// | [KitLastKnown], [KitLastKnownRow] | §4 what a list held last time, read-only while the live list loads |
/// | [KitTranscriptExcerpt], [KitExcerptMessage] | §4 a conversation's end as it read last time, read-only while its history loads |
/// | [KitStatusLine] | §5 one status line |
/// | [KitAskLine] | §2, §5 a one-time question with its two answers |
/// | [KitRequestCard] | §2, §3 a request the person answers (permission, question) |
/// | [KitRow], [KitRowGroup], [KitRowValue] | §6 rows, rows grouped on one panel, and a row's current value |
/// | [KitRowIcon], [KitRowMenu], [KitChevron], [KitSwitchRow], [KitExpandRow] | §6 a row's current mark, overflow menu, chevron, switch and unfolding group |
/// | [KitPageHeading] | a page's large title under a bar with no title, and one quiet line close under it (what it belongs to, or a switcher) |
/// | [KitSectionLabel] | §5 a section's name above its content, on the one inset every label shares, with the section gap above |
/// | [KitSliverRowGroup] | §4, §5 rows on one panel in a lazy list (a list's head and its rows as one panel) |
/// | [KitPanel] | §3 a block of content the person works with |
/// | [KitStatusMark] | §6 a step's leading state mark (waiting, working, done, failed) |
/// | [KitTaskMark] | §6 a task's leading mark: a step's four, needs you, stopped |
/// | [KitNotice] | §3 a message inside one part of a form or list |
/// | [KitMotion] | §10 the timings, curves and when things may loop |
/// | [KitHaptics] | §10, kit v2 §2.13 send, done and commit: the only vibration, obeying Settings › Vibration |
/// | [KitEffects], [KitEffectsScope] | §10 the person's motion, celebration and vibration choices (Settings › Appearance) |
/// | [KitGlass] | §10 a bounded solid surface floating over content (dock, rail, top controls, composer) |
/// | [KitFrost] | frosted glass (blur, saturation, tint, noise, hairline) for the chat composer and header, only while content scrolls under them; solid otherwise |
/// | [KitIllustration], [KitScene], [KitDraw], [KitPortalScene] | §10 drawings in the brand's line, drawn in code, that can move |
/// | [KitSurface] | kit v2 §4 the one solid box: a surface step fill, token shape and padding, optional hairline edge |
/// | [KitDivider] | kit v2 the one separator: a pixel-snapped hairline, optionally inset to a row's words |
/// | [KitIcon], [KitIconSize], [KitBrandMark] | kit v2 §9 the one way to draw a glyph at a designed size, and the open-portal mark |
/// | [KitChip], [KitChipKind], [KitChipTone], [KitChipWrap] | kit v2 §4-§5 a small rounded label, always with a word, and its wrapping row |
/// | [KitFeedItem] | Chats home: one conversation with its project, title, state and last line |
/// | [KitStatusTag], [KitStatusTagTone] | a small worded state tag at the end of a feed row |
/// | [KitFilterChips], [KitFilterChip] | one scrolling line of filter chips under a title |
/// | [KitScrollFade] | a sideways-scrolling line whose edges fade where there is more past them, never a hard cut mid-word |
/// | [KitLeadColumn] | which leading column a part's words start after: the list's 56 dp text rail, or the conversation's compact 48 |
/// | [KitFloatingAction] | a screen's one floating primary, a round accent plus at the thumb, clearing the dock |
/// | [KitAgentLine] | one quiet muted line, "Agent · place ⌄", that opens the choices behind it |
/// | [KitSessionTabs], [KitSessionTab] | a screen's open sessions as pills, shown from two; the one shown in mint |
/// | [KitStatusAnswer], [KitAnswerTone] | a status page's one big centred answer ("Everything is running"), its mark, one small line (or a live countdown) and one quiet action |
/// | [KitAgentChoice], [KitAgentChoiceState] | one agent as a large card in a grid: its big mark, name in title size and a calm line from its persona; the chosen one has a mint ring and a filled check badge, one not set up is dimmed and says Set up |
/// | [KitAgentGridSpan] | lets the one card spanning a grid's row know to lay out sideways |
/// | [KitAgentGrid] | agent cards in two equal columns, a pair as tall as its taller card; an odd last one spans the row sideways; one column at large text |
/// | [KitStartBox] | the glowing question field a new conversation starts with (two lines by default, growing as you type), its question as the placeholder |
/// | [KitSegmented], [KitSegment] | kit v2 §1.6 one choice among 2–4 short, always-visible options |
/// | [KitMenuItem], [KitMenuGroup], [showKitMenu], [KitMenuPanel] | kit v2 the one popup menu: groups (a named group gets a heading), checks, disabled reasons, destructive last |
/// | [KitTerm], [showKitTerm] | K2 §1.20 a term that explains itself |
/// | [KitUndo], [showKitUndo] | K2 §1.17, §4.1 the one Undo bar |
/// | [KitBottomInset], [KitClearance] | K2 §2.12 how much of the bottom is covered by something pinned or floating |
/// | [KitMaxWidth] | the one place a part gets its maximum width: it never paints wider than its role's cap, at the start edge or centred, and a phone is untouched |
/// | [KitReach] | how far a flat list may run its rows out over a sheet's rails, so its words start on the title's line |
/// | [KitSince], [KitSincePhase], [KitSinceStatus], [KitSinceTicks] | the one wait timer: slow after a while, then minute ticks |
/// | [KitTime] | F15 the one way a time is written: the clock in the person's 12/24-hour setting, a moment as clock, day or date |
/// | [KitImage], [KitAvatar], [KitZoom], [KitZoomController] | kit v2 sharp raster images, the one identity mark, and the one pinch/pan/zoom viewer |
/// | [KitImageGallery], [showKitImageGallery], [KitImageMarkup], [showKitImageMarkup] | the pictures of a conversation one swipe apart, and the pen and crop editor that sends a picture to the message field |
/// | [KitQr] | kit v2 §5 a QR code another device can scan |
/// | [KitErrorFallback] | what a part shows in place of itself when it fails to build: one plain sentence on the theme's ground, Back where a page can be left; safe above every theme and navigator |
/// | [KitStreamCaret] | the soft caret after the words of a reply still being written; breathes only where loops may run |
/// | [KitCarousel] | a row of cards to swipe through one page at a time with the next card peeking and where you are shown as dots or a bar; every card stays its own target |
/// | [KitFineTunePanel], [KitFineTuneKnob] | Settings › Appearance › Fine-tune: the knobs over the chosen style (corners, depth, density, edges) as segmented choices, each with the style's own choice in words, and a reset to the style's defaults while any differs |
/// | [KitOptionCard] | one visual choice drawn: a theme as a compact swatch in its own colours, a live look, a glowing-border style, a surface style's sample, or an icon with a line of words; the chosen one has a ring and a check |
/// | [KitAccentRow] | the Accent choice: "Match theme" and round swatches, the chosen one ringed and checked |
/// | [KitWheel], [KitWheelColumn] | a value chosen by rolling it under a band (a time, a day, an interval), one to three columns |
/// | [KitStepSlider] | one choice among a few ordered steps as a slider that rests only on the steps, with small and large letters at its ends and the chosen step's name under it |
/// | [KitSwatch], [KitSwatchGrid], [KitThemePreview] | kit v2 §5 choosing a theme or accent by looking, and a theme's live preview |
/// | [KitLevelMeter] | the microphone's input level as bars (decorative) |
/// | [KitContextRing], [KitContextLevel], [KitContextRingSize] | how full the model's context window is, as a ring with its percent; unknown draws nothing |
/// | [KitProgressRing], [KitTrailMark] | one long job as one big ring with a real figure (a measured share, or steps done of all), the sentence of what it does now and a slim step trail |
/// | [KitContextChart], [KitContextBar] | the context after each reply, as bars with the compaction line |
/// | [KitContextSheet] | the context details: big ring, tokens, model, window, one paragraph and the chart |
/// | [KitFactRow], [KitProvenance] | one remembered fact or search hit: its text, a provenance border and word (You told it, From a source, It guessed), and its source and date line |
/// | [KitTerminalView] | kit v2 §9.2 the terminal: a live xterm session or a transcript, in the theme's colours |
/// | [showKitTerminalLinkSheet] | a link tapped in the terminal: its whole address, Open link (through `openExternalLink`) or Copy link |
/// | [KitPageRoute] | §10 a pushed page with the kit's one transition |
/// | [KitSwap], [KitSpin], [KitAnimatedBox], [KitDim], [KitAnimatedValue], [KitPace] | §10 the small motion parts: cross-fade, spin, surface change, dim, eased number |
/// | [KitFindMark] | chat-2 the find-in-conversation mark: an accent wash behind a hit (passive .18, active .38, as [KitCodeBlock] marks), never a text colour |
/// | [KitArrival], [KitArrivalScope] | P9.4 a search result's arrival: the page opened for one row scrolls to it, focuses it and washes it once |
/// | [KitGroupNote] | P3.10 one muted line under a row group: what it leaves out, and why |
/// | [KitGuide] | A settings page that asks one decision at a time: a preview on top, a rail of chips that show their values (groups, or one group's questions), the open question with its "Changes" line, and on a guided visit a Next bar, a dot on the next chip and checks on answered ones (Settings › Appearance) |
/// | [KitIconButton] | kit v2 §1.10 the one icon-only control |
/// | [KitInset] | §2 a row of tertiary buttons pulled back so their labels line up with the text above |
/// | [KitProgressView] | §4 a [KitProgress] drawn: a rounded bar and its line of words |
/// | [KitWindowRow] | one limit window: its name, a bar of what is left and the amount, on one line |
/// | [KitPlanBlock] | one plan of a group: its name and tier, one small summary, and a line per limit window under them |
/// | [KitProgressRow] | a row holding a measured amount: a quota window, a download, the context used, a budget |
/// | [KitField] | kit v2 §1.4 the one labelled text input (plain, multiline, number, secret) |
/// | [KitSearchField], [KitSearchNoMatch] | kit v2 §1.5 the one search field, and its inline "nothing matches" |
/// | [KitChoiceList], [KitChoiceRow], [KitPickerRow], [showKitChoiceSheet] | kit v2 §1.6 choices in one panel, one choice's row, a setting row that opens the picker sheet |
/// | [KitChoiceTiles], [KitChoiceMore] | up to three big choices that lead with the reason (a tap chooses), and the quiet line that opens the rest |
/// | [KitChoiceCards] | two or three large answers to a first page's one question, a card each with an icon and a cost line; a tap chooses, the page's main button acts |
/// | [KitHeroQuestion] | the one big question a first page opens on, in display type |
/// | [KitDateTimeRow], [showKitDatePicker], [showKitTimePicker], [showKitDateTimePicker] | a date or time value's row and the pickers it opens |
/// | [showKitAlert], [showKitInputDialog] | kit v2 §4.8 a blocking alert with at most one action, and one short text entry |
/// | [KitDetailsFold], [showKitTechnicalDetails] | K2 §1.8 the one technical fold, and a raw error on its own sheet |
/// | [KitTappable] | the one region that acts on a tap: a 48 dp hit area, focus ring and hover |
/// | [KitTopBar], [KitShellControls] | kit v2 §1.18 a screen's header, and the shell's server pill and search |
/// | [KitNav], [KitNavRail] | kit v2 §8.1 the shell's navigation: the rail and the sidebar (no bar on a phone) |
/// | [KitTabSwitcher], [KitTabStrip] | §10 destinations switched with a fade-through, and a strip of tabs with counts |
/// | [KitStatusLineSlot], [KitStatusScope], [KitStatusContribution] | §5 where a window's one status line is drawn, the app-wide conditions, and a page's own |
/// | [KitAnimatedRows], [KitReveal], [KitEntrance], [KitRefresh] | §10 rows that come and go, a part that folds in and out, a part arriving, and pull to refresh |
/// | [KitScrollbar], [KitScrollArea], [KitOwnScrollbar], [KitContextRegion] | desktop: a draggable thumb, a long scrollable's controller, one thumb per box, and the right-click menu |
/// | [KitJumpPill], [KitJumpPillLayer] | a floating pill back to one end of a scroll area, and where it sits |
/// | [KitLogPanel] | K2 §1.11 the one log view: follows the newest line until the person scrolls up |
/// | [KitDiffView], [showKitDiff] | K2 §1.15 the one diff: unified on a phone, side by side when wide |
/// | [KitViewer], [showKitViewer] | the one file and page viewer, as a sheet, a page or a pane |
/// | [KitChecklist] | a job made of steps, with the steps only the person can do mixed in |
/// | [KitCapabilityExplainer] | kit v2 §2.2 a missing capability, explained in place and offered where it can be turned on |
/// | [KitAgentCard], [KitAgentCardMode] | the frame for a card an agent describes: eyebrow, title, parts, its ask, and its receipt, passed-over and unreadable looks |
/// | [KitHandoffRecord] | the record a new conversation starts from, ready to read before it is sent: size, what was left out, hidden secrets and a folded preview |
/// | [KitContinuedLine] | the one quiet line that links a conversation to the one it was continued in or from, and opens it |
/// | [KitPresence] | a part that fades in and out with its slot along a row, and is not built at all while hidden |
/// | [KitKeyValue], [KitKeyValueRow] | label and value rows for an agent's facts, tabular figures, long values wrap |
/// | [KitMiniTable] | a header and up to 20 rows of up to 6 columns that scrolls sideways inside its own panel |
/// | [KitChart], [KitChartKind], [KitChartSeries], [KitChartWords] | a bar or line chart in theme roles, one scale, a legend, and a text summary for a screen reader |
/// | [KitSenseAsk], [KitSenseKind], [KitSenseItem], [KitSenseAction] | an agent's ask for a photo, file or voice note from the phone: pick, preview, remove, send |
/// | [KitReceipt] | a write's receipt in place: sending, sent, done, not confirmed, not accepted |
/// | [showKitRequestSheet] | §2 a request's details on a sheet, with its answers pinned |
/// | [KitScanner] | the camera frame that finds a pairing QR |
/// | [KitBreadcrumb] | a folder trail |
/// | [KitTaskCard], [KitPriorityGlyph] | one task on the AI Team board, and its priority as signal bars |
/// | [KitBoardLane], [KitBoardLanes] | one board column's cards, and the board: column strip and lanes in step |
/// | [KitWorkGraph] | a team task's items and what each needs, as a graph |
/// | [TerminalKeyBar] | two rows of terminal keys above the phone's keyboard, with sticky Ctrl and Alt |
/// | [KitMessage], [KitTurn], [KitWorkLine], [KitToolRow] | STATE-16 a transcript's words, one turn, a turn's work folded under one chip, and one step of it |
/// | [KitBackgroundWorkLine], [KitBackgroundTask] | the quiet line under a finished turn while work the agent started still runs in the background |
/// | [KitStepTimeline] | a turn's work as one quiet line that opens in place to a timeline: a thin rail, a dot for a sentence, a tile for a tool step, the live mark for the step in progress, a preview card for a file write or edit |
/// | [KitMarkdown] | agent Markdown in kit text: the prose of a reply or a thought |
/// | [KitComposer], [KitComposerChips], [KitComposerStatusStrip] | the composer, its chips, and the standing facts above it |
/// | [KitPhraseChips] | the person's own common short phrases as one row above the message box: tap sends, long-press offers Edit and Hide |
/// | [KitQueuedMessage] | STATE-17 everything waiting to reach the agent, in one bubble |
/// | [KitAgentStrip] | who is working on a team task, in order |
/// | [KitFoldersOpenScene], [ServersLinkScene], [ServersWelcomeScene], [SetupPhoneScene], [SetupReadyScene], [SetupStepsScene], [SetupUnpluggedScene] | §10 scenes: a folder opening, the servers and setup moments |
/// | [StatesFolderScene], [StatesSearchScene], [StatesSheetScene], [StatesTerminalScene], [StatesTrayScene], [StatesUnpluggedScene], [StatesWorkingScene] | §10 scenes for the not-normal states |
/// | [TeamBoardScene], [TeamDiscoverRelayScene], [TeamDiscoverTeaserScene], [TeamIdleScene], [TeamMergedScene], [TeamNudgeScene], [TeamPlanningScene], [TeamRestScene], [TeamWakingScene] | §10 the AI Team's scenes |
///
/// Every not-normal state is a [KitStateView]; the retired `product_states.dart`
/// widgets are gone (kit-hygiene). Its error words (`productErrorText` and
/// friends) are not a kit part and are imported from there directly.
/// `test/design_standard_test.dart` checks the migrated screens.
library;

export 'kit_action_stack.dart';
export 'kit_agent_choice.dart';
export 'kit_agent_grid.dart';
export 'kit_agent_grid_span.dart';
export 'kit_agent_line.dart';
export 'kit_status_answer.dart';
export 'kit_ask_field.dart';
export 'kit_ask_line.dart';
export 'kit_buttons.dart';
export 'kit_effects.dart';
export 'kit_icon_button.dart';
export 'kit_image_gallery.dart';
export 'kit_image_markup.dart';
export 'kit_illustration.dart';
export 'kit_layout.dart';
export 'kit_live_background.dart';
export 'kit_live_look_preview.dart';
export 'kit_floating_panel.dart';
export 'kit_steer_box.dart';
export 'kit_link_scope.dart';
export 'kit_web_preview.dart';
export 'kit_widget_preview.dart';
export 'kit_widget_style_form.dart';
export 'kit_motion.dart';
export 'kit_panel.dart';
export 'kit_notice.dart';
export 'kit_progress.dart';
export 'kit_request_card.dart';
export 'kit_max_width.dart';
export 'kit_reach.dart';
export 'kit_row.dart';
export 'kit_row_parts.dart';
export 'kit_optical.dart';
export 'kit_lead_column.dart';
export 'kit_scroll_fade.dart';
export 'kit_page_heading.dart';
export 'kit_section_label.dart';
export 'kit_sliver_row_group.dart';
export 'kit_screen.dart';
export 'kit_sheet.dart';
export 'kit_skeleton_transcript.dart';
export 'kit_last_known.dart';
export 'kit_state_view.dart';
export 'kit_status_line.dart';
export 'kit_status_mark.dart';
export 'kit_bidi.dart';
export 'kit_copy.dart';
export 'kit_one_time_code.dart';
export 'kit_redact.dart';
export 'kit_technical_value.dart';
export 'kit_text.dart';
export 'kit_text_halo.dart';
export 'kit_tokens.dart';
export 'kit_task_mark.dart';
export 'kit_task_chip.dart';
export 'kit_task_sheet.dart';
export 'kit_bottom_inset.dart';
export 'kit_chip.dart';
export 'kit_fact_row.dart';
export 'kit_feed_item.dart';
export 'style/kit_signal.dart'
    show
        KitSignalAspect,
        KitTrack,
        kitSegmentTimer,
        kitSignalHead,
        kitSignalRing,
        kitSignalStartTick,
        kitSignalStopKey,
        kitSignalTies;
export 'kit_filter_chip.dart';
export 'kit_filter_chips.dart';
export 'kit_fine_tune_panel.dart';
export 'kit_floating_action.dart';
export 'kit_status_tag.dart';
export 'kit_code_block.dart';
export 'kit_divider.dart';
// The retired AppGlyph and AppBrandMark stay reachable only through
// app_iconography.dart (KitIcon.md), so new code reaches for KitIcon.
export 'kit_icon.dart' hide AppBrandMark, AppGlyph;
export 'kit_image.dart';
export 'kit_level_meter.dart';
export 'kit_context_chart.dart';
export 'kit_context_ring.dart';
export 'kit_progress_ring.dart';
export 'kit_presence.dart';
export 'kit_handoff_record.dart';
export 'kit_continued_line.dart';
export 'kit_context_sheet.dart';
export 'kit_menu.dart';
export 'kit_page_route.dart';
export 'kit_qr.dart';
export 'kit_date_time_picker.dart';
export 'kit_scanner.dart';
export 'kit_segmented.dart';
export 'kit_session_tabs.dart';
export 'kit_since.dart';
export 'kit_start_box.dart';
export 'kit_time.dart';
export 'kit_word_text.dart';
export 'kit_surface.dart';
export 'kit_surface_look.dart' show KitSurfaceLook, KitSurfaceRole;
export 'kit_swatch.dart';
export 'kit_error_fallback.dart';
export 'kit_step_slider.dart';
export 'kit_wheel.dart';
export 'kit_carousel.dart';
export 'kit_option_card.dart';
export 'kit_term.dart';
export 'kit_terminal_links.dart';
export 'kit_terminal_view.dart';
export 'kit_undo.dart';
export 'kit_group_note.dart';
export 'kit_guide.dart';
export 'scenes/portal_scene.dart';
export 'motion/kit_animated_rows.dart';
export 'motion/kit_haptics.dart';
export 'motion/kit_motion_parts.dart';
export 'motion/kit_page_transitions.dart';
export 'motion/kit_refresh.dart';
export 'motion/kit_reveal.dart';
export 'motion/kit_tab_switcher.dart';
export 'glass/kit_glass.dart';
export 'chat/kit_message.dart';
export 'chat/kit_phrase_chips.dart';
export 'chat/kit_transcript_excerpt.dart';
export 'chat/kit_turn.dart';
export 'kit_request_sheet.dart';
export 'kit_context_region.dart';
export 'kit_scrollbar.dart';
export 'chat/kit_step_timeline.dart';
export 'chat/kit_tool_row.dart';
export 'kit_viewer.dart';
export 'kit_capability_explainer.dart';
export 'kit_checklist.dart';
export 'kit_diff_view.dart';
export 'kit_board_lane.dart';
export 'kit_swipe_action.dart';
export 'chat/kit_markdown.dart';
export 'chat/kit_stream_caret.dart';
export 'kit_status_slot.dart';
export 'kit_breadcrumb.dart';
export 'kit_choice_list.dart';
export 'kit_choice_tiles.dart';
export 'kit_choice_cards.dart';
export 'kit_hero_question.dart';
export 'kit_details_fold.dart';
export 'kit_field.dart';
export 'kit_arrival.dart';
export 'kit_jump_pill.dart';
export 'kit_nav.dart';
export 'kit_needs_you.dart';
export 'kit_progress_row.dart';
export 'kit_plan_block.dart';
export 'kit_window_row.dart';
export 'kit_receipt.dart';
export 'kit_agent_card.dart';
export 'kit_chart.dart';
export 'kit_key_value.dart';
export 'kit_mini_table.dart';
export 'kit_sense_ask.dart';
export 'kit_search_field.dart';
export 'kit_tappable.dart';
export 'kit_ink_layer.dart';
export 'kit_system_bars.dart';
export 'kit_tip.dart';
export 'kit_top_bar.dart';
export 'kit_task_card.dart';
export 'kit_log_panel.dart';
export 'kit_work_graph.dart';
export 'chat/kit_agent_strip.dart';
export 'chat/kit_composer.dart';
export 'chat/kit_composer_chips.dart';
export 'chat/kit_project_file_list.dart';
export 'chat/kit_project_file_sheet.dart';
export 'chat/kit_find_mark.dart';
export 'chat/kit_work_line.dart';
export 'chat/kit_background_work_line.dart';
export 'chat/kit_queued_message.dart';
export 'kit_dialog.dart';

export 'team/kit_digest.dart';
export 'team/kit_findings_card.dart';
export 'team/kit_merge_queue.dart';
export 'team/kit_milestone_row.dart';
export 'team/kit_phase_card.dart';
export 'team/kit_plan_card.dart';
export 'team/kit_project_row.dart';
export 'team/kit_promote_card.dart';
export 'team/kit_server_lane.dart';
export 'team/kit_spec_block.dart';
export 'team/kit_team_data.dart';
export 'team/kit_timeline_day.dart';

export 'kit_server_trust.dart';
