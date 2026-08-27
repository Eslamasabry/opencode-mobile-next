import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../widgets/markdown.dart';

class AboutScreen extends StatelessWidget {
  const AboutScreen({super.key});

  Future<List<String>> _loadDocuments() => Future.wait([
    rootBundle.loadString('PRIVACY.md'),
    rootBundle.loadString('THIRD_PARTY_NOTICES.md'),
  ]);

  @override
  Widget build(BuildContext context) {
    return DefaultTabController(
      length: 2,
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
              return const Center(child: CircularProgressIndicator());
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
