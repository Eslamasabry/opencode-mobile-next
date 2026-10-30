part of '../settings_screen.dart';

/// The parts of Appearance a search result or a link can mean; each opens
/// its question.
enum AppearanceSection { mode, effects, theme, style, background, text }

/// Settings › Appearance (`appearance-settings`), one decision at a time
/// ([KitGuide]): four chips at the top (Colours, Style, Background, Text &
/// motion), each showing what is set now. A chip opens its two or three
/// questions as smaller chips and the first of them (for a returning
/// person, the one last used there) at once; each question names what it
/// changes. There is no preview: every choice applies at once, so the page
/// itself (and the rest of the app) shows it.
///
/// The first visit is guided: a Next bar suggests the next question, a dot
/// marks the next chip, answered chips carry a check, and Finish ends with
/// "All set". Once finished or skipped (remembered on this device,
/// [AppearanceGuideStore]) the page is one scrolling page with every question
/// and its control in place (appearance_one_page.dart, owner 2026-10-27),
/// ending with "Guide me again" and "Reset to defaults" (which asks first, in
/// the page).
///
/// Saver holds Style and Background: their chips and sections say so, the
/// guide skips them, and each explains why with a way to turn Saver off.
/// Every choice is saved at once; a refused save puts it back and says so.
///
/// Kit only: KitGuide, KitOptionCard, KitAccentRow, KitSegmented,
/// KitStepSlider, KitNotice, KitRowGroup, KitRow, KitButton, KitSectionLabel,
/// KitDivider, KitReveal and KitText.
class AppearanceSettingsScreen extends StatefulWidget {
  final ConnectionController controller;

  /// The part a search result means: the page opens at its question.
  final AppearanceSection? initialSection;

  const AppearanceSettingsScreen({
    super.key,
    required this.controller,
    this.initialSection,
  });

  @override
  State<AppearanceSettingsScreen> createState() =>
      _AppearanceSettingsScreenState();
}

/// The most-used themes and looks, shown before "See all".
const _themePicks = [
  ThemePackId.opencode,
  ThemePackId.nord,
  ThemePackId.catppuccin,
  ThemePackId.dracula,
  ThemePackId.tokyoNight,
  ThemePackId.gruvbox,
  ThemePackId.rosePine,
  ThemePackId.solarized,
];
const _lookPicks = [
  'aurora',
  'mesh',
  'dotRipple',
  'lightRays',
  'bokeh',
  'silk',
  'topo',
];

class _AppearanceSettingsScreenState extends State<AppearanceSettingsScreen> {
  late final _store = AppearanceGuideStore(widget.controller.store.prefs);
  late AppearanceGuideRecord _record = _store.read();

  /// The guide shows on this visit (a first visit, or "Guide me again").
  late bool _guided = !_record.done;

  /// Finish was pressed: the chips keep their checks until one is tapped.
  bool _finished = false;

  /// The question on screen; null: the four chips.
  AppearanceQuestion? _question;

  /// The open question was answered (the Next button fills in).
  bool _picked = false;
  bool _confirmReset = false;
  bool _allThemes = false;
  bool _allLooks = false;

  /// What "Turn off Saver" goes back to.
  AppAppearance _beforeSaver = AppAppearance.dark;

  /// The page was opened at a question (search, a link): Back leaves it.
  late final String? _arrivalRow = KitArrivalScope.maybeRowId(context);
  bool _arrived = false;

  /// A choice the device refused to save, and the question it was for
  /// (null: Reset), so the one page says so under that question.
  bool _failed = false;
  AppearanceQuestion? _failedAt;

  /// Each question's place on the one page, to scroll to.
  final _blocks = {
    for (final question in AppearanceQuestion.values) question: GlobalKey(),
  };

  @override
  void initState() {
    super.initState();
    final appearance = widget.controller.appearance.value;
    if (appearance != AppAppearance.saver) _beforeSaver = appearance;
    final arrival = _arrivalQuestion(widget.initialSection, _arrivalRow);
    if (arrival != null) {
      _arrived = true;
      // The one page shows every question: a row's KitArrival scrolls to it,
      // a section is scrolled to here.
      if (_guided) {
        _question = arrival;
      } else if (_arrivalRow == null) {
        WidgetsBinding.instance.addPostFrameCallback(
          (_) => _scrollTo(arrival, animate: false),
        );
      }
    }
    WidgetsBinding.instance.addPostFrameCallback(
      (_) => mounted
          ? offerMakeItYours(
              context,
              widget.controller,
              onAppearance: true,
              showChips: _home,
            )
          : null,
    );
  }

  /// The question a search result or a link means: its row's, else its
  /// section's.
  static AppearanceQuestion? _arrivalQuestion(
    AppearanceSection? section,
    String? rowId,
  ) {
    if (rowId != null) {
      if (rowId == 'effects-live-intensity') return AppearanceQuestion.strength;
      if (rowId.startsWith('effects-live')) return AppearanceQuestion.look;
      if (rowId.startsWith('effects-glow')) return AppearanceQuestion.glow;
      if (rowId == 'effects-text') return AppearanceQuestion.text;
      if (rowId.startsWith('effects-')) return AppearanceQuestion.motion;
    }
    return switch (section) {
      AppearanceSection.mode => AppearanceQuestion.mode,
      AppearanceSection.theme => AppearanceQuestion.theme,
      AppearanceSection.effects => AppearanceQuestion.motion,
      AppearanceSection.style => AppearanceQuestion.style,
      AppearanceSection.background => AppearanceQuestion.look,
      AppearanceSection.text => AppearanceQuestion.text,
      null => null,
    };
  }

  bool get _saver => widget.controller.appearance.value == AppAppearance.saver;

  bool _held(AppearanceQuestion question) => _saver && question.heldBySaver;

  void _remember(AppearanceGuideRecord record) {
    _record = record;
    unawaited(_store.write(record));
  }

  // --- Moving around ------------------------------------------------------

  void _open(AppearanceQuestion question) {
    setState(() {
      _question = question;
      _picked = false;
      _confirmReset = false;
    });
    _remember(
      _record.copyWith(
        seen: _record.seen.contains(question)
            ? null
            : [..._record.seen, question],
        lastIn: {..._record.lastIn, question.group: question},
      ),
    );
  }

  /// A chip of the start view: its first open question, or for a returning
  /// person the one last used there. After Finish a chip opens the one page
  /// at its section.
  void _openGroup(AppearanceGroup group) {
    if (_finished) {
      setState(() {
        _guided = false;
        _finished = false;
      });
      WidgetsBinding.instance.addPostFrameCallback(
        (_) => _scrollTo(group.questions.first),
      );
      return;
    }
    final last = _record.lastIn[group];
    final first = group.questions.firstWhere(
      (q) => !_held(q),
      orElse: () => group.questions.first,
    );
    _open(!_guided && last != null && !_held(last) ? last : first);
  }

  void _home() {
    if (_question == null) return;
    setState(() {
      _question = null;
      _confirmReset = false;
    });
  }

  AppearanceQuestion? get _lastSeen =>
      AppearanceQuestion.order.where(_record.seen.contains).lastOrNull;

  void _next() {
    final question = _question;
    if (question == null) {
      final next = AppearanceQuestion.after(
        _record.seen.isEmpty ? null : _lastSeen,
        saver: _saver,
      );
      if (next != null) _open(next);
      return;
    }
    final next = AppearanceQuestion.after(question, saver: _saver);
    if (next != null) return _open(next);
    setState(() {
      _question = null;
      _finished = true;
    });
    _remember(_record.copyWith(done: true, seen: AppearanceQuestion.order));
  }

  void _skipGuide() {
    setState(() => _guided = false);
    _remember(_record.copyWith(done: true));
  }

  void _guideAgain() {
    setState(() {
      _guided = true;
      _finished = false;
      _question = null;
      _confirmReset = false;
    });
    _remember(
      AppearanceGuideRecord(lastIn: _record.lastIn, last: _record.last),
    );
  }

  /// Scrolls the one page to [question]'s place.
  void _scrollTo(AppearanceQuestion question, {bool animate = true}) {
    final place = _blocks[question]!.currentContext;
    if (!mounted || place == null) return;
    final still = !animate || KitMotion.reduced(context);
    unawaited(
      Scrollable.ensureVisible(
        place,
        duration: still ? Duration.zero : KitMotion.standard,
        curve: KitMotion.emphasized,
      ),
    );
  }

  // --- Saving -------------------------------------------------------------

  /// Saves an answer to [on] (default: the open question): the guide's Next
  /// fills in.
  Future<void> _save(
    Future<void> Function() write, [
    AppearanceQuestion? on,
  ]) async {
    final question = on ?? _question;
    setState(() {
      _failed = false;
      _failedAt = question;
      if (_question != null) _picked = true;
    });
    if (question != null) _remember(_record.copyWith(last: question));
    try {
      await write();
    } catch (_) {
      if (mounted) setState(() => _failed = true);
    }
  }

  Future<void> _choose(KitEffects next, [AppearanceQuestion? on]) =>
      _save(() => widget.controller.setEffects(next), on);

  Future<void> _setMode(AppAppearance mode) {
    if (mode != AppAppearance.saver) _beforeSaver = mode;
    return _save(
      () => widget.controller.setAppearance(mode),
      AppearanceQuestion.mode,
    );
  }

  Future<void> _reset() async {
    final controller = widget.controller;
    setState(() {
      _confirmReset = false;
      _failed = false;
      _failedAt = null;
    });
    try {
      await controller.setAppearance(AppAppearance.dark);
      await controller.setThemePack(ThemePackId.opencode);
      await controller.setAppearanceAccent(null);
      await controller.setTextSize(KitTextSize.normal);
      await controller.setEffects(
        KitEffects.defaults.copyWith(haptics: controller.effects.value.haptics),
      );
    } catch (_) {
      if (mounted) setState(() => _failed = true);
    }
  }

  // --- Words --------------------------------------------------------------

  static String _accentLabel(AppLocalizations copy, String id) => switch (id) {
    'mint' => copy.appearanceAccentMint,
    'teal' => copy.appearanceAccentTeal,
    'blue' => copy.appearanceAccentBlue,
    'violet' => copy.appearanceAccentViolet,
    'pink' => copy.appearanceAccentPink,
    'mono' => copy.appearanceAccentMono,
    _ => copy.appearanceAccentSilver,
  };

  static String _groupLabel(AppLocalizations copy, AppearanceGroup group) =>
      switch (group) {
        AppearanceGroup.colours => copy.appearanceGroupColours,
        AppearanceGroup.style => copy.appearanceGroupStyle,
        AppearanceGroup.background => copy.appearanceGroupBackground,
        AppearanceGroup.comfort => copy.appearanceGroupComfort,
      };

  static String _label(AppLocalizations copy, AppearanceQuestion q) =>
      switch (q) {
        AppearanceQuestion.mode => copy.appearanceChipMode,
        AppearanceQuestion.theme => copy.appearanceChipTheme,
        AppearanceQuestion.accentColour => copy.appearanceChipAccent,
        AppearanceQuestion.style => copy.appearanceChipStyle,
        AppearanceQuestion.shape => copy.appearanceChipShape,
        AppearanceQuestion.look => copy.appearanceChipLook,
        AppearanceQuestion.strength => copy.appearanceChipStrength,
        AppearanceQuestion.text => copy.appearanceChipText,
        AppearanceQuestion.motion => copy.appearanceChipMotion,
        AppearanceQuestion.glow => copy.appearanceChipGlow,
      };

  static (String, String) _ask(AppLocalizations copy, AppearanceQuestion q) =>
      switch (q) {
        AppearanceQuestion.mode => (
          copy.appearanceAskMode,
          copy.appearanceChangesMode,
        ),
        AppearanceQuestion.theme => (
          copy.appearanceAskTheme,
          copy.appearanceChangesTheme,
        ),
        AppearanceQuestion.accentColour => (
          copy.appearanceAskAccent,
          copy.appearanceChangesAccent,
        ),
        AppearanceQuestion.style => (
          copy.appearanceAskStyle,
          copy.appearanceChangesStyle,
        ),
        AppearanceQuestion.shape => (
          copy.appearanceAskShape,
          copy.appearanceChangesShape,
        ),
        AppearanceQuestion.look => (
          copy.appearanceAskLook,
          copy.appearanceChangesLook,
        ),
        AppearanceQuestion.strength => (
          copy.appearanceAskStrength,
          copy.appearanceChangesStrength,
        ),
        AppearanceQuestion.text => (
          copy.appearanceAskText,
          copy.appearanceChangesText,
        ),
        AppearanceQuestion.motion => (
          copy.appearanceAskMotion,
          copy.appearanceChangesMotion,
        ),
        AppearanceQuestion.glow => (
          copy.appearanceAskGlow,
          copy.appearanceChangesGlow,
        ),
      };

  static String _styleLabel(AppLocalizations copy, KitSurfaceStyle style) =>
      switch (style) {
        KitSurfaceStyle.solid => copy.appearanceSurfaceSolid,
        KitSurfaceStyle.soft => copy.appearanceSurfaceSoft,
        KitSurfaceStyle.brutal => copy.appearanceSurfaceBrutal,
        KitSurfaceStyle.paper => copy.appearanceSurfacePaper,
        KitSurfaceStyle.signal => copy.signalStyleName,
      };

  static String _cornersLabel(AppLocalizations copy, KitCorners corners) =>
      switch (corners) {
        KitCorners.round => copy.appearanceFineTuneCornersRound,
        KitCorners.soft => copy.appearanceFineTuneCornersSoft,
        KitCorners.sharp => copy.appearanceFineTuneCornersSharp,
      };

  static String _spacingLabel(AppLocalizations copy, KitDensity density) =>
      switch (density) {
        KitDensity.comfortable => copy.appearanceSpacingRoomy,
        KitDensity.compact => copy.appearanceSpacingCompact,
      };

  static String _intensityLabel(AppLocalizations copy, KitLiveIntensity v) =>
      switch (v) {
        KitLiveIntensity.subtle => copy.liveIntensitySubtle,
        KitLiveIntensity.normal => copy.liveIntensityNormal,
        KitLiveIntensity.bold => copy.liveIntensityBold,
      };

  static String _textLabel(AppLocalizations copy, KitTextSize size) =>
      switch (size) {
        KitTextSize.smallest => copy.textSizeSmallest,
        KitTextSize.small => copy.textSizeSmall,
        KitTextSize.normal => copy.textSizeDefault,
        KitTextSize.large => copy.textSizeLarge,
      };

  String _modeLabel(AppLocalizations copy, AppAppearance mode) =>
      switch (mode) {
        AppAppearance.system => copy.appearanceModeAuto,
        AppAppearance.saver => copy.appearanceModeSaver,
        _ => appearanceLabel(mode, context),
      };

  /// The motion in force: Saver keeps it off.
  KitMotionLevel _motion(KitEffects effects) =>
      _saver ? KitMotionLevel.off : effects.motion;

  /// What a question's chip says is set now.
  String _value(AppLocalizations copy, AppearanceQuestion q, KitEffects fx) {
    final controller = widget.controller;
    if (_held(q)) return copy.appearanceOffInSaver;
    final knobs = KitFineTunePanel.read(fx).knobs;
    return switch (q) {
      AppearanceQuestion.mode => _modeLabel(copy, controller.appearance.value),
      AppearanceQuestion.theme => themePackLabels[controller.themePack.value]!,
      AppearanceQuestion.accentColour => switch (appearanceAccentById(
        controller.appearanceAccent.value,
      )) {
        final accent? => _accentLabel(copy, accent.id),
        null => copy.appearanceAccentMatchShort,
      },
      AppearanceQuestion.style => _styleLabel(copy, fx.surfaceStyle),
      AppearanceQuestion.shape => copy.appearanceShapeValue(
        _cornersLabel(copy, knobs.corners),
        _spacingLabel(copy, knobs.density),
      ),
      AppearanceQuestion.look =>
        fx.liveBackground
            ? kitLiveStyleById(fx.liveStyle).label(copy)
            : copy.appearanceLookOff,
      AppearanceQuestion.strength =>
        fx.liveBackground
            ? _intensityLabel(copy, fx.liveIntensity)
            : copy.appearanceStrengthNoLook,
      AppearanceQuestion.text => _textLabel(copy, controller.textSize.value),
      AppearanceQuestion.motion => switch (_motion(fx)) {
        KitMotionLevel.full => copy.effectsMotionFull,
        KitMotionLevel.calm => copy.effectsMotionCalm,
        KitMotionLevel.off => copy.effectsMotionOff,
      },
      AppearanceQuestion.glow =>
        !fx.activityGlow
            ? copy.appearanceGlowOff
            : fx.glowStyle == KitGlowStyle.softRing
            ? copy.effectsGlowStyleSoft
            : copy.effectsGlowStyleClassic,
    };
  }

  /// What a group's chip says is set now.
  String _groupValue(AppLocalizations copy, AppearanceGroup g, KitEffects fx) {
    String v(AppearanceQuestion q) => _value(copy, q, fx);
    return switch (g) {
      AppearanceGroup.colours => copy.appearanceGroupPair(
        v(AppearanceQuestion.mode),
        v(AppearanceQuestion.theme),
      ),
      AppearanceGroup.style =>
        _saver ? copy.appearanceOffInSaver : v(AppearanceQuestion.style),
      AppearanceGroup.background =>
        _saver
            ? copy.appearanceOffInSaver
            : fx.liveBackground
            ? copy.appearanceGroupPair(
                v(AppearanceQuestion.look),
                v(AppearanceQuestion.strength),
              )
            : copy.appearanceLookOff,
      AppearanceGroup.comfort => copy.appearanceGroupPair(
        v(AppearanceQuestion.text),
        switch (_motion(fx)) {
          KitMotionLevel.full => copy.appearanceMotionPhraseFull,
          KitMotionLevel.calm => copy.appearanceMotionPhraseCalm,
          KitMotionLevel.off => copy.appearanceMotionPhraseOff,
        },
      ),
    };
  }

  // --- The rail and the bar -----------------------------------------------

  /// Every question the guide asks was seen (Saver's held ones aside).
  bool get _allSeen => AppearanceQuestion.order
      .where((q) => !_held(q))
      .every(_record.seen.contains);

  List<KitGuideItem> _items(AppLocalizations copy, KitEffects fx) {
    final question = _question;
    if (question == null) {
      final nextGroup = AppearanceQuestion.after(
        _lastSeen,
        saver: _saver,
      )?.group;
      final first = _record.seen.isEmpty;
      return [
        for (final group in AppearanceGroup.values)
          KitGuideItem(
            id: group.name,
            key: ValueKey('appearance-group-${group.name}'),
            label: _groupLabel(copy, group),
            value: _groupValue(copy, group, fx),
            done:
                (_guided || _finished) &&
                group.questions.every(
                  (q) => _record.seen.contains(q) || _held(q),
                ),
            next:
                _guided &&
                !_finished &&
                (first
                    ? group == AppearanceGroup.colours
                    : !_allSeen && group == nextGroup),
            held:
                _saver &&
                (group == AppearanceGroup.style ||
                    group == AppearanceGroup.background),
          ),
      ];
    }
    final group = question.group;
    final next = AppearanceQuestion.after(question, saver: _saver);
    return [
      for (final (i, q) in group.questions.indexed)
        KitGuideItem(
          id: q.name,
          key: ValueKey('appearance-question-${q.name}'),
          label: _label(copy, q),
          value: _value(copy, q, fx),
          done: _guided && q != question && _record.seen.contains(q),
          next: _guided && _picked && q == next,
          held: _held(q),
          semanticsLabel: copy.appearanceGuidePlace(
            _label(copy, q),
            _value(copy, q, fx),
            i + 1,
            group.questions.length,
            _groupLabel(copy, group),
          ),
        ),
    ];
  }

  KitGuideNext? _bar(AppLocalizations copy) {
    if (!_guided) return null;
    final total = AppearanceQuestion.order.length;
    const key = ValueKey('appearance-guide-next');
    final question = _question;
    if (question == null) {
      final seen = _record.seen.length;
      final next = AppearanceQuestion.after(
        seen == 0 ? null : _lastSeen,
        saver: _saver,
      );
      if (_finished || next == null || (seen > 0 && _allSeen)) {
        return KitGuideNext(key: key, count: copy.appearanceGuideSavesAsYouGo);
      }
      return KitGuideNext(
        key: key,
        count: seen == 0
            ? copy.appearanceGuideCountIntro(total)
            : copy.appearanceGuideCountSeen(seen, total),
        label: seen == 0
            ? copy.appearanceGuideStartButton
            : copy.appearanceGuideContinue(_groupLabel(copy, next.group)),
        onPressed: _next,
        ready: true,
      );
    }
    final next = AppearanceQuestion.after(question, saver: _saver);
    return KitGuideNext(
      key: key,
      count: copy.appearanceGuideCountAt(
        AppearanceQuestion.order.indexOf(question) + 1,
        total,
      ),
      label: next == null
          ? copy.appearanceGuideFinish
          : copy.appearanceGuideNext(
              next.group == question.group
                  ? _label(copy, next)
                  : _groupLabel(copy, next.group),
            ),
      onPressed: _next,
      ready: _picked,
    );
  }

  /// Under the four rows.
  Widget _homeBody(AppLocalizations copy, KitEffects fx) {
    final tokens = KitTokens.of(context);
    final inset = EdgeInsetsDirectional.only(
      start: tokens.gutter,
      end: tokens.gutter,
      top: tokens.space4,
    );
    if (_guided) {
      if (_finished || (_record.seen.isNotEmpty && _allSeen)) {
        return Padding(
          padding: inset,
          child: KitNotice(
            key: const ValueKey('appearance-guide-all-set'),
            tone: AppStatusTone.ok,
            title: copy.appearanceGuideAllSetTitle,
            message: copy.appearanceGuideAllSet,
          ),
        );
      }
      // The words keep the page gutter; Skip is a row like Reset, so its
      // words start where the others do (not at a button's own inset).
      return Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Padding(
            padding: inset,
            child: KitText(
              _record.seen.isEmpty
                  ? copy.appearanceGuideStart
                  : copy.appearanceGuideResume,
              role: KitTextRole.secondary,
              tone: KitTextTone.secondary,
            ),
          ),
          if (_record.seen.isEmpty)
            KitRowGroup(
              gapBefore: tokens.space2,
              children: [
                KitRow(
                  key: const ValueKey('appearance-guide-skip'),
                  title: copy.appearanceGuideSkip,
                  titleAccent: true,
                  onTap: _skipGuide,
                ),
              ],
            ),
        ],
      );
    }
    // The page's two rarer acts, as rows like every other page's (they act
    // in place, so their words take the accent and no chevron); Reset asks
    // first, in the same place.
    return KitReveal(
      child: _confirmReset
          ? Padding(
              padding: inset,
              child: KitNotice(
                key: const ValueKey('appearance-reset-confirm'),
                message: copy.appearanceGuideResetConfirm,
                actions: [
                  KitAction(
                    key: const ValueKey('appearance-reset-do'),
                    label: copy.appearanceGuideResetDo,
                    destructive: true,
                    onPressed: _reset,
                  ),
                  KitAction(
                    key: const ValueKey('appearance-reset-keep'),
                    label: copy.appearanceGuideResetKeep,
                    onPressed: () => setState(() => _confirmReset = false),
                  ),
                ],
              ),
            )
          : KitRowGroup(
              key: const ValueKey('appearance-home-acts'),
              gapBefore: tokens.sectionGap,
              children: [
                KitRow(
                  key: const ValueKey('appearance-guide-again'),
                  title: copy.appearanceGuideAgain,
                  titleAccent: true,
                  onTap: _guideAgain,
                ),
                KitRow(
                  key: const ValueKey('appearance-reset'),
                  title: copy.appearanceGuideReset,
                  titleAccent: true,
                  onTap: () => setState(() => _confirmReset = true),
                ),
              ],
            ),
    );
  }

  // --- The questions --------------------------------------------------------

  /// [children] in rows of [columns], every cell one width (and [height]).
  Widget _grid(List<Widget> children, {required int columns, double? height}) {
    final tokens = KitTokens.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        for (var row = 0; row < children.length; row += columns) ...[
          if (row > 0) SizedBox(height: tokens.space2),
          SizedBox(
            height: height,
            child: Row(
              crossAxisAlignment: height == null
                  ? CrossAxisAlignment.start
                  : CrossAxisAlignment.stretch,
              children: [
                for (var i = row; i < row + columns; i++) ...[
                  if (i > row) SizedBox(width: tokens.space2),
                  Expanded(
                    child: i < children.length
                        ? children[i]
                        : const SizedBox.shrink(),
                  ),
                ],
              ],
            ),
          ),
        ],
      ],
    );
  }

  /// A question's control on the page's rails, with a note under it.
  Widget _inset(Widget child, {String? note, Key? noteKey, Widget? after}) {
    final tokens = KitTokens.of(context);
    return Padding(
      padding: EdgeInsetsDirectional.only(
        start: tokens.gutter,
        end: tokens.gutter,
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          child,
          if (note != null) ...[
            SizedBox(height: tokens.space3),
            KitText(
              note,
              key: noteKey,
              role: KitTextRole.secondary,
              tone: KitTextTone.secondary,
            ),
          ],
          ?after,
        ],
      ),
    );
  }

  Widget _seeAll(String label, VoidCallback onPressed, Key key) => Padding(
    padding: EdgeInsetsDirectional.only(top: KitTokens.of(context).space3),
    child: KitButton.secondary(key: key, label: label, onPressed: onPressed),
  );

  /// Saver holds this question: why, and the way out. On the one page each
  /// held section has its own ([keyed]: its keys name the section).
  Widget _heldBody(
    AppLocalizations copy,
    AppearanceQuestion q, {
    bool keyed = false,
  }) => _inset(
    KitNotice(
      key: ValueKey(
        keyed
            ? 'appearance-saver-holds-${q.group.name}'
            : 'appearance-saver-holds',
      ),
      message: q.group == AppearanceGroup.background
          ? copy.appearanceSaverHoldsBackground
          : copy.appearanceSaverHoldsStyle,
      actions: [
        KitAction(
          key: ValueKey(
            keyed
                ? 'appearance-turn-off-saver-${q.group.name}'
                : 'appearance-turn-off-saver',
          ),
          label: copy.appearanceTurnOffSaver,
          onPressed: () => _setMode(_beforeSaver),
        ),
      ],
    ),
  );

  /// [q]'s control. On the one page ([onePage]) the pickers sit tighter
  /// (more across, motion as segments) so every question shows at once.
  Widget _questionBody(
    AppLocalizations copy,
    AppearanceQuestion q,
    KitEffects fx, {
    bool onePage = false,
  }) {
    if (_held(q)) return _heldBody(copy, q);
    final body = switch (q) {
      AppearanceQuestion.mode => _modeBody(copy),
      AppearanceQuestion.theme => _themeBody(copy, fx, onePage: onePage),
      AppearanceQuestion.accentColour => _accentBody(copy),
      AppearanceQuestion.style => _styleBody(copy, fx),
      AppearanceQuestion.shape => _shapeBody(copy, fx),
      AppearanceQuestion.look => _lookBody(copy, fx, onePage: onePage),
      AppearanceQuestion.strength => _strengthBody(copy, fx),
      AppearanceQuestion.text => _textBody(copy),
      AppearanceQuestion.motion => _motionBody(copy, fx, onePage: onePage),
      AppearanceQuestion.glow => _glowBody(copy, fx, onePage: onePage),
    };
    final row = _arrivalRow;
    return row != null && _arrived && _arrivalQuestion(null, row) == q
        ? KitArrival(id: row, child: body)
        : body;
  }

  Widget _modeBody(AppLocalizations copy) {
    final appearance = widget.controller.appearance.value;
    final modes = [
      (AppAppearance.system, AppIconography.systemTheme),
      (AppAppearance.light, AppIconography.lightMode),
      (AppAppearance.dark, AppIconography.darkMode),
      (AppAppearance.saver, AppIconography.leaf),
    ];
    final note = switch (appearance) {
      AppAppearance.saver => copy.appearanceSaverNote,
      AppAppearance.system => copy.appearanceModeAutoNote,
      _ => null,
    };
    return KeyedSubtree(
      key: const ValueKey('appearance-settings-entry'),
      child: _inset(
        KitSegmented<AppAppearance>(
          semanticsLabel: copy.e7SettingsUi69,
          tall: true,
          selected: appearance,
          segments: [
            for (final (value, icon) in modes)
              KitSegment(
                key: ValueKey('appearance-mode-${value.name}'),
                value: value,
                icon: icon,
                label: _modeLabel(copy, value),
              ),
          ],
          onChanged: _setMode,
        ),
        note: note,
        noteKey: appearance == AppAppearance.saver
            ? const ValueKey('appearance-saver-note')
            : null,
      ),
    );
  }

  Widget _themeBody(
    AppLocalizations copy,
    KitEffects fx, {
    bool onePage = false,
  }) {
    final controller = widget.controller;
    final saver = _saver;
    final brightness = saver ? Brightness.dark : Theme.of(context).brightness;
    final current = ThemeRoles.of(context);
    final selected = controller.themePack.value;
    // The chosen theme always shows among the first eight.
    // The one page shows two rows of three.
    final shown = onePage ? _themePicks.take(6).toList() : _themePicks;
    final picks = shown.contains(selected)
        ? shown
        : [...shown.take(shown.length - 1), selected];
    final ids = _allThemes ? ThemePackId.values : picks;
    final scale = MediaQuery.textScalerOf(context).scale(16) / 16;
    return _inset(
      _grid(
        columns: onePage ? 3 : 2,
        height: (onePage ? 64 : 84) + 30 * scale,
        [
          for (final id in ids)
            if ((id == ThemePackId.dynamic
                    ? harvestedDynamicPack.value
                    : themePack(id))
                case final pack)
              KitOptionCard.theme(
                key: ValueKey('theme-pack-${id.name}'),
                roles: pack?.palette(brightness).themeRoles ?? current,
                label: themePackLabels[id]!,
                selected: selected == id,
                saverLook: saver ? kitLiveStyleById(fx.liveStyle) : null,
                disabledReason: pack != null
                    ? null
                    : copy.e7AppearanceDynamicUnavailable,
                onPressed: pack != null
                    ? () => _save(
                        () => controller.setThemePack(id),
                        AppearanceQuestion.theme,
                      )
                    : null,
              ),
        ],
      ),
      after: _allThemes
          ? null
          : _seeAll(
              copy.appearanceSeeAllThemes(ThemePackId.values.length),
              () => setState(() => _allThemes = true),
              const ValueKey('appearance-see-all-themes'),
            ),
    );
  }

  Widget _accentBody(AppLocalizations copy) {
    final controller = widget.controller;
    final brightness = _saver ? Brightness.dark : Theme.of(context).brightness;
    final selected = controller.themePack.value;
    final own = effectiveThemePack(selected).palette(brightness).themeRoles;
    return _inset(
      KitAccentRow.grid(
        key: const ValueKey('appearance-accent'),
        semanticsLabel: copy.appearanceAccentTitle,
        matchLabel: copy.appearanceAccentMatchShort,
        theme: own,
        selected: appearanceAccentById(controller.appearanceAccent.value)?.id,
        swatches: [
          for (final accent in appearanceAccents)
            KitAccentSwatch(
              id: accent.id,
              // As it will be used here: guarded for this theme.
              color: own.withPartner(accent.of(brightness)).partner,
              label: _accentLabel(copy, accent.id),
            ),
        ],
        onChanged: (id) => _save(
          () => controller.setAppearanceAccent(id),
          AppearanceQuestion.accentColour,
        ),
      ),
      note: copy.appearanceAccentMatchNote(themePackLabels[selected]!),
    );
  }

  Widget _styleBody(AppLocalizations copy, KitEffects fx) {
    final tokens = KitTokens.of(context);
    final cards = _inset(
      _grid(columns: 2, [
        for (final style in KitSurfaceStyle.values)
          KitOptionCard.surface(
            key: ValueKey('appearance-surface-${style.name}'),
            style: style,
            label: _styleLabel(copy, style),
            selected: fx.surfaceStyle == style,
            onPressed: () {
              unawaited(
                _choose(
                  KitSurfaceLook.choose(fx, style),
                  AppearanceQuestion.style,
                ),
              );
            },
          ),
      ]),
    );
    return KeyedSubtree(
      key: const ValueKey('appearance-surfaces'),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        mainAxisSize: MainAxisSize.min,
        children: [
          cards,
          // Glass for the message box and header, under the style cards (the
          // control brings its own page gutter).
          Padding(
            padding: EdgeInsetsDirectional.only(
              start: tokens.gutter,
              end: tokens.gutter,
              top: tokens.space4,
              bottom: tokens.space2,
            ),
            child: KitText(copy.frostGlassQuestion, role: KitTextRole.label),
          ),
          const GlassChoiceControl(),
        ],
      ),
    );
  }

  Widget _shapeBody(AppLocalizations copy, KitEffects fx) {
    final tokens = KitTokens.of(context);
    final read = KitFineTunePanel.read(
      fx,
      highContrast: MediaQuery.maybeHighContrastOf(context) ?? false,
    );
    final defaults = read.defaults;
    final knobs = read.knobs;
    final name = _styleLabel(copy, read.style);
    T? own<T>(T value, T byDefault) => value == byDefault ? null : value;
    // Corners and spacing only: each style keeps its own depth and edges.
    void tuned({KitCorners? corners, KitDensity? density}) => _choose(
      fx.copyWith(
        fineTune: KitFineTune(
          corners: own(corners ?? knobs.corners, defaults.corners),
          density: own(density ?? knobs.density, defaults.density),
        ),
      ),
      AppearanceQuestion.shape,
    );
    final differs =
        knobs.corners != defaults.corners || knobs.density != defaults.density;
    Widget heading(String text) => Padding(
      padding: EdgeInsetsDirectional.only(
        top: tokens.space3,
        bottom: tokens.space2,
      ),
      child: KitText(text, role: KitTextRole.label),
    );
    return KeyedSubtree(
      key: const ValueKey('appearance-shape'),
      child: _inset(
        Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            heading(copy.appearanceFineTuneCorners),
            KitSegmented<KitCorners>(
              key: const ValueKey('appearance-fine-tune-corners'),
              semanticsLabel: copy.appearanceFineTuneCorners,
              selected: knobs.corners,
              segments: [
                for (final value in KitCorners.values)
                  KitSegment(
                    key: ValueKey('appearance-fine-tune-corners-${value.name}'),
                    value: value,
                    label: _cornersLabel(copy, value),
                  ),
              ],
              onChanged: (v) => tuned(corners: v),
            ),
            heading(copy.appearanceShapeSpacing),
            KitSegmented<KitDensity>(
              key: const ValueKey('appearance-fine-tune-density'),
              semanticsLabel: copy.appearanceShapeSpacing,
              selected: knobs.density,
              segments: [
                for (final value in KitDensity.values)
                  KitSegment(
                    key: ValueKey('appearance-fine-tune-density-${value.name}'),
                    value: value,
                    label: _spacingLabel(copy, value),
                  ),
              ],
              onChanged: (v) => tuned(density: v),
            ),
          ],
        ),
        note: copy.appearanceShapeNote(
          name,
          _cornersLabel(copy, defaults.corners),
          _spacingLabel(copy, defaults.density),
        ),
        after: differs
            ? Align(
                alignment: AlignmentDirectional.centerStart,
                child: KitButton(
                  key: const ValueKey('appearance-shape-reset'),
                  role: KitButtonRole.tertiary,
                  label: copy.appearanceFineTuneReset(name),
                  expand: false,
                  onPressed: () => _choose(
                    fx.copyWith(fineTune: KitFineTune.none),
                    AppearanceQuestion.shape,
                  ),
                ),
              )
            : null,
      ),
    );
  }

  Widget _lookBody(
    AppLocalizations copy,
    KitEffects fx, {
    bool onePage = false,
  }) {
    final chosen = fx.liveBackground ? kitLiveStyleById(fx.liveStyle).id : null;
    // The one page shows two rows of three: Off and five looks.
    final picks = onePage ? _lookPicks.take(5).toList() : _lookPicks;
    final looks = _allLooks
        ? kitLiveStyles
        : [
            for (final id in picks)
              // The chosen look always shows (the one page in the last
              // place, so the rows stay full).
              if (!onePage ||
                  chosen == null ||
                  picks.contains(chosen) ||
                  id != picks.last)
                kitLiveStyleById(id),
            if (chosen != null && !picks.contains(chosen))
              kitLiveStyleById(chosen),
          ];
    final scale = MediaQuery.textScalerOf(context).scale(16) / 16;
    return KeyedSubtree(
      key: const ValueKey('effects-live-style'),
      child: _inset(
        _grid(columns: 3, height: 136 + 44 * scale, [
          KitOptionCard.look(
            key: const ValueKey('live-style-off'),
            look: null,
            label: copy.appearanceLookOff,
            selected: chosen == null,
            onPressed: () => _choose(
              fx.copyWith(liveBackground: false),
              AppearanceQuestion.look,
            ),
          ),
          for (final look in looks)
            KitOptionCard.look(
              key: ValueKey('live-style-${look.id}'),
              look: look,
              label: look.label(copy),
              selected: look.id == chosen,
              onPressed: () => _choose(
                fx.copyWith(liveBackground: true, liveStyle: look.id),
                AppearanceQuestion.look,
              ),
            ),
        ]),
        after: _allLooks
            ? null
            : _seeAll(
                copy.appearanceSeeAllLooks(kitLiveStyles.length),
                () => setState(() => _allLooks = true),
                const ValueKey('appearance-see-all-looks'),
              ),
      ),
    );
  }

  Widget _strengthBody(AppLocalizations copy, KitEffects fx) {
    if (!fx.liveBackground) {
      return _inset(
        KitNotice(
          key: const ValueKey('appearance-strength-needs-look'),
          message: copy.appearanceStrengthNeedsLook,
          actions: [
            KitAction(
              key: const ValueKey('appearance-choose-look'),
              label: copy.appearanceChooseLookFirst,
              onPressed: () => _open(AppearanceQuestion.look),
            ),
          ],
        ),
      );
    }
    return _inset(
      KitSegmented<KitLiveIntensity>(
        semanticsLabel: copy.appearanceChipStrength,
        selected: fx.liveIntensity,
        segments: [
          for (final value in KitLiveIntensity.values)
            KitSegment(
              key: ValueKey('effects-live-intensity-${value.name}'),
              value: value,
              label: _intensityLabel(copy, value),
            ),
        ],
        onChanged: (v) =>
            _choose(fx.copyWith(liveIntensity: v), AppearanceQuestion.strength),
      ),
      note: copy.appearanceIntensityHelp,
    );
  }

  Widget _textBody(AppLocalizations copy) {
    final controller = widget.controller;
    return KeyedSubtree(
      key: const ValueKey('text-size-row'),
      child: _inset(
        KitStepSlider<KitTextSize>(
          semanticsLabel: copy.textSizeTitle,
          selected: controller.textSize.value,
          steps: [
            for (final value in KitTextSize.values)
              KitSliderStep(
                key: ValueKey('text-size-${value.name}'),
                value: value,
                label: _textLabel(copy, value),
              ),
          ],
          onChanged: (value) => _save(
            () => controller.setTextSize(value),
            AppearanceQuestion.text,
          ),
        ),
        note: copy.appearanceTextHelp,
      ),
    );
  }

  Widget _motionBody(
    AppLocalizations copy,
    KitEffects fx, {
    bool onePage = false,
  }) {
    final tokens = KitTokens.of(context);
    final motion = _motion(fx);
    // Reduced with Motion not Off: the system setting wins.
    final systemStill =
        KitMotion.reduced(context) && motion != KitMotionLevel.off;
    final levels = [
      (
        KitMotionLevel.full,
        AppIconography.sparkle,
        copy.effectsMotionFull,
        copy.effectsMotionFullHint,
      ),
      (
        KitMotionLevel.calm,
        AppIconography.waveform,
        copy.effectsMotionCalm,
        copy.effectsMotionCalmHint,
      ),
      (
        KitMotionLevel.off,
        AppIconography.stopCircle,
        copy.effectsMotionOff,
        copy.effectsMotionOffHint,
      ),
    ];
    void pick(KitMotionLevel level) => _choose(
      fx.copyWith(motion: level, celebrations: level == KitMotionLevel.full),
      AppearanceQuestion.motion,
    );
    final note = _saver
        ? copy.appearanceSaverMotion
        : systemStill
        ? copy.effectsMotionSystemOff
        : null;
    // The one page: three segments, and what the chosen one does under them.
    if (onePage) {
      return KeyedSubtree(
        key: const ValueKey('effects-motion'),
        child: _inset(
          KitSegmented<KitMotionLevel>(
            semanticsLabel: copy.appearanceChipMotion,
            tall: true,
            selected: motion,
            segments: [
              for (final (level, icon, label, _) in levels)
                KitSegment(
                  key: ValueKey('effects-motion-${level.name}'),
                  value: level,
                  icon: icon,
                  label: label,
                ),
            ],
            onChanged: pick,
          ),
          note: note ?? levels.firstWhere((entry) => entry.$1 == motion).$4,
        ),
      );
    }
    return KeyedSubtree(
      key: const ValueKey('effects-motion'),
      child: _inset(
        Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            for (final (i, (level, icon, label, caption))
                in levels.indexed) ...[
              if (i > 0) SizedBox(height: tokens.space2),
              KitOptionCard.plain(
                key: ValueKey('effects-motion-${level.name}'),
                icon: icon,
                label: label,
                caption: caption,
                selected: motion == level,
                onPressed: () => pick(level),
              ),
            ],
          ],
        ),
        note: note,
      ),
    );
  }

  Widget _glowBody(
    AppLocalizations copy,
    KitEffects fx, {
    bool onePage = false,
  }) {
    final tokens = KitTokens.of(context);
    final chosen = fx.activityGlow ? fx.glowStyle : null;
    final options = [
      (null, copy.appearanceGlowOff, copy.appearanceGlowOffHint, 'off'),
      (
        KitGlowStyle.classic,
        copy.effectsGlowStyleClassic,
        copy.appearanceGlowClassicHint,
        'classic',
      ),
      (
        KitGlowStyle.softRing,
        copy.effectsGlowStyleSoft,
        copy.appearanceGlowSoftHint,
        'softRing',
      ),
    ];
    final cards = [
      for (final (style, label, caption, id) in options)
        KitOptionCard.glow(
          key: ValueKey('effects-glow-$id'),
          style: style,
          label: label,
          // The one page says the chosen one's words once, under the row.
          caption: onePage ? null : caption,
          selected: chosen == style,
          // One choice: colours and speed stay at the app's own.
          onPressed: () => _choose(
            style == null
                ? fx.copyWith(activityGlow: false)
                : fx.copyWith(
                    activityGlow: true,
                    glowStyle: style,
                    glowColours: KitGlowColours.one,
                    glowSpeed: KitGlowSpeed.normal,
                  ),
            AppearanceQuestion.glow,
          ),
        ),
    ];
    return KeyedSubtree(
      key: const ValueKey('effects-glow'),
      child: _inset(
        onePage
            ? _grid(cards, columns: 3)
            : Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  for (final (i, card) in cards.indexed) ...[
                    if (i > 0) SizedBox(height: tokens.space2),
                    card,
                  ],
                ],
              ),
        note: onePage
            ? options.firstWhere((option) => option.$1 == chosen).$3
            : null,
      ),
    );
  }

  // --- The page -------------------------------------------------------------

  @override
  Widget build(BuildContext context) {
    final controller = widget.controller;
    final copy = _settingsCopy(context);
    final tokens = KitTokens.of(context);
    return ListenableBuilder(
      listenable: Listenable.merge([
        controller.appearance,
        controller.effects,
        controller.themePack,
        controller.appearanceAccent,
        controller.textSize,
        harvestedDynamicPack,
      ]),
      builder: (context, _) {
        // The stored choices: what the questions show and change.
        final effects = controller.effects.value;
        final saver = _saver;
        // The page shows the choices in force as the person makes them,
        // even where it sits above the app's own scope (tests); Saver
        // applies on top, as in main.dart.
        final inForce = saver ? effects.underSaver : effects;
        final question = _question;
        final failed = KitReveal(
          child: _failed
              ? Padding(
                  padding: EdgeInsetsDirectional.only(
                    start: tokens.gutter,
                    end: tokens.gutter,
                    top: tokens.space3,
                  ),
                  child: KitNotice(
                    key: const ValueKey('appearance-display-failed'),
                    tone: AppStatusTone.failure,
                    message: copy.effectsSaveFailed,
                  ),
                )
              : null,
        );
        return KitEffectsScope(
          effects: inForce,
          child: PopScope(
            // In-page Back returns to the four chips first, unless the page
            // was opened at this question.
            canPop: question == null || _arrived,
            onPopInvokedWithResult: (popped, _) {
              if (!popped) _home();
            },
            child: KitScreen(
              topBar: KitTopBar(title: copy.e7AppearanceTitle),
              width: KitScreenWidth.list,
              // A returning person sees every question at once; the guide's
              // chips are the first visit's (and Guide me again's).
              body: !_guided
                  ? _onePage(copy, effects, failed)
                  : KitGuide(
                      key: const ValueKey('appearance-settings'),
                      items: _items(copy, effects),
                      selected: question?.name,
                      onItem: (id) => question == null
                          ? _openGroup(AppearanceGroup.values.byName(id))
                          : _open(AppearanceQuestion.values.byName(id)),
                      group: question == null
                          ? null
                          : KitGuideGroup(
                              label: _groupLabel(copy, question.group),
                              backKey: const ValueKey('appearance-guide-back'),
                              onBack: _home,
                            ),
                      question: question == null
                          ? null
                          : KitGuideQuestion(
                              id: question.name,
                              title: _ask(copy, question).$1,
                              changes: _ask(copy, question).$2,
                              child: _questionBody(copy, question, effects),
                            ),
                      home: _homeBody(copy, effects),
                      notice: failed,
                      next: _bar(copy),
                    ),
            ),
          ),
        );
      },
    );
  }
}
