import 'dart:async';

import 'package:flutter/widgets.dart';

import '../../l10n/app_localizations.dart';
import '../../state/connection.dart';
import '../../state/tips.dart';
import '../kit/kit_page_route.dart';
import '../kit/kit_tip.dart';
import '../screens/settings_screen.dart' show AppearanceSettingsScreen;

/// The teaching system's registry (docs/ux-system/teaching.md): every tip
/// and mini tutorial the app has, declared once. Help › Tips lists them all;
/// a test fails when an entry has no Help › Tips row or no en and ar copy.

/// A one-sentence tip, or a 2–4 step mini tutorial.
enum TeachingKind { tip, tour }

/// One registered tip or mini tutorial.
@immutable
class TeachingEntry {
  const TeachingEntry({
    required this.id,
    required this.kind,
    required this.title,
    required this.message,
    required this.copyKeys,
    required this.anchors,
    required this.trigger,
    this.maxCount = TipController.defaultMaxCount,
    this.priority = 0,
    this.steps,
  });

  /// Stable: the storage name in `oc.tips` and the Help › Tips row key.
  final String id;
  final TeachingKind kind;

  /// The Help › Tips row's name.
  final String Function(AppLocalizations l10n) title;

  /// The tip's sentence, or the tutorial's offer words.
  final String Function(AppLocalizations l10n) message;

  /// Every ARB key the entry's words use (checked for en and ar).
  final List<String> copyKeys;

  /// The keys of the controls it points at.
  final List<Key> anchors;

  /// When it shows, in plain words (for people reading the registry).
  final String trigger;

  final int maxCount;

  /// A higher priority replaces a lower one on screen.
  final int priority;

  /// A tutorial's steps.
  final List<KitTourStep> Function(AppLocalizations l10n)? steps;
}

abstract final class TeachingIds {
  static const stopAfterStep = 'stop-after-step';
  static const sendLater = 'send-later';
  static const makeItYours = 'make-it-yours';

  /// The earlier three-step Appearance tour "Make it yours" replaced: an
  /// answer given to it (done, declined) stands for the new one.
  static const legacyAppearanceTour = 'appearance-studio';
}

/// The Appearance chips the "Make it yours" steps point at: the four chips
/// of the guided start view of `AppearanceSettingsScreen`.
abstract final class MakeItYoursAnchors {
  /// Colours: light or dark, the theme and the accent.
  static const colours = ValueKey('appearance-group-colours');

  /// Style: Solid, Soft, Brutal, Paper, and its shape.
  static const style = ValueKey('appearance-group-style');

  /// Background: the look behind every page and its strength.
  static const background = ValueKey('appearance-group-background');

  /// Text & motion: text size, motion and the reply glow.
  static const comfort = ValueKey('appearance-group-comfort');

  static const all = <Key>[colours, style, background, comfort];
}

/// Every tip and mini tutorial, in the order Help › Tips lists them.
final List<TeachingEntry> teachingRegistry = [
  TeachingEntry(
    id: TeachingIds.stopAfterStep,
    kind: TeachingKind.tip,
    title: (l10n) => l10n.tipStopAfterStepTitle,
    message: (l10n) => l10n.tipStopAfterStep,
    copyKeys: const ['tipStopAfterStepTitle', 'tipStopAfterStep'],
    anchors: const [Key('chat-stop-button')],
    trigger:
        'a reply has run for 10 s and Stop offers "Stop after this step"; '
        'done once the person stops after a step',
    priority: 2,
  ),
  TeachingEntry(
    id: TeachingIds.sendLater,
    kind: TeachingKind.tip,
    title: (l10n) => l10n.tipSendLaterTitle,
    message: (l10n) => l10n.tipSendLater,
    copyKeys: const ['tipSendLaterTitle', 'tipSendLater'],
    anchors: const [Key('chat-send-button')],
    trigger:
        'words wait in the box and Send offers "Send later or repeat"; '
        'done once the person opens it',
    priority: 1,
  ),
  TeachingEntry(
    id: TeachingIds.makeItYours,
    kind: TeachingKind.tour,
    title: (l10n) => l10n.tourMakeItYoursTitle,
    message: (l10n) => l10n.tourMakeItYoursOffer,
    copyKeys: const [
      'tourMakeItYoursTitle',
      'tourMakeItYoursOffer',
      'tourAppearanceStart',
      'tourNotNow',
      'tourMakeItYoursColours',
      'tourMakeItYoursStyle',
      'tourMakeItYoursBackground',
      'tourMakeItYoursComfort',
    ],
    anchors: MakeItYoursAnchors.all,
    trigger:
        'after the first reply: the first visit to Settings › This app › '
        'Appearance, or a screen that calls offerMakeItYours (offered once)',
    maxCount: 1,
    // Four steps over the real Appearance chips, in the guide's order; the
    // live preview stays in sight above them and is named in the first.
    steps: (l10n) => [
      KitTourStep(
        anchors: const [MakeItYoursAnchors.colours],
        message: l10n.tourMakeItYoursColours,
      ),
      KitTourStep(
        anchors: const [MakeItYoursAnchors.style],
        message: l10n.tourMakeItYoursStyle,
      ),
      KitTourStep(
        anchors: const [MakeItYoursAnchors.background],
        message: l10n.tourMakeItYoursBackground,
      ),
      KitTourStep(
        anchors: const [MakeItYoursAnchors.comfort],
        message: l10n.tourMakeItYoursComfort,
      ),
    ],
  ),
];

TeachingEntry teachingEntry(String id) =>
    teachingRegistry.firstWhere((entry) => entry.id == id);

/// Offers a registered tip at its moment; true when it is now on screen.
bool offerTip(
  TipController tips,
  String id, {
  Object? visit,
  TipMoment moment = const TipMoment(),
}) {
  final entry = teachingEntry(id);
  return tips.offer(
    id,
    visit: visit,
    maxCount: entry.maxCount,
    priority: entry.priority,
    moment: moment,
  );
}

AppLocalizations _l10n(BuildContext context) =>
    lookupAppLocalizations(Localizations.localeOf(context));

/// Whether "Make it yours" was answered: taken, declined or done, or the
/// Appearance tour before it.
bool makeItYoursAnswered(TipController tips) =>
    tips.record(TeachingIds.makeItYours).done ||
    tips.record(TeachingIds.legacyAppearanceTour).done;

/// Runs "Make it yours" over the Appearance screen of [context], on its four
/// chips ([showChips] brings them back first).
Future<void> runMakeItYours(
  BuildContext context,
  TipController tips, {
  VoidCallback? showChips,
}) async {
  showChips?.call();
  // The chips' first frame, then the tour over them.
  await WidgetsBinding.instance.endOfFrame;
  if (!context.mounted) return;
  final steps = teachingEntry(TeachingIds.makeItYours).steps!(_l10n(context));
  await KitTour.show(context, steps: steps);
  tips.markUsed(TeachingIds.makeItYours);
}

/// Offers "Make it yours" once, never forced, as a card at the bottom of
/// the screen of [context]: after the first reply, at the first visit to
/// Appearance (which passes [showChips]) or from any other screen, which
/// then opens Appearance for the tour. "Not now", or leaving the screen, is
/// an answer too: it is not offered again (Help › Tips still replays it).
void offerMakeItYours(
  BuildContext context,
  ConnectionController controller, {
  bool onAppearance = false,
  VoidCallback? showChips,
}) {
  final tips = controller.tips;
  if (makeItYoursAnswered(tips)) return;
  final offered = offerTip(
    tips,
    TeachingIds.makeItYours,
    visit: context,
    // Nothing is taught before the first reply (the nudges' rule too).
    moment: TipMoment(onboarding: !controller.nudges.firstReplySeen),
  );
  if (!offered) return;
  final l10n = _l10n(context);
  unawaited(() async {
    final start = await KitTour.offer(
      context,
      message: l10n.tourMakeItYoursOffer,
      startLabel: l10n.tourAppearanceStart,
      notNowLabel: l10n.tourNotNow,
      offerKey: const ValueKey('tour-offer-make-it-yours'),
    );
    // Offered once: taking it, declining or leaving is the answer, so the
    // Appearance page it opens offers nothing of its own.
    tips.markUsed(TeachingIds.makeItYours);
    if (!start || !context.mounted) return;
    if (onAppearance) {
      await runMakeItYours(context, tips, showChips: showChips);
    } else {
      await _openAndRun(context, controller);
    }
  }());
}

/// Opens Appearance over [context] and runs "Make it yours" on it.
Future<void> _openAndRun(
  BuildContext context,
  ConnectionController controller,
) async {
  final navigator = Navigator.of(context);
  unawaited(
    pushKitPage<void>(
      context,
      (_) => AppearanceSettingsScreen(controller: controller),
    ),
  );
  // The page's first frames, then the tour over it.
  await Future<void>.delayed(const Duration(milliseconds: 400));
  if (!navigator.mounted) return;
  await KitTour.show(
    navigator.context,
    steps: teachingEntry(TeachingIds.makeItYours).steps!(
      _l10n(navigator.context),
    ),
  );
}

/// Help › Tips' "Show me" for a tutorial: opens its screen and runs it.
Future<void> replayTour(
  BuildContext context,
  ConnectionController controller,
  String id,
) async {
  if (id != TeachingIds.makeItYours) return;
  final tips = controller.tips..replay(id);
  await _openAndRun(context, controller);
  tips.dismiss(id);
}
