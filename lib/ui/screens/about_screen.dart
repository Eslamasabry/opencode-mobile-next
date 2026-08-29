import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../widgets/markdown.dart';
import '../widgets/product_states.dart';

/// Upstream OpenCode asks third-party projects that use the OpenCode name to
/// say plainly that they are not the official project. This is that statement,
/// and it is shown on every tab of this screen rather than buried in a
/// document the reader has to scroll.
const String nonAffiliationDisclaimer =
    'OpenCode Mobile is an independent community project. It is not built, '
    'maintained, endorsed by, or affiliated with the official OpenCode team.';

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
                    'App information could not be loaded: ${snapshot.error}',
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
          if (showAppSummary) ...[
            const ListTile(
              contentPadding: EdgeInsets.zero,
              leading: Icon(Icons.terminal_rounded, size: 34),
              title: Text('OpenCode for Android'),
              subtitle: Text(
                'A mobile client for an OpenCode server. Voice recognition runs locally after optional model downloads.',
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
