import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../feedback/bug_report.dart';
import '../../platform/platform_capabilities.dart';
import '../app_theme.dart';
import '../widgets/markdown.dart';
import '../widgets/product_states.dart';

/// Upstream OpenCode asks third-party projects that use the OpenCode name to
/// say plainly that they are not the official project. This is that statement,
/// and it is shown on every tab of this screen rather than buried in a
/// document the reader has to scroll.
const String nonAffiliationDisclaimer =
    'OpenCode Mobile is an independent community project. It is not built, '
    'maintained, endorsed by, or affiliated with the official OpenCode team.';

/// The alpha statement shown on every tab of About. These three claims are
/// the product's public position: AI-assisted ("vibecoded") construction,
/// alpha maturity, and desktop builds that nobody has hardware-tested yet.
/// They are asserted verbatim by test/about_alpha_notice_test.dart so the
/// copy cannot quietly walk back any of them.
const String alphaNoticeBody =
    'This app is built heavily with AI assistance and is in public alpha. '
    'Expect rough edges and untested corners — the desktop builds especially '
    'have not been hardware-tested. Report what breaks: it directly decides '
    'what gets fixed.';

class AboutScreen extends StatelessWidget {
  const AboutScreen({super.key, this.initialTab = 0});

  /// 0 opens Privacy, 1 opens Open source.
  final int initialTab;

  Future<List<String>> _loadDocuments() => Future.wait([
    rootBundle.loadString('PRIVACY.md'),
    rootBundle.loadString('THIRD_PARTY_NOTICES.md'),
  ]);

  @override
  Widget build(BuildContext context) {
    return DefaultTabController(
      length: 2,
      initialIndex: initialTab.clamp(0, 1),
      child: Scaffold(
        appBar: AppBar(
          title: const Text('About and open source notices'),
          actions: [
            IconButton(
              key: const ValueKey('about-report-bug'),
              tooltip: 'Report a bug',
              onPressed: () => unawaited(openBugReport(context)),
              icon: const Icon(Icons.bug_report_outlined),
            ),
          ],
          bottom: const TabBar(
            tabs: [
              Tab(icon: Icon(Icons.privacy_tip_outlined), text: 'Privacy'),
              Tab(icon: Icon(Icons.code_rounded), text: 'Open source'),
            ],
          ),
        ),
        body: FutureBuilder<List<String>>(
          future: _loadDocuments(),
          builder: (context, snapshot) {
            if (snapshot.connectionState != ConnectionState.done) {
              return const LoadingList(rows: 6);
            }
            if (snapshot.hasError) {
              return Center(
                child: Padding(
                  padding: const EdgeInsets.all(24),
                  child: Text(
                    'App information could not be loaded: '
                    '${snapshot.error}',
                    textAlign: TextAlign.center,
                  ),
                ),
              );
            }
            final documents = snapshot.data!;
            return TabBarView(
              children: [
                _DocumentView(data: documents[0]),
                _DocumentView(data: documents[1], showAppSummary: true),
              ],
            );
          },
        ),
      ),
    );
  }
}

class _AlphaNotice extends StatelessWidget {
  const _AlphaNotice();

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Card(
      margin: const EdgeInsets.fromLTRB(16, 12, 16, 0),
      clipBehavior: Clip.antiAlias,
      child: Padding(
        padding: const EdgeInsets.fromLTRB(16, 14, 16, 14),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Icon(
                  Icons.science_outlined,
                  size: 18,
                  color: AppTheme.successOf(theme),
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: Text('Alpha · vibecoded', style: theme.textTheme.titleSmall),
                ),
              ],
            ),
            const SizedBox(height: 6),
            Text(
              alphaNoticeBody,
              style: theme.textTheme.bodySmall?.copyWith(
                color: AppTheme.mutedOf(theme),
              ),
            ),
            const SizedBox(height: 4),
            Align(
              alignment: Alignment.centerRight,
              child: TextButton.icon(
                key: const ValueKey('about-alpha-report-bug'),
                onPressed: () => unawaited(openBugReport(context)),
                icon: const Icon(Icons.bug_report_outlined, size: 18),
                label: const Text('Report a bug'),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _DocumentView extends StatelessWidget {
  const _DocumentView({required this.data, this.showAppSummary = false});

  final String data;
  final bool showAppSummary;

  @override
  Widget build(BuildContext context) {
    return SelectionArea(
      child: ListView(
        padding: const EdgeInsets.fromLTRB(20, 12, 20, 32),
        children: [
          const _NonAffiliationNotice(),
          const SizedBox(height: 16),
          // Scrolls with the document rather than sitting as fixed chrome:
          // at 2x text a fixed notice would squeeze (or overflow) the very
          // content the reader came for.
          const _AlphaNotice(),
          const SizedBox(height: 16),
          if (showAppSummary) ...[
            ListTile(
              contentPadding: EdgeInsets.zero,
              leading: const Icon(Icons.terminal_rounded, size: 34),
              // The desktop bundle is the same app, but naming it "for
              // Android" and promising local voice recognition describes a
              // build the reader is not running.
              title: Text(
                platformCapabilities.supportsVoice
                    ? 'OpenCode for Android'
                    : 'OpenCode for desktop',
              ),
              subtitle: Text(
                platformCapabilities.supportsVoice
                    ? 'A mobile client for an OpenCode server. Voice '
                          'recognition runs locally after optional model '
                          'downloads.'
                    : 'A desktop client for an OpenCode server.',
              ),
            ),
            const Divider(height: 28),
          ],
          MarkdownText(data),
        ],
      ),
    );
  }
}

class _NonAffiliationNotice extends StatelessWidget {
  const _NonAffiliationNotice();

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Container(
      key: const Key('about-non-affiliation'),
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: theme.colorScheme.surfaceContainerHighest,
        borderRadius: BorderRadius.circular(10),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(
            Icons.info_outline_rounded,
            size: 20,
            color: theme.colorScheme.onSurfaceVariant,
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Text(
              nonAffiliationDisclaimer,
              style: theme.textTheme.bodySmall?.copyWith(
                color: theme.colorScheme.onSurfaceVariant,
              ),
            ),
          ),
        ],
      ),
    );
  }
}
