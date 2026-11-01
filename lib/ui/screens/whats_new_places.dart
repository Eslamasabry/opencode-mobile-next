import 'package:flutter/material.dart';

import '../../l10n/app_localizations.dart';
import '../../platform/platform_capabilities.dart';
import '../../state/connection.dart';
import '../../state/whats_new.dart';
import '../teaching/teaching.dart' show MakeItYoursAnchors;
import 'settings/updates_screen.dart';
import 'settings_screen.dart' show AgentsHubScreen, AppearanceSettingsScreen;

/// Where each "What's new" step's "Show me" goes: the page to open (or the
/// chat, wired at merge) and the key of the control to highlight. Every
/// target is one place in this file, so a merge that moves a control changes
/// one line (docs/release/whats-new.md lists what to connect).
abstract final class WhatsNewPlaces {
  /// Settings › This app › Updates' "Check now" row.
  static const updatesAnchor = ValueKey('updates-check-now');

  /// The page holding [updatesAnchor]: Settings › This app › Updates. Only
  /// Android has it (the app installs its own updates there alone).
  static Widget updatesPage() => const UpdatesScreen();

  /// The Settings › Agents list.
  static const agentsAnchor = ValueKey('agents-hub-list');

  /// The message box: a long press opens its paste menu, and its + opens
  /// the sheet with "Paste image" (the step's message names both).
  static const pasteImageAnchor = ValueKey('kit-composer-text');

  /// The microphone in the message box: tapped it opens voice mode, held it
  /// records until the finger lifts.
  static const holdToTalkAnchor = ValueKey('kit-composer-mic');

  /// Opens a chat and returns a context under its message box, so the tour
  /// can highlight a composer control. Not wired yet (see
  /// docs/release/whats-new.md): while null, a step that needs it has no
  /// "Show me".
  static Future<BuildContext?> Function(
    BuildContext context,
    ConnectionController controller,
  )?
  openChat;

  /// Whether [target] can be shown from here.
  static bool available(WhatsNewTarget target, ConnectionController? c) =>
      switch (target) {
        WhatsNewTarget.updates => platformCapabilities.isAndroid,
        WhatsNewTarget.agents || WhatsNewTarget.appearanceText => c != null,
        WhatsNewTarget.pasteImage ||
        WhatsNewTarget.holdToTalk => c != null && openChat != null,
      };

  /// The page a target opens, or null when it highlights inside a chat.
  static Widget? page(WhatsNewTarget target, ConnectionController? c) =>
      switch (target) {
        WhatsNewTarget.updates => updatesPage(),
        WhatsNewTarget.agents => AgentsHubScreen(controller: c!),
        WhatsNewTarget.appearanceText => AppearanceSettingsScreen(
          controller: c!,
        ),
        _ => null,
      };

  static Key anchor(WhatsNewTarget target) => switch (target) {
    WhatsNewTarget.updates => updatesAnchor,
    WhatsNewTarget.agents => agentsAnchor,
    WhatsNewTarget.appearanceText => MakeItYoursAnchors.comfort,
    WhatsNewTarget.pasteImage => pasteImageAnchor,
    WhatsNewTarget.holdToTalk => holdToTalkAnchor,
  };

  static String message(WhatsNewTarget target, AppLocalizations l10n) =>
      switch (target) {
        WhatsNewTarget.updates => l10n.whatsNewShowUpdates,
        WhatsNewTarget.agents => l10n.whatsNewShowAgents,
        WhatsNewTarget.appearanceText => l10n.tourMakeItYoursComfort,
        WhatsNewTarget.pasteImage => l10n.whatsNewShowPaste,
        WhatsNewTarget.holdToTalk => l10n.whatsNewShowMic,
      };
}
