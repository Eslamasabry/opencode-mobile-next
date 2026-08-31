import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../ui/app_theme.dart';
import '../ui/widgets/product_states.dart';

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

/// S11: the last full-height sheet still on a bare spinner and a raw
/// `${snapshot.error}` with no way out. It now uses the shared loading and
/// error states, so a failure explains itself and offers Try again.
class VoiceNoticesView extends StatefulWidget {
  const VoiceNoticesView({super.key});

  @override
  State<VoiceNoticesView> createState() => _VoiceNoticesViewState();
}

class _VoiceNoticesViewState extends State<VoiceNoticesView> {
  late Future<String> _notices = loadVoiceNotices();

  Future<void> _retry() async => setState(() => _notices = loadVoiceNotices());

  @override
  Widget build(BuildContext context) => FutureBuilder<String>(
    future: _notices,
    builder: (context, snapshot) {
      if (snapshot.hasError) {
        return ProductErrorState(
          message: productErrorText(snapshot.error!),
          onRetry: _retry,
        );
      }
      if (!snapshot.hasData) {
        return const LoadingList(rows: 6);
      }
      return SelectionArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(16),
          child: Text(
            snapshot.data!,
            style: const TextStyle(
              fontFamily: AppTheme.monoFamily,
              fontSize: AppTheme.codeFontSize,
            ),
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
