import '../voice/model_manifest.dart';

/// The one on-device assistant model: Google's Gemma 4 E4B, LiteRT-LM build
/// (Apache 2.0, not gated). Pinned to a Hugging Face commit with the size and
/// sha256 Hugging Face publishes for the file, so the voice downloader's
/// resume and verification apply unchanged.
const localAssistantPack = VoiceModelPack(
  id: 'gemma-4-e4b',
  label: 'Gemma 4 E4B',
  description: 'Summaries written on this phone, with no network.',
  repository: 'litert-community/gemma-4-E4B-it-litert-lm',
  revision: 'e6a3892a8c22cf87ff99583996149cf3cb32452b',
  // The model card measures about 3.3 GB resident on the CPU and 0.7 GB on
  // the GPU. A "6 GB" phone reports about 5.5 GB to apps, so the gate sits
  // just under it.
  minimumMemoryMb: 5400,
  files: [
    VoiceModelFile(
      name: 'gemma-4-E4B-it.litertlm',
      length: 3659530240,
      sha256:
          '0b2a8980ce155fd97673d8e820b4d29d9c7d99b8fa6806f425d969b145bd52e0',
    ),
  ],
);

/// Longest transcript handed to the model, in characters; the engine's
/// 4096-token window also holds the instruction and the reply.
const localSummaryTranscriptChars = 9000;

const localSummaryMaxTokens = 320;

/// The instruction plus the conversation, cut to fit the model's window.
/// A long conversation keeps its opening (what was asked) and its end (where
/// it stands now); the middle is the part dropped.
String buildLocalSummaryPrompt(
  String transcript, {
  int maxChars = localSummaryTranscriptChars,
}) {
  final text = transcript.trim();
  final String body;
  if (text.length <= maxChars) {
    body = text;
  } else {
    final head = (maxChars * 0.25).floor();
    final tail = maxChars - head;
    body =
        '${text.substring(0, head).trimRight()}\n[...]\n'
        '${text.substring(text.length - tail).trimLeft()}';
  }
  return 'Summarize the conversation below in at most five short bullet '
      'points: what was asked, what was done, and what is still open. Write '
      'in the same language as the conversation. Use plain text and do not '
      'add anything that is not in it.\n\n$body';
}
