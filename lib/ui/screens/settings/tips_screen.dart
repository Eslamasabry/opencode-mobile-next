import 'dart:async';

import 'package:flutter/material.dart';

import '../../../l10n/app_localizations.dart';
import '../../../state/connection.dart';
import '../../app_iconography.dart';
import '../../kit/kit.dart';
import '../../teaching/teaching.dart';

/// Help › Tips (docs/ux-system/teaching.md): every registered tip and mini
/// tutorial, each with "Show me", and "Show tips again", which lets every
/// tip and tutorial show again at its moment (and the one-time cards too).
///
/// "Show me" replays a tip in place, on its row; a tutorial opens its
/// screen and runs there. Nothing is counted for a replay.
class TipsScreen extends StatefulWidget {
  const TipsScreen({super.key, required this.controller});

  final ConnectionController controller;

  @override
  State<TipsScreen> createState() => _TipsScreenState();
}

class _TipsScreenState extends State<TipsScreen> {
  bool _reset = false;

  @override
  void initState() {
    super.initState();
    widget.controller.tips.addListener(_changed);
  }

  @override
  void dispose() {
    widget.controller.tips.removeListener(_changed);
    super.dispose();
  }

  void _changed() {
    if (mounted) setState(() {});
  }

  Future<void> _showTipsAgain() async {
    await widget.controller.nudges.reset();
    await widget.controller.tips.reset();
    if (mounted) setState(() => _reset = true);
  }

  Widget _row(BuildContext context, AppLocalizations l10n, TeachingEntry e) {
    final tips = widget.controller.tips;
    final tour = e.kind == TeachingKind.tour;
    final showMe = KitButton.tertiary(
      key: ValueKey('tips-show-me-${e.id}'),
      label: l10n.tipsShowMe,
      onPressed: () {
        if (tour) {
          unawaited(replayTour(context, widget.controller, e.id));
        } else {
          tips.replay(e.id);
        }
      },
    );
    return KitRow(
      key: ValueKey('tips-row-${e.id}'),
      leading: KitRow.icon(
        context,
        tour ? AppIconography.playCircle : AppIconography.idea,
      ),
      // The name alone: a tip's sentence is what "Show me" shows.
      title: e.title(l10n),
      // At 2× text "Show me" leaves the name a narrow column.
      titleMaxLines: 3,
      trailing: tour
          ? showMe
          : KitTip(
              tipKey: ValueKey('tips-replay-${e.id}'),
              message: e.message(l10n),
              visible: tips.isActive(e.id) && tips.activeIsReplay,
              onDismissed: () => tips.dismiss(e.id),
              child: showMe,
            ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final tokens = KitTokens.of(context);
    final tips = [
      for (final e in teachingRegistry)
        if (e.kind == TeachingKind.tip) e,
    ];
    final tours = [
      for (final e in teachingRegistry)
        if (e.kind == TeachingKind.tour) e,
    ];
    return KitScreen(
      topBar: KitTopBar(title: l10n.tipsTitle),
      width: KitScreenWidth.reading,
      body: Builder(
        builder: (context) => ListView(
          key: const ValueKey('tips-page'),
          padding: EdgeInsetsDirectional.only(
            top: tokens.space2,
            bottom: KitScreen.endPadding(context),
          ),
          children: [
            Padding(
              padding: EdgeInsetsDirectional.symmetric(
                horizontal: tokens.gutter,
              ),
              child: KitText(l10n.tipsIntro, role: KitTextRole.secondary),
            ),
            KitSectionLabel(l10n.tipsTipsLabel),
            KitRowGroup(
              key: const ValueKey('tips-group-tips'),
              children: [for (final e in tips) _row(context, l10n, e)],
            ),
            KitSectionLabel(l10n.tipsToursLabel),
            KitRowGroup(
              key: const ValueKey('tips-group-tours'),
              children: [for (final e in tours) _row(context, l10n, e)],
            ),
            SizedBox(height: tokens.sectionGap),
            KitRowGroup(
              children: [
                KitRow(
                  key: const ValueKey('tips-show-again'),
                  leading: KitRow.icon(context, AppIconography.idea),
                  title: l10n.discoverShowTipsAgain,
                  supporting: TextSpan(
                    text: _reset
                        ? l10n.discoverShowTipsDone
                        : l10n.discoverShowTipsSubtitle,
                  ),
                  supportingMaxLines: 2,
                  onTap: () => unawaited(_showTipsAgain()),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
