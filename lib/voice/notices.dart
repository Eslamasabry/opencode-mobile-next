import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

const voiceNoticeAssets = <String>[
  'THIRD_PARTY_NOTICES.md',
  'LICENSES/Apache-2.0.txt',
  'LICENSES/BSD-3-Clause-record.txt',
  'LICENSES/MIT-ONNX-Runtime.txt',
  'LICENSES/MIT-OpenAI-Whisper.txt',
];

Future<String> loadVoiceNotices() async {
  final sections = <String>[];
  for (final asset in voiceNoticeAssets) {
    final text = await rootBundle.loadString(asset);
    sections.add(
      asset == voiceNoticeAssets.first
          ? text.trim()
          : '# $asset\n\n${text.trim()}',
    );
  }
  return sections.join('\n\n---\n\n');
}

class VoiceNoticesView extends StatelessWidget {
  const VoiceNoticesView({super.key});

  @override
  Widget build(BuildContext context) => FutureBuilder<String>(
    future: loadVoiceNotices(),
    builder: (context, snapshot) {
      if (snapshot.hasError) {
        return Center(child: Text('Could not load notices: ${snapshot.error}'));
      }
      if (!snapshot.hasData) {
        return const Center(child: CircularProgressIndicator());
      }
      return SelectionArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(16),
          child: Text(
            snapshot.data!,
            style: const TextStyle(fontFamily: 'AppMono', fontSize: 12),
          ),
        ),
      );
    },
  );
}

class VoiceNoticesPage extends StatelessWidget {
  const VoiceNoticesPage({super.key});

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('Voice licenses and provenance')),
    body: const VoiceNoticesView(),
  );
}

Future<void> showVoiceNotices(BuildContext context) => Navigator.of(
  context,
).push(MaterialPageRoute<void>(builder: (_) => const VoiceNoticesPage()));
