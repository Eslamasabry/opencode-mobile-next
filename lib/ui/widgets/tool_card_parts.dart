part of 'tool_card.dart';

/// An edit as a [KitDiffView] capped at 12 lines; "Open all changes" opens
/// the read-only diff page.
class _Diff extends StatelessWidget {
  const _Diff({required this.files});

  final List<KitDiffFile> files;

  @override
  Widget build(BuildContext context) => KitDiffView(
    files: files,
    maxLines: 12,
    keyPrefix: 'tool-diff',
    onOpenAll: () => showKitDiff(
      context,
      title: _chatL10n(context).toolCardChangesIn(
        files.length == 1
            ? _fileName(files.first.path)
            : _chatL10n(context).chatUiFileCount(files.length),
      ),
      files: files,
    ),
  );
}

/// One question the agent asked and the answer it got, in words.
class _QuestionAnswer extends StatelessWidget {
  const _QuestionAnswer({required this.question, this.answer});

  final String question;
  final dynamic answer;

  @override
  Widget build(BuildContext context) {
    final l10n = _chatL10n(context);
    final joined = answer is List
        ? (answer as List).map((item) => '$item').join(', ')
        : _valueString(answer);
    final answerText = joined == null || joined.trim().isEmpty ? null : joined;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        KitText(
          question,
          role: KitTextRole.secondary,
          tone: KitTextTone.primary,
        ),
        KitText(
          answerText == null
              ? l10n.chatUiNoAnswer
              : l10n.chatUiAnsweredDetail(answerText),
          role: KitTextRole.secondary,
          tone: answerText == null
              ? KitTextTone.tertiary
              : KitTextTone.secondary,
        ),
      ],
    );
  }
}

/// A produced image: loading as a working row, a failure in words with
/// "Load (name) again", else the picture (decoded at its laid-out size by
/// KitImage), which opens the reader with Attach and Download.
class _ImagePreview extends StatelessWidget {
  const _ImagePreview({
    required this.file,
    required this.load,
    required this.onRetry,
    this.onAttach,
    this.onDownload,
  });

  final ToolOutputFile file;
  final Future<FilePreviewData> load;
  final VoidCallback onRetry;
  final ToolOutputFileAction? onAttach;
  final ToolOutputFileAction? onDownload;

  @override
  Widget build(BuildContext context) {
    final l10n = _chatL10n(context);
    return FutureBuilder<FilePreviewData>(
      future: load,
      builder: (context, snapshot) {
        if (snapshot.connectionState != ConnectionState.done) {
          return KitRow(
            key: const Key('tool-output-image-loading'),
            leading: const KitStatusMark(state: KitMarkState.working),
            title: l10n.chatUiLoadingFile(file.displayName),
            padding: EdgeInsets.zero,
          );
        }
        final data = snapshot.data;
        final failure = snapshot.error;
        final error = failure != null
            ? productErrorText(failure, l10n: l10n)
            : data?.error;
        if (error != null || data?.bytes?.isNotEmpty != true) {
          return KitNotice(
            key: const Key('tool-output-image-error'),
            title: file.displayName,
            message: error ?? l10n.chatUiImageDataIsUnavailable,
            icon: AppIconography.imageBroken,
            liveRegion: false,
            actions: [
              KitAction(
                label: l10n.toolCardLoadImageAgain(file.displayName),
                icon: AppIconography.retry,
                onPressed: onRetry,
              ),
            ],
          );
        }
        final previewData = data!;
        return KitTappable(
          label: l10n.chatUiPreviewGeneratedImage(file.displayName),
          shape: KitShape.code,
          onTap: () => showFilePreviewSheet(
            context,
            previewData,
            onAttach: onAttach == null
                ? null
                : () => onAttach!(file, previewData),
            onDownload: onDownload == null
                ? null
                : () => onDownload!(file, previewData),
          ),
          child: AspectRatio(
            aspectRatio: 16 / 9,
            child: KitImage(
              imageKey: const Key('tool-output-image'),
              source: KitImageSource.memory(previewData.bytes!),
              semanticsLabel: null,
              shape: KitShape.code,
            ),
          ),
        );
      },
    );
  }
}

/// A produced file as a row: its name and type; a tap loads it (a working
/// mark while it does) and opens the reader with Attach and Download.
class _FileRow extends StatefulWidget {
  const _FileRow({
    required this.file,
    required this.load,
    this.onAttach,
    this.onDownload,
  });

  final ToolOutputFile file;
  final Future<FilePreviewData> Function() load;
  final ToolOutputFileAction? onAttach;
  final ToolOutputFileAction? onDownload;

  @override
  State<_FileRow> createState() => _FileRowState();
}

class _FileRowState extends State<_FileRow> {
  bool _opening = false;

  Future<void> _open() async {
    if (_opening) return;
    setState(() => _opening = true);
    try {
      final data = await widget.load();
      if (!mounted) return;
      setState(() => _opening = false);
      await showFilePreviewSheet(
        context,
        data,
        onAttach: widget.onAttach == null
            ? null
            : () => widget.onAttach!(widget.file, data),
        onDownload: widget.onDownload == null
            ? null
            : () => widget.onDownload!(widget.file, data),
      );
    } finally {
      if (mounted && _opening) setState(() => _opening = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = _chatL10n(context);
    return KitRow(
      key: const Key('tool-output-file'),
      leading: const KitRowIcon(AppIconography.file),
      title: widget.file.displayName,
      supporting: TextSpan(
        text: widget.file.mimeType ?? l10n.chatUiGeneratedFile,
      ),
      trailing: _opening
          ? const KitStatusMark(state: KitMarkState.working)
          : const KitChevron(),
      onTap: _opening ? null : _open,
      padding: EdgeInsets.zero,
    );
  }
}
