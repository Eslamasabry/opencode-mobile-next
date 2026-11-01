import 'package:flutter/material.dart';

import '../../../l10n/app_localizations.dart';
import '../../kit/kit.dart';

/// Help › How to reach your computer: Tailscale first, an SSH tunnel second,
/// plain steps, and the one safety rule. It never suggests a public tunnel.
class ReachComputerScreen extends StatelessWidget {
  const ReachComputerScreen({super.key});

  Widget _steps(BuildContext context, List<String> steps) {
    final tokens = KitTokens.of(context);
    return Padding(
      padding: EdgeInsetsDirectional.symmetric(horizontal: tokens.gutter),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          for (var i = 0; i < steps.length; i++)
            Padding(
              padding: EdgeInsetsDirectional.only(bottom: tokens.space3),
              child: KitText('${i + 1}. ${steps[i]}', role: KitTextRole.body),
            ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final tokens = KitTokens.of(context);
    return KitScreen(
      topBar: KitTopBar(title: l10n.reachTitle),
      width: KitScreenWidth.reading,
      body: Builder(
        builder: (context) => ListView(
          key: const ValueKey('reach-computer-page'),
          padding: EdgeInsetsDirectional.only(
            top: tokens.space2,
            bottom: KitScreen.endPadding(context),
          ),
          children: [
            Padding(
              padding: EdgeInsetsDirectional.symmetric(
                horizontal: tokens.gutter,
              ),
              child: KitText(l10n.reachIntro, role: KitTextRole.secondary),
            ),
            KitSectionLabel(l10n.reachTailscaleHeading),
            _steps(context, [
              l10n.reachTailscale1,
              l10n.reachTailscale2,
              l10n.reachTailscale3,
              l10n.reachTailscale4,
            ]),
            KitSectionLabel(l10n.reachSshHeading),
            _steps(context, [
              l10n.reachSsh1,
              l10n.reachSsh2,
              l10n.reachSsh3,
              l10n.reachSsh4,
            ]),
            Padding(
              padding: EdgeInsetsDirectional.symmetric(
                horizontal: tokens.gutter,
              ),
              child: KitCodeBlock(
                key: const ValueKey('reach-ssh-command'),
                text: l10n.reachSshCommand,
                kind: KitCodeKind.command,
              ),
            ),
            KitSectionLabel(l10n.reachSafetyHeading),
            Padding(
              padding: EdgeInsetsDirectional.symmetric(
                horizontal: tokens.gutter,
              ),
              child: KitText(l10n.reachSafety, role: KitTextRole.body),
            ),
          ],
        ),
      ),
    );
  }
}
