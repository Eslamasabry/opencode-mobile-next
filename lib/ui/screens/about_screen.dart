import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../widgets/markdown.dart';

class AboutScreen extends StatelessWidget {
  const AboutScreen({super.key});

  Future<String> _loadNotices() =>
      rootBundle.loadString('THIRD_PARTY_NOTICES.md');

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('About and open source notices')),
      body: FutureBuilder<String>(
        future: _loadNotices(),
        builder: (context, snapshot) {
          if (snapshot.connectionState != ConnectionState.done) {
            return const Center(child: CircularProgressIndicator());
          }
          if (snapshot.hasError) {
            return Center(
              child: Padding(
                padding: const EdgeInsets.all(24),
                child: Text(
                  'Open source notices could not be loaded: ${snapshot.error}',
                  textAlign: TextAlign.center,
                ),
              ),
            );
          }
          return SelectionArea(
            child: ListView(
              padding: const EdgeInsets.fromLTRB(20, 12, 20, 32),
              children: [
                const ListTile(
                  contentPadding: EdgeInsets.zero,
                  leading: Icon(Icons.terminal_rounded, size: 34),
                  title: Text('OpenCode for Android'),
                  subtitle: Text(
                    'A mobile client for an OpenCode server. Voice recognition runs locally after optional model downloads.',
                  ),
                ),
                const Divider(height: 28),
                MarkdownText(snapshot.data!),
              ],
            ),
          );
        },
      ),
    );
  }
}
