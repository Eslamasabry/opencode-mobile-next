import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../l10n/app_localizations.dart';
import '../ui/app_iconography.dart';
import '../ui/kit/kit.dart';
import '../ui/widgets/external_link.dart';
import 'presentation.dart';

/// Every file the full voice notice is built from. The in-app page shows the
/// components and their licenses; the whole text (build provenance, lockfile
/// versions) stays in the repository for maintainers.
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

/// One open-source part voice input is built on: who made it, under which
/// license, and where its project lives. Names, makers and license names
/// are proper names and stay untranslated.
@immutable
class VoiceNoticeComponent {
  const VoiceNoticeComponent({
    required this.id,
    required this.name,
    required this.maker,
    required this.license,
    required this.licenseAsset,
    required this.website,
  });

  final String id;

  /// Null: the name is copy ([AppLocalizations.voiceNoticesWhisper]).
  final String? name;
  final String maker;
  final String license;
  final String licenseAsset;
  final String website;

  String nameIn(AppLocalizations strings) =>
      name ?? strings.voiceNoticesWhisper;
}

/// The parts voice input ships or downloads (THIRD_PARTY_NOTICES.md), most
/// visible first: the models the person downloads, then the runtime that
/// decodes them, then the recorder.
const voiceNoticeComponents = <VoiceNoticeComponent>[
  VoiceNoticeComponent(
    id: 'whisper',
    name: null,
    maker: 'OpenAI',
    license: 'MIT License',
    licenseAsset: 'LICENSES/MIT-OpenAI-Whisper.txt',
    website: 'https://github.com/openai/whisper',
  ),
  VoiceNoticeComponent(
    id: 'sherpa-onnx',
    name: 'sherpa-onnx',
    maker: 'k2-fsa',
    license: 'Apache License 2.0',
    licenseAsset: 'LICENSES/Apache-2.0.txt',
    website: 'https://github.com/k2-fsa/sherpa-onnx',
  ),
  VoiceNoticeComponent(
    id: 'onnx-runtime',
    name: 'ONNX Runtime',
    maker: 'Microsoft',
    license: 'MIT License',
    licenseAsset: 'LICENSES/MIT-ONNX-Runtime.txt',
    website: 'https://github.com/microsoft/onnxruntime',
  ),
  VoiceNoticeComponent(
    id: 'record',
    name: 'record',
    maker: 'llfbandit',
    license: 'BSD 3-Clause License',
    licenseAsset: 'LICENSES/BSD-3-Clause-record.txt',
    website: 'https://github.com/llfbandit/record',
  ),
];

/// A license file wrapped at 80 columns, rejoined into paragraphs so it
/// wraps to the window instead of breaking mid-sentence. Blank lines stay
/// paragraph breaks, and an indented or numbered line starts a new line.
String reflowLicenseText(String text) {
  final newLine = RegExp(r'^\s+|^[-*]\s|^\(?[0-9a-z]{1,3}[.)]\s');
  return text
      .trim()
      .split(RegExp(r'\n[ \t]*\n'))
      .map((paragraph) {
        final out = StringBuffer();
        for (final line in paragraph.split('\n')) {
          if (out.isEmpty) {
            out.write(line.trimRight());
          } else if (newLine.hasMatch(line)) {
            out
              ..write('\n')
              ..write(line.trimRight());
          } else {
            out
              ..write(' ')
              ..write(line.trim());
          }
        }
        return out.toString();
      })
      .join('\n\n');
}

/// Opens one component's license in the kit viewer: the text loads there
/// (with its own loading and Try again), Copy is in its menu, and the one
/// labelled action opens the project's website through [openExternalLink].
Future<void> showVoiceNoticeLicense(
  BuildContext context,
  VoiceNoticeComponent component,
) {
  final strings = voiceStrings(context);
  final name = component.nameIn(strings);
  return showKitViewer(
    context,
    name: name,
    path: component.license,
    viewerKey: ValueKey('voice-notice-viewer-${component.id}'),
    source: KitViewerSource.load(
      () async => KitViewerContent.text(
        reflowLicenseText(await rootBundle.loadString(component.licenseAsset)),
      ),
    ),
    primary: KitAction(
      key: ValueKey('voice-notice-website-${component.id}'),
      label: strings.voiceNoticesOpenWebsite(name),
      icon: AppIconography.externalLink,
      onPressed: () => openExternalLink(context, component.website),
    ),
  );
}

/// The voice licenses group of Settings › About (the Voice licenses page it
/// used to be is gone, FG6): one row per open-source part with its maker and
/// license, under a "Voice licenses" label, and one line saying what the rows
/// are. A row opens the license text; the group itself loads nothing, so it
/// has no loading or error state of its own.
class VoiceNoticesView extends StatelessWidget {
  const VoiceNoticesView({super.key});

  @override
  Widget build(BuildContext context) {
    final strings = voiceStrings(context);
    final tokens = KitTokens.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        KitRowGroup(
          key: const ValueKey('about-voice-licences'),
          label: strings.voiceNoticesTitle,
          leadingIcons: false,
          children: [
            for (final component in voiceNoticeComponents)
              KitRow(
                key: ValueKey('voice-notice-${component.id}'),
                title: component.nameIn(strings),
                supporting: TextSpan(
                  text: strings.voiceNoticesMadeBy(
                    component.maker,
                    component.license,
                  ),
                ),
                supportingMaxLines: 2,
                trailing: const KitChevron(),
                onTap: () => showVoiceNoticeLicense(context, component),
              ),
          ],
        ),
        Padding(
          padding: EdgeInsetsDirectional.only(
            start: tokens.gutter + tokens.space1,
            end: tokens.gutter + tokens.space1,
            top: tokens.labelGap,
          ),
          child: KitText(
            strings.voiceNoticesIntro,
            role: KitTextRole.caption,
            tone: KitTextTone.secondary,
          ),
        ),
      ],
    );
  }
}
