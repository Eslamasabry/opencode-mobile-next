import 'package:flutter/material.dart';

import '../../../l10n/app_localizations.dart';
import '../../../platform/platform_capabilities.dart';
import '../../app_iconography.dart';
import '../../kit/kit.dart';

/// Help › Browser mode: what the browser build can and cannot do, and why.
///
/// Reached from the Help group only when running in a browser. Every line is
/// a fact about this page or this build read from [PlatformCapabilities];
/// nothing here is a toggle, because none of the limits are the app's to
/// lift. They are the browser's, and the copy says how to get around each
/// where a way exists (a secure origin, an HTTPS server, the phone app).
class BrowserModeScreen extends StatelessWidget {
  const BrowserModeScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final tokens = KitTokens.of(context);
    final capabilities = platformCapabilities;
    return KitScreen(
      key: const ValueKey('browser-mode-screen'),
      topBar: KitTopBar(title: l10n.browserModeTitle),
      width: KitScreenWidth.reading,
      body: Builder(
        builder: (context) => ListView(
          padding: EdgeInsetsDirectional.only(
            top: tokens.space2,
            bottom: KitScreen.endPadding(context),
          ),
          children: [
            Padding(
              padding: EdgeInsetsDirectional.symmetric(
                horizontal: tokens.gutter,
              ),
              child: KitText(
                l10n.browserModeIntro,
                role: KitTextRole.secondary,
              ),
            ),
            KitSectionLabel(l10n.browserModeAvailableHeading),
            KitRowGroup(
              children: [
                _fact(
                  context,
                  'browser-mode-servers',
                  AppIconography.server,
                  l10n.browserModeAvailableServers,
                ),
                // Keyboard conventions are a fact of the desktop browser
                // only; a phone browser keeps the touch product and this row
                // is left out rather than promising a keyboard it lacks.
                if (capabilities.usesDesktopInteractions)
                  _fact(
                    context,
                    'browser-mode-keyboard',
                    AppIconography.keyboard,
                    l10n.browserModeAvailableKeyboard,
                  ),
                _fact(
                  context,
                  'browser-mode-paste',
                  AppIconography.paste,
                  l10n.browserModeAvailablePaste,
                ),
              ],
            ),
            KitSectionLabel(l10n.browserModeUnavailableHeading),
            KitRowGroup(
              children: [
                _fact(
                  context,
                  'browser-mode-termux',
                  AppIconography.phone,
                  l10n.browserModeUnavailableTermux,
                ),
                _fact(
                  context,
                  'browser-mode-voice',
                  AppIconography.mic,
                  l10n.browserModeUnavailableVoice,
                ),
                _fact(
                  context,
                  'browser-mode-background',
                  AppIconography.notificationImportant,
                  l10n.browserModeUnavailableBackground,
                ),
                if (!capabilities.supportsAgentSocketConnections)
                  _fact(
                    context,
                    'browser-mode-agent-socket',
                    AppIconography.blocked,
                    l10n.browserModeUnavailableAgentSocket,
                  ),
              ],
            ),
            KitSectionLabel(l10n.browserModeContextHeading),
            KitRowGroup(
              children: [
                _fact(
                  context,
                  'browser-mode-secure-context',
                  capabilities.secureContext
                      ? AppIconography.locked
                      : AppIconography.privacyWarning,
                  capabilities.secureContext
                      ? l10n.browserModeSecureContextOk
                      : l10n.browserModeSecureContextMissing,
                ),
                _fact(
                  context,
                  'browser-mode-mixed-content',
                  AppIconography.secureNetwork,
                  l10n.browserModeMixedContent,
                ),
                _fact(
                  context,
                  'browser-mode-cors',
                  AppIconography.networkOff,
                  l10n.browserModeCors,
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  /// One sentence with its mark. The sentence is the row: nothing to tap,
  /// because none of these limits can be lifted from here.
  Widget _fact(BuildContext context, String key, IconData icon, String text) =>
      KitRow(
        key: ValueKey(key),
        leading: KitRow.icon(context, icon),
        title: text,
        titleMaxLines: 8,
      );
}
