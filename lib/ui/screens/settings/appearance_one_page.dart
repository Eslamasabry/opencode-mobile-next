part of '../settings_screen.dart';

/// Settings › Appearance for a returning person (owner, 2026-10-27: "looks
/// empty but condensed"): every question on one scrolling page, in the four
/// sections the guide's chips name (Colours, Style, Background, Text &
/// motion), each with its control in place, so any choice is one tap away.
/// A section is its questions under a hairline: no small group word above
/// the first question (impeccable audit 2026-10-28, #15: an eyebrow over a
/// heading says the same thing twice); the question is the heading. The
/// guide keeps the group names on its chips.
/// A question shows only while it applies (Strength while a look is on; Saver
/// says once per section why Style and Background wait), folding in and out
/// with [KitReveal]. "Guide me again" and "Reset to defaults" end the page.
///
/// Wide windows (expanded and up) set the sections in two columns: Colours
/// and Style, then Background and Text & motion.
///
/// Kit only: KitSectionLabel, KitText, KitDivider and KitReveal around the
/// question bodies the guide uses.
extension _AppearanceOnePage on _AppearanceSettingsScreenState {
  /// Whether [q] has anything to ask now.
  bool _applies(AppearanceQuestion q, KitEffects fx) => switch (q) {
    AppearanceQuestion.strength => fx.liveBackground,
    _ => true,
  };

  Widget _onePage(AppLocalizations copy, KitEffects fx, Widget failed) {
    final tokens = KitTokens.of(context);
    // A refused save is said under the question it was for; Reset's above
    // the page's last rows.
    final failedAt = _failedAt;
    Widget section(AppearanceGroup group, {required bool first}) =>
        _onePageSection(copy, group, fx, failed: failed, first: first);
    final groups = AppearanceGroup.values;
    final Widget sections;
    if (KitLayout.windowOf(context).isWide) {
      Widget column(List<AppearanceGroup> half) => Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          for (final (i, group) in half.indexed) section(group, first: i == 0),
        ],
      );
      sections = Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Expanded(child: column(groups.sublist(0, 2))),
          Expanded(child: column(groups.sublist(2))),
        ],
      );
    } else {
      sections = Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          for (final (i, group) in groups.indexed)
            section(group, first: i == 0),
        ],
      );
    }
    // The page's key, as KitGuide's on the guided visit.
    return KeyedSubtree(
      key: const ValueKey('appearance-settings'),
      child: ListView(
        key: const ValueKey('appearance-one-page'),
        padding: EdgeInsetsDirectional.only(
          top: tokens.space2,
          bottom: KitScreen.endPadding(context),
        ),
        children: [
          sections,
          if (failedAt == null) failed,
          Padding(
            padding: EdgeInsetsDirectional.only(top: tokens.space4),
            child: _homeBody(copy, fx),
          ),
        ],
      ),
    );
  }

  /// One heading and its questions, or Saver's word on why they wait.
  Widget _onePageSection(
    AppLocalizations copy,
    AppearanceGroup group,
    KitEffects fx, {
    required Widget failed,
    required bool first,
  }) {
    final tokens = KitTokens.of(context);
    final held = _saver && group.questions.any((q) => q.heldBySaver);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        // Sections part with a hairline and more air than questions do; a
        // style that rules its sections itself (Paper) keeps its one rule.
        if (!first) ...[
          SizedBox(height: tokens.space6),
          // Asked under the page's effects scope, where the style is.
          Builder(
            builder: (context) =>
                KitSurfaceLook.sectionRule(
                      context,
                      hairline: KitTokens.hairlineWidth(context),
                    ) ==
                    null
                ? const KitDivider()
                : const SizedBox.shrink(),
          ),
        ],
        // Saver's word on a held section has no question over it: the
        // group's name heads it.
        if (held) ...[
          KitSectionLabel(
            _AppearanceSettingsScreenState._groupLabel(copy, group),
            // The tour's anchors (MakeItYoursAnchors), as on the guide's start.
            key: ValueKey('appearance-group-${group.name}'),
            gapBefore: first ? tokens.space2 : tokens.space6,
          ),
          _heldBody(copy, group.questions.first, keyed: true),
        ] else
          for (final (i, q) in group.questions.indexed)
            KitReveal(
              child: _applies(q, fx)
                  ? _onePageQuestion(
                      copy,
                      q,
                      fx,
                      failed: failed,
                      // The section's first question carries the tour's
                      // anchor (MakeItYoursAnchors) the label used to.
                      anchor: i == 0
                          ? ValueKey('appearance-group-${group.name}')
                          : null,
                      top: i > 0
                          ? tokens.space6
                          : first
                          ? tokens.space2
                          : tokens.space6,
                    )
                  : null,
            ),
      ],
    );
  }

  /// A question in words, its control under it.
  Widget _onePageQuestion(
    AppLocalizations copy,
    AppearanceQuestion q,
    KitEffects fx, {
    required Widget failed,
    required double top,
    Key? anchor,
  }) {
    final tokens = KitTokens.of(context);
    return KeyedSubtree(
      key: _blocks[q],
      // Its own node for screen readers: without it every question's note
      // joined one long announcement (3820 on the emulator).
      child: Semantics(
        container: true,
        explicitChildNodes: true,
        child: Padding(
          padding: EdgeInsetsDirectional.only(top: top),
          child: Column(
            key: ValueKey('appearance-one-${q.name}'),
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Padding(
                padding: EdgeInsetsDirectional.symmetric(
                  horizontal: tokens.gutter,
                ),
                child: Semantics(
                  key: anchor,
                  header: true,
                  child: KitText(
                    _AppearanceSettingsScreenState._ask(copy, q).$1,
                    role: KitTextRole.rowTitle,
                  ),
                ),
              ),
              SizedBox(height: tokens.space3),
              _questionBody(copy, q, fx, onePage: true),
              if (_failedAt == q) failed,
            ],
          ),
        ),
      ),
    );
  }
}
