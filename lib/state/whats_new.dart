import 'package:shared_preferences/shared_preferences.dart';

import '../l10n/app_localizations.dart';
import '../ui/app_iconography.dart';
import 'package:flutter/widgets.dart' show IconData;

/// Where a walkthrough step's "Show me" goes. A step with no target is about
/// something with no screen of its own (the message box), so it has no
/// "Show me".
enum WhatsNewTarget {
  /// Where the app's updates are (Settings › This app › Updates).
  updates,

  /// Settings › Agents.
  agents,

  /// The message box, where an image is pasted (needs a chat to open).
  pasteImage,

  /// The microphone in the message box (needs a chat to open).
  holdToTalk,

  /// Appearance › Text & motion.
  appearanceText,
}

/// One step of the "What's new" walkthrough: one new thing, one sentence.
class WhatsNewStep {
  const WhatsNewStep({required this.text, required this.icon, this.target});

  final IconData icon;

  final String Function(AppLocalizations l10n) text;
  final WhatsNewTarget? target;
}

/// The steps of the "What's new" walkthrough, by release version (three to
/// five each). The release process adds one entry per release
/// (docs/release/whats-new.md); a version with no entry shows nothing after
/// the update.
final Map<String, List<WhatsNewStep>> whatsNewByVersion = {
  '2.2.0': [
    WhatsNewStep(
      text: (l10n) => l10n.whatsNew220Updates,
      icon: AppIconography.systemDownload,
      target: WhatsNewTarget.updates,
    ),
    WhatsNewStep(
      text: (l10n) => l10n.whatsNew220Agents,
      icon: AppIconography.agent,
      target: WhatsNewTarget.agents,
    ),
    WhatsNewStep(
      text: (l10n) => l10n.whatsNew220LargeText,
      icon: AppIconography.info,
      target: WhatsNewTarget.appearanceText,
    ),
    WhatsNewStep(
      text: (l10n) => l10n.whatsNew220PasteImages,
      icon: AppIconography.image,
      target: WhatsNewTarget.pasteImage,
    ),
    WhatsNewStep(
      text: (l10n) => l10n.whatsNew220Voice,
      icon: AppIconography.mic,
      target: WhatsNewTarget.holdToTalk,
    ),
  ],
};

/// The version whose steps the walkthrough shows for [version]: the same one
/// if it has an entry, else the newest entry (About › What's new on a build
/// whose notes are not written yet).
String? whatsNewVersionFor(String version) {
  if (whatsNewByVersion.containsKey(version)) return version;
  if (whatsNewByVersion.isEmpty) return null;
  final versions = whatsNewByVersion.keys.toList()..sort(_compareVersions);
  return versions.last;
}

int _compareVersions(String a, String b) {
  final x = a.split('.').map((p) => int.tryParse(p) ?? 0).toList();
  final y = b.split('.').map((p) => int.tryParse(p) ?? 0).toList();
  for (var i = 0; i < 3; i++) {
    final c = (i < x.length ? x[i] : 0).compareTo(i < y.length ? y[i] : 0);
    if (c != 0) return c;
  }
  return 0;
}

/// Decides whether the walkthrough opens on this launch, and remembers the
/// build so it never opens twice.
abstract final class WhatsNew {
  /// The last build the person opened the app on (app-wide, not per profile).
  static const seenBuildKey = 'oc.whatsNewSeenBuild';

  /// True once per update: the first launch on a build different from the
  /// stored one, never on a fresh install ([existingInstall] false and
  /// nothing stored), and only for a version that has steps.
  static bool takeDue(
    SharedPreferences prefs, {
    required String version,
    required String build,
    required bool existingInstall,
  }) {
    final seen = prefs.getString(seenBuildKey);
    if (seen == build) return false;
    prefs.setString(seenBuildKey, build);
    if (seen == null && !existingInstall) return false;
    return whatsNewByVersion.containsKey(version);
  }
}
