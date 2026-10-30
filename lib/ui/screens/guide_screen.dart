import 'package:flutter/material.dart';

import '../../l10n/app_localizations.dart';
import '../../platform/platform_capabilities.dart';
import '../app_theme.dart';
import '../kit/kit_buttons.dart';
import '../kit/kit_code_block.dart';
import '../kit/kit_notice.dart';
import '../kit/kit_page_route.dart';
import '../kit/kit_row.dart';
import '../kit/kit_row_parts.dart';
import '../kit/kit_screen.dart';
import '../kit/kit_text.dart';
import '../kit/kit_tokens.dart';
import '../kit/kit_top_bar.dart';
import '../setup_commands.dart';
import 'demo_screen.dart';
import 'phone_setup/phone_setup_routes.dart';
import 'servers_screen.dart' show ServersRouteRequest;

/// Setup guide. Leads with the one story a first-time user needs — run
/// `opencode2 pair`, scan or paste, start talking — and folds every other
/// route (HTTPS proxies, SSH tunnels, older `opencode serve` servers, Termux
/// internals) behind an "Advanced" disclosure so nobody has to pick a path
/// before they know what the app does.
///
/// Step 2 acts as well as explains: its words name the buttons of the Add
/// server flow and its "Add server" button opens that flow, so nothing
/// sends the reader to find Servers first. On a phone that can host its
/// own server the guide says so: no computer is needed.
class GuideScreen extends StatelessWidget {
  final bool embedded;
  const GuideScreen({super.key, this.embedded = false});

  Widget _body(BuildContext context) {
    final tokens = KitTokens.of(context);
    final l10n = _sharedCopy(context);
    // The on-device path is Termux, which is Android-only. A desktop reader
    // is told about the one path that exists for them rather than a second
    // one they cannot take.
    final onDevice = platformCapabilities.supportsTermux;
    final canScan = platformCapabilities.supportsQrPairing;
    final rails = EdgeInsets.symmetric(horizontal: tokens.gutter);
    return ListView(
      padding: EdgeInsets.only(
        top: tokens.space3,
        bottom: KitScreen.endPadding(context),
      ),
      children: [
        Padding(
          padding: rails,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              KitText(
                l10n.e7SharedThreeStepsToYourFirstSession,
                role: KitTextRole.title,
              ),
              SizedBox(height: tokens.space2),
              KitText(
                l10n.e7SharedOpenCodeRunsOnYourComputerThisApp,
                tone: KitTextTone.secondary,
              ),
            ],
          ),
        ),
        SizedBox(height: tokens.sectionGap),
        KitRowGroup(
          children: [
            _Step(
              n: 1,
              title: l10n.e7SharedOnYourComputerRunOneCommand,
              body: l10n.e7SharedInATerminalOnTheComputerWhere,
              below: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  const Cmd(
                    SetupCommands.pair,
                    key: ValueKey('guide-pair-command'),
                  ),
                  KitText(
                    l10n.e7SharedItStartsTheServerAndPrintsA,
                    role: KitTextRole.secondary,
                  ),
                ],
              ),
            ),
            _Step(
              n: 2,
              title: canScan
                  ? l10n.e7SharedScanTheQROrPasteTheCode
                  : l10n.e7SharedPasteTheCodeInThisApp,
              body: canScan ? l10n.guideStepTwoScan : l10n.guideStepTwoPaste,
              below: Align(
                alignment: AlignmentDirectional.centerStart,
                child: KitButton.secondary(
                  key: const ValueKey('guide-add-server'),
                  label: l10n.e7SetupAddServer,
                  icon: AppIconography.add,
                  expand: false,
                  // Add server itself, the same flow as the Servers page's
                  // (P3.9), not the list to find it on.
                  onPressed: () => Navigator.of(context).pushNamed(
                    '/servers',
                    arguments: const ServersRouteRequest.add(),
                  ),
                ),
              ),
            ),
            _Step(
              n: 3,
              title: l10n.e7SharedStartTalking,
              body: l10n.e7SharedPickAProjectAndSendYourFirst,
            ),
          ],
        ),
        if (onDevice) ...[
          SizedBox(height: tokens.sectionGap),
          KitRowGroup(
            children: [
              KitRow(
                key: const ValueKey('guide-phone-path'),
                leading: KitRow.icon(context, AppIconography.phone),
                title: l10n.guidePhonePathTitle,
                titleMaxLines: 2,
                supporting: TextSpan(text: l10n.guidePhonePathBody),
                supportingMaxLines: 2,
                trailing: const KitChevron(),
                onTap: () => openPhoneSetupStart(context),
              ),
            ],
          ),
        ],
        // Not ready to connect anything yet: the offline demo (the welcome's
        // "Just show me"), still one tap away once a server is saved. The
        // welcome that embeds this guide offers it itself.
        if (!embedded) ...[
          SizedBox(height: tokens.sectionGap),
          KitRowGroup(
            children: [
              KitRow(
                key: const ValueKey('settings-try-demo'),
                leading: KitRow.icon(context, AppIconography.play),
                title: l10n.settingsTryDemo,
                supporting: TextSpan(text: l10n.demoScreenSimulated),
                supportingMaxLines: 2,
                trailing: const KitChevron(),
                onTap: () =>
                    pushKitPage<void>(context, (_) => const DemoScreen()),
              ),
            ],
          ),
        ],
        SizedBox(height: tokens.sectionGap),
        KitRowGroup(
          children: [
            KitExpandRow(
              headerKey: const ValueKey('guide-advanced'),
              leading: KitRow.icon(context, AppIconography.settingsAdvanced),
              title: l10n.e7SharedAdvanced,
              supporting: TextSpan(
                text: onDevice
                    ? l10n.e7SharedHTTPSSSHTunnelsOlderServersTermuxInternals
                    : l10n.e7SharedHTTPSSSHTunnelsOlderServers,
              ),
              supportingMaxLines: 2,
              children: [
                Padding(
                  padding: EdgeInsets.all(tokens.space4),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      _Section(
                        title: l10n.e7SharedReachAServerOverHTTPSOrA,
                        children: [
                          KitText(
                            l10n.e7SharedPairingWorksWhenTheAddressTheServer,
                          ),
                          KitNotice(
                            message: l10n.connectionHelpGuideTip,
                            icon: AppIconography.idea,
                            liveRegion: false,
                          ),
                        ],
                      ),
                      _Section(
                        title: l10n.e7SharedOlderServersWithoutPairing,
                        children: [
                          KitText(
                            l10n.e7SharedServersStartedWithOpencodeServeDoNot,
                          ),
                          const Cmd(SetupCommands.serve),
                          KitText(
                            l10n.e7SharedThenAddTheServerManuallyWithUsername,
                          ),
                        ],
                      ),
                      if (onDevice)
                        _Section(
                          key: const ValueKey('guide-termux-section'),
                          title: l10n.e7SharedOnDeviceViaTermuxAutomated,
                          children: [
                            KitText(l10n.e7SharedUseTheOnDeviceTermuxCardOn),
                            KitNotice(
                              message: l10n
                                  .e7SharedOnlyTwoTapsNeedYouPersonallyDownloading,
                              icon: AppIconography.idea,
                              liveRegion: false,
                            ),
                            KitText(l10n.e7SharedPreferManualInsideTermuxRun),
                            const Cmd(_termuxManual, script: true),
                            KitText(
                              l10n.e7SharedTheChrootSharesTheNetworkStackSo,
                            ),
                          ],
                        ),
                      _Section(
                        title: l10n.e7SharedSecurityNotes,
                        children: [
                          Bullet(
                            l10n.e7SharedAlwaysSetOPENCODESERVERPASSWORDWhenBinding,
                          ),
                          Bullet(
                            onDevice
                                ? l10n.e7SharedPasswordsAreStoredInTheAndroidKeystore
                                : platformCapabilities.platform ==
                                      TargetPlatform.iOS
                                ? l10n.iosKeychainGuide
                                : l10n.platformSecureStorageGuide,
                          ),
                          Bullet(l10n.e7SharedTheServerCanExecuteCommandsOnIts),
                        ],
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ],
        ),
      ],
    );
  }

  static const _termuxManual =
      '# plain-Termux npm installs are broken upstream\n'
      '# (npm os=android -> no opencode-android-arm64 package),\n'
      '# so we use an Ubuntu chroot:\n'
      'pkg install proot-distro\n'
      'proot-distro install ubuntu\n'
      'proot-distro login ubuntu\n'
      '  apt update && apt install -y nodejs npm\n'
      '  npm i -g @opencode/cli@2.1.1\n'
      '  opencode2 serve --hostname 127.0.0.1 --port 4096 &\n'
      'exit';

  @override
  Widget build(BuildContext context) {
    if (embedded) return _body(context);
    return KitScreen(
      topBar: KitTopBar(title: _sharedCopy(context).onboardingSetupGuide),
      width: KitScreenWidth.reading,
      body: _body(context),
    );
  }
}

/// One numbered step of the three-step story: a row with the step's glyph,
/// its title and words, and what the step hands over (a command, a button)
/// under them. The number is spoken as "Step 1 of 3".
class _Step extends StatelessWidget {
  const _Step({
    required this.n,
    required this.title,
    required this.body,
    this.below,
  });

  final int n;
  final String title;
  final String body;
  final Widget? below;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final below = this.below;
    return Semantics(
      container: true,
      label: _sharedCopy(context).e7SharedDetail307(n),
      child: KitRow(
        leading: KitRow.number(context, n),
        leadingAtTop: true,
        title: title,
        titleMaxLines: 3,
        supporting: TextSpan(text: body),
        supportingMaxLines: 8,
        below: below == null
            ? null
            : Padding(
                padding: EdgeInsets.only(
                  top: tokens.space2,
                  bottom: tokens.space2,
                ),
                child: below,
              ),
      ),
    );
  }
}

class _Section extends StatelessWidget {
  final String title;
  final List<Widget> children;
  const _Section({super.key, required this.title, required this.children});

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    return Padding(
      padding: EdgeInsets.only(bottom: tokens.space5),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Semantics(
            header: true,
            child: KitText(title, role: KitTextRole.label),
          ),
          for (final child in children)
            Padding(
              padding: EdgeInsets.only(top: tokens.space2),
              child: child,
            ),
        ],
      ),
    );
  }
}

/// A command the reader runs elsewhere, copyable in place (the kit's code
/// block: left to right, a copy button that shows its own check). [script]
/// shows several lines with comments as a shell script instead of one
/// prompt per line.
class Cmd extends StatelessWidget {
  final String text;
  final bool script;
  const Cmd(this.text, {super.key, this.script = false});

  @override
  Widget build(BuildContext context) => KitCodeBlock(
    text: text,
    kind: script ? KitCodeKind.code : KitCodeKind.command,
    language: script ? 'bash' : null,
    copyLabel: _sharedCopy(context).handoffCopyCommand,
  );
}

/// One point of a short list: an accent dot and the words.
class Bullet extends StatelessWidget {
  final String text;
  const Bullet(this.text, {super.key});

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const ExcludeSemantics(
          child: KitText('•', tone: KitTextTone.secondary),
        ),
        SizedBox(width: tokens.space2),
        Expanded(child: KitText(text)),
      ],
    );
  }
}

AppLocalizations _sharedCopy(BuildContext context) =>
    lookupAppLocalizations(Localizations.localeOf(context));
