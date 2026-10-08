import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:package_info_plus/package_info_plus.dart';

import '../../l10n/app_localizations.dart';
import '../../platform/platform_capabilities.dart';
import '../../state/connection.dart';
import '../../update/shorebird_update_notice.dart';
import '../../voice/notices.dart';
import '../app_theme.dart';
import '../desktop/desktop_interaction.dart';
import '../desktop/shortcuts.dart';
import '../kit/kit.dart';

/// Upstream OpenCode asks third-party projects that use the OpenCode name to
/// say plainly that they are not the official project. This is that statement,
/// and it is shown once near the top of this screen rather than buried in a
/// document the reader has to scroll.
const String nonAffiliationDisclaimer =
    'OpenCode Mobile is an independent community project. It is not built, '
    'maintained, endorsed by, or affiliated with the official OpenCode team.';

/// Build provenance is independent of the Android release channel. Desktop
/// readiness is still disclosed until those builds have hardware evidence.
const String buildProvenanceBody =
    'This independent app is built heavily with AI assistance. '
    'Android is the primary supported platform. Desktop builds are '
    'experimental and have not been hardware-tested. '
    'Report what breaks to help improve the app.';

/// Settings › About (target-ia §1.3 row 22): which build this is, what it
/// is not, the one-time tips and the keyboard shortcuts, and the open
/// source notices. The privacy policy moved to Settings › Privacy and data
/// (P3.10), so there are no tabs.
///
/// Built from kit parts only (screen-system-1). One scroll: the build's
/// identity once, with its version copyable in one tap and an update check
/// on builds that can update themselves; the package id and signer folded
/// under Details; the non-affiliation statement; the build notice; Show
/// tips again (with a [controller]) and, on a desktop, the keyboard
/// shortcuts; then Open source: every bundled package licence in the
/// [KitViewer], the voice models' licences on Android, and the notices,
/// reflowed by [KitMarkdown].
class AboutScreen extends StatefulWidget {
  const AboutScreen({super.key, this.controller, this.updateService});

  /// Where Show tips again puts the one-time tips back. Null (the named
  /// route) leaves that row out.
  final ConnectionController? controller;

  /// Checks for and fetches an update. Null: on Android the app's own
  /// updater (made on the first check), elsewhere no update row.
  final AppUpdateService? updateService;

  @override
  State<AboutScreen> createState() => _AboutScreenState();
}

/// What the last update check found.
enum _UpdateCheck {
  idle,
  checking,
  current,
  downloading,
  ready,
  cannot,
  failed,
}

class _AboutScreenState extends State<AboutScreen> {
  static const _platform = MethodChannel('oc/termux');

  Future<String>? _notices;
  late final Future<_BuildData> _build = _loadBuild();
  _UpdateCheck _update = _UpdateCheck.idle;
  AppUpdateService? _service;

  /// Show tips again ran: the row says so in place of a snackbar (KIT-34).
  bool _tipsReset = false;

  bool get _canCheckUpdates =>
      widget.updateService != null || platformCapabilities.isAndroid;

  @override
  void initState() {
    super.initState();
    _notices = rootBundle
        .loadString('THIRD_PARTY_NOTICES.md')
        .then((text) => reflowMarkdown(aboutNoticesForReaders(text)));
  }

  /// Puts every one-time tip back. The row itself then says so: there is
  /// no way to take the reset back, so no Undo bar (KIT-34).
  Future<void> _showTipsAgain(ConnectionController controller) async {
    await controller.nudges.reset();
    if (mounted) setState(() => _tipsReset = true);
  }

  Future<_BuildData> _loadBuild() async {
    PackageInfo? package;
    try {
      package = await PackageInfo.fromPlatform();
    } catch (_) {
      return const _BuildData(package: null, signer: null);
    }
    String? signer;
    if (platformCapabilities.supportsTermux) {
      try {
        signer = await _platform.invokeMethod<String>(
          'getSigningCertificateSha256',
        );
      } catch (_) {
        // Desktop and older Android builds do not expose a signer.
      }
    }
    return _BuildData(package: package, signer: signer);
  }

  Future<void> _checkForUpdate() async {
    if (_update == _UpdateCheck.checking ||
        _update == _UpdateCheck.downloading) {
      return;
    }
    setState(() => _update = _UpdateCheck.checking);
    try {
      final service = _service ??=
          widget.updateService ?? ShorebirdAppUpdateService();
      if (!service.isAvailable) {
        if (mounted) setState(() => _update = _UpdateCheck.cannot);
        return;
      }
      final state = await service.checkForUpdate();
      if (!mounted) return;
      switch (state) {
        case AppUpdateState.current:
          setState(() => _update = _UpdateCheck.current);
        case AppUpdateState.restartRequired:
          setState(() => _update = _UpdateCheck.ready);
        case AppUpdateState.unavailable:
          setState(() => _update = _UpdateCheck.cannot);
        case AppUpdateState.available:
          setState(() => _update = _UpdateCheck.downloading);
          await service.downloadUpdate();
          if (mounted) setState(() => _update = _UpdateCheck.ready);
      }
    } catch (_) {
      if (mounted) setState(() => _update = _UpdateCheck.failed);
    }
  }

  String _updateLine(AppLocalizations l10n) => switch (_update) {
    _UpdateCheck.idle => l10n.aboutUpdateIdle,
    _UpdateCheck.checking => l10n.aboutUpdateChecking,
    _UpdateCheck.current => l10n.aboutUpdateCurrent,
    _UpdateCheck.downloading => l10n.aboutUpdateDownloading,
    _UpdateCheck.ready => l10n.aboutUpdateReady,
    _UpdateCheck.cannot => l10n.aboutUpdateCannot,
    _UpdateCheck.failed => l10n.aboutUpdateFailed,
  };

  Future<String> _licenceText() async {
    final out = StringBuffer();
    await for (final entry in LicenseRegistry.licenses) {
      out
        ..writeln(entry.packages.join(', '))
        ..writeln();
      for (final paragraph in entry.paragraphs) {
        out
          ..writeln(paragraph.text)
          ..writeln();
      }
      out.writeln();
    }
    return out.toString();
  }

  void _openLicences(AppLocalizations l10n) => unawaited(
    showKitViewer(
      context,
      name: l10n.aboutAllLicences,
      interactive: false,
      viewerKey: const ValueKey('about-licences-viewer'),
      source: KitViewerSource.load(
        () async => KitViewerContent.text(await _licenceText()),
      ),
    ),
  );

  void _openNotices(AppLocalizations l10n) => unawaited(
    showKitViewer(
      context,
      name: l10n.aboutBundledComponents,
      interactive: false,
      viewerKey: const ValueKey('about-notices-document'),
      source: KitViewerSource.load(
        () async => KitViewerContent.markdown(await _notices!),
      ),
    ),
  );

  /// The platform line under the product name. The desktop bundle is the
  /// same app, but promising local voice recognition would describe a
  /// build the reader is not running.
  String _summary(AppLocalizations l10n) => platformCapabilities.supportsVoice
      ? l10n.e7SettingsDetailUi22
      : platformCapabilities.platform == TargetPlatform.iOS
      ? l10n.iosRemoteSummary
      : l10n.e7SettingsDetailUi23;

  @override
  Widget build(BuildContext context) {
    final l10n = _screenCopy(context);
    final tokens = KitTokens.of(context);
    Widget rails(Widget child) => Padding(
      padding: EdgeInsetsDirectional.symmetric(horizontal: tokens.gutter),
      child: child,
    );
    return KitScreen(
      topBar: KitTopBar(title: l10n.aboutTitle),
      width: KitScreenWidth.reading,
      body: Builder(
        builder: (context) {
          final controller = widget.controller;
          final shortcuts = desktopInteractions;
          return ListView(
            key: const ValueKey('about-page'),
            padding: EdgeInsetsDirectional.only(
              top: tokens.space4,
              bottom: KitScreen.endPadding(context),
            ),
            children: [
              FutureBuilder<_BuildData>(
                future: _build,
                builder: (context, snapshot) =>
                    _identity(context, l10n, snapshot.data),
              ),
              SizedBox(height: tokens.space4),
              rails(
                KitNotice(
                  key: const Key('about-non-affiliation'),
                  message: l10n.e7SettingsNonAffiliation,
                  liveRegion: false,
                ),
              ),
              SizedBox(height: tokens.space3),
              // Reporting a problem lives in Settings (one entry point).
              rails(
                KitNotice(
                  icon: AppIconography.experiments,
                  title: l10n.e7SettingsDetailUi19,
                  message: l10n.e7SettingsAlphaBody,
                  liveRegion: false,
                ),
              ),
              if (controller != null || shortcuts) ...[
                SizedBox(height: tokens.sectionGap),
                KitRowGroup(
                  key: const ValueKey('about-help'),
                  label: l10n.aboutHelpSection,
                  children: [
                    // Each one-time tip fires once, at its moment. This puts
                    // them all back, for a person who dismissed one too fast.
                    if (controller != null)
                      KitArrival(
                        id: 'settings-show-tips-again',
                        child: KitRow(
                          key: const ValueKey('settings-show-tips-again'),
                          leading: KitRow.icon(context, AppIconography.idea),
                          title: l10n.discoverShowTipsAgain,
                          supporting: TextSpan(
                            text: _tipsReset
                                ? l10n.discoverShowTipsDone
                                : l10n.discoverShowTipsSubtitle,
                          ),
                          supportingMaxLines: 2,
                          onTap: () => unawaited(_showTipsAgain(controller)),
                        ),
                      ),
                    // The shortcut layer must be discoverable without
                    // already knowing a shortcut, and means nothing without
                    // a keyboard.
                    if (shortcuts)
                      KitRow(
                        key: const ValueKey('library-keyboard-shortcuts'),
                        leading: KitRow.icon(context, AppIconography.keyboard),
                        title: l10n.e7LibraryKeyboardShortcuts,
                        trailing: const KitChevron(),
                        onTap: () => unawaited(showShortcutsHelp(context)),
                      ),
                  ],
                ),
              ],
              SizedBox(height: tokens.sectionGap),
              KitRowGroup(
                key: const ValueKey('about-open-source'),
                label: l10n.e7SettingsDetailUi18,
                children: [
                  KitRow(
                    key: const ValueKey('about-all-licences'),
                    leading: KitRow.icon(context, AppIconography.article),
                    title: l10n.aboutAllLicences,
                    supporting: TextSpan(text: l10n.aboutAllLicencesDetail),
                    supportingMaxLines: 2,
                    trailing: const KitChevron(),
                    onTap: () => _openLicences(l10n),
                  ),
                  // The notices for what ships inside the app, on their own
                  // page: About stays short (owner review, build 2061).
                  KitRow(
                    key: const ValueKey('about-bundled-components'),
                    leading: KitRow.icon(context, AppIconography.info),
                    title: l10n.aboutBundledComponents,
                    supporting: TextSpan(
                      text: l10n.aboutBundledComponentsDetail,
                    ),
                    supportingMaxLines: 2,
                    trailing: const KitChevron(),
                    onTap: () => _openNotices(l10n),
                  ),
                ],
              ),
              // The voice models' licences, where they can run: part of Open
              // source, listed here instead of on a page of their own.
              if (platformCapabilities.supportsVoice) ...[
                SizedBox(height: tokens.sectionGap),
                KitArrival(
                  id: 'settings-voice-notices',
                  child: const VoiceNoticesView(),
                ),
              ],
            ],
          );
        },
      ),
    );
  }

  /// The build, once: product and version (copyable), the platform line,
  /// the update check, and the package id and signer under Details.
  Widget _identity(BuildContext context, AppLocalizations l10n, _BuildData? d) {
    final tokens = KitTokens.of(context);
    final package = d?.package;
    final signer = d?.signer;
    final version = package == null
        ? l10n.appTitle
        : l10n.aboutBuildVersion(package.version, package.buildNumber);
    final checking =
        _update == _UpdateCheck.checking || _update == _UpdateCheck.downloading;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        KitRowGroup(
          children: [
            KitRow(
              key: const ValueKey('about-version'),
              leading: KitRow.icon(context, AppIconography.phone),
              title: version,
              titleMaxLines: 2,
              supporting: TextSpan(text: _summary(l10n)),
              supportingMaxLines: 3,
              trailing: package == null
                  ? null
                  : KitIconButton.copy(
                      key: const ValueKey('about-copy-version'),
                      tooltip: l10n.aboutCopyVersion,
                      text: () => version,
                    ),
            ),
            if (_canCheckUpdates)
              KitRow(
                key: const ValueKey('about-check-updates'),
                leading: KitRow.icon(context, AppIconography.systemDownload),
                title: l10n.aboutCheckUpdates,
                supporting: TextSpan(text: _updateLine(l10n)),
                supportingMaxLines: 2,
                enabled: !checking,
                disabledReason: checking ? _updateLine(l10n) : null,
                onTap: () => unawaited(_checkForUpdate()),
              ),
          ],
        ),
        if (package != null)
          Padding(
            padding: EdgeInsetsDirectional.only(
              start: tokens.gutter,
              end: tokens.gutter,
              top: tokens.space2,
            ),
            child: KitDetailsFold(
              foldKey: const ValueKey('about-details'),
              values: [
                KitTechnicalValue(l10n.aboutPackageId, package.packageName),
                if (signer != null && signer.isNotEmpty)
                  KitTechnicalValue(
                    l10n.aboutSigningCertificate,
                    signer,
                    key: const ValueKey('about-signing-certificate'),
                  ),
              ],
            ),
          ),
      ],
    );
  }
}

/// Joins the hard-wrapped lines of a paragraph or list item into one line,
/// so the documents reflow to the window instead of breaking mid-sentence
/// (map: about-privacy-tab, about-open-source-tab). Headings, list starts,
/// quotes, tables, blank lines, code fences and a line ending in two spaces
/// (a deliberate break) are kept as they are.
@visibleForTesting
String reflowMarkdown(String source) {
  final block = RegExp(r'^\s*(#|[-*+]\s|\d+[.)]\s|>|\||```|~~~|<)');
  final out = <String>[];
  var fenced = false;
  var joinable = false;
  for (final line in source.split('\n')) {
    final trimmed = line.trimLeft();
    if (trimmed.startsWith('```') || trimmed.startsWith('~~~')) {
      fenced = !fenced;
      out.add(line);
      joinable = false;
      continue;
    }
    if (fenced || trimmed.isEmpty) {
      out.add(line);
      joinable = false;
      continue;
    }
    final starts = block.hasMatch(line);
    if (joinable && !starts) {
      out[out.length - 1] = '${out.last.trimRight()} $trimmed';
    } else {
      out.add(line);
    }
    final heading = trimmed.startsWith('#');
    final table = trimmed.startsWith('|');
    joinable = !heading && !table && !line.endsWith('  ');
  }
  return out.join('\n');
}

class _BuildData {
  const _BuildData({required this.package, required this.signer});

  final PackageInfo? package;
  final String? signer;
}

AppLocalizations _screenCopy(BuildContext context) =>
    Localizations.of<AppLocalizations>(context, AppLocalizations) ??
    lookupAppLocalizations(const Locale('en'));

/// The notices as a reader needs them: the bundled components, the package
/// table and the data-flow notes. The file's intro (the non-affiliation
/// line, which the page already shows, and how the file is verified) and
/// its regeneration steps are for maintainers, so they are left out.
@visibleForTesting
String aboutNoticesForReaders(String markdown) {
  final lines = markdown.split('\n');
  final start = lines.indexWhere((line) => line.startsWith('## '));
  if (start < 0) return markdown;
  final end = lines.indexWhere(
    (line) => line.startsWith('## Regenerating'),
    start,
  );
  return lines.sublist(start, end < 0 ? lines.length : end).join('\n').trim();
}

/// The privacy policy in the viewer, in the reader's language where it is
/// translated. Settings › Privacy and data opens it (About's old Privacy
/// tab, merged there by P3.10).
Future<void> showPrivacyPolicy(BuildContext context) {
  final l10n = _screenCopy(context);
  final language = Localizations.localeOf(context).languageCode;
  return showKitViewer(
    context,
    name: l10n.privacyPolicyTitle,
    interactive: false,
    viewerKey: const ValueKey('privacy-policy-viewer'),
    source: KitViewerSource.load(() async {
      final text = await rootBundle.loadString(
        language == 'ar' ? 'assets/l10n/PRIVACY.ar.md' : 'PRIVACY.md',
      );
      return KitViewerContent.markdown(reflowMarkdown(text));
    }),
  );
}
