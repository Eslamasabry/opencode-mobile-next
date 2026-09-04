part of '../chat_screen.dart';

typedef _AttachmentChooser =
    Future<PromptAttachment?> Function(List<PromptAttachment> current);

class _PromptEditorResult {
  const _PromptEditorResult({required this.value, required this.attachments});

  final TextEditingValue value;
  final List<PromptAttachment> attachments;
}

class _PromptEditorScreen extends StatefulWidget {
  const _PromptEditorScreen({
    required this.initialValue,
    required this.initialAttachments,
    required this.chooseAttachment,
  });

  final TextEditingValue initialValue;
  final List<PromptAttachment> initialAttachments;
  final _AttachmentChooser chooseAttachment;

  @override
  State<_PromptEditorScreen> createState() => _PromptEditorScreenState();
}

class _PromptEditorScreenState extends State<_PromptEditorScreen> {
  late final TextEditingController _controller;
  late final List<PromptAttachment> _attachments;
  bool _closing = false;

  @override
  void initState() {
    super.initState();
    _controller = TextEditingController.fromValue(widget.initialValue);
    _attachments = List<PromptAttachment>.from(widget.initialAttachments);
  }

  bool get _dirty =>
      _controller.text != widget.initialValue.text ||
      !_sameAttachments(_attachments, widget.initialAttachments);

  bool _sameAttachments(
    List<PromptAttachment> left,
    List<PromptAttachment> right,
  ) {
    if (left.length != right.length) return false;
    for (var index = 0; index < left.length; index += 1) {
      final a = left[index];
      final b = right[index];
      if (a.mime != b.mime || a.filename != b.filename || a.url != b.url) {
        return false;
      }
    }
    return true;
  }

  Future<void> _addAttachment() async {
    try {
      final attachment = await widget.chooseAttachment(_attachments);
      if (!mounted || attachment == null) return;
      setState(() => _attachments.add(attachment));
    } catch (error) {
      if (!mounted) return;
      showProductError(context, error);
    }
  }

  void _save() {
    Navigator.pop(
      context,
      _PromptEditorResult(
        value: _controller.value,
        attachments: List.unmodifiable(_attachments),
      ),
    );
  }

  Future<void> _cancel() async {
    if (_closing) return;
    if (!_dirty) {
      Navigator.pop(context);
      return;
    }
    _closing = true;
    final discard = await showConfirmSheet(
      context,
      icon: Icons.delete_sweep_outlined,
      title: 'Discard prompt changes?',
      message:
          'Your original composer draft and attachments will stay unchanged.',
      confirmLabel: 'Discard',
      cancelLabel: 'Keep editing',
      destructive: true,
    );
    _closing = false;
    if (discard && mounted) Navigator.pop(context);
  }

  @override
  Widget build(BuildContext context) {
    return PopScope(
      canPop: false,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop) unawaited(_cancel());
      },
      child: Scaffold(
        key: const Key('prompt-editor-screen'),
        appBar: AppBar(
          leading: IconButton(
            tooltip: 'Close prompt editor',
            onPressed: _cancel,
            icon: const Icon(Icons.close_rounded),
          ),
          title: const Text('Prompt editor'),
          actions: [
            IconButton(
              key: const Key('prompt-editor-attach'),
              tooltip: _attachments.length >= _maxAttachmentCount
                  ? 'Attachment limit reached'
                  : 'Attach file',
              onPressed: _attachments.length >= _maxAttachmentCount
                  ? null
                  : _addAttachment,
              icon: const Icon(Icons.attach_file_rounded),
            ),
            TextButton(
              key: const Key('prompt-editor-done'),
              onPressed: _save,
              child: const Text('Done'),
            ),
            const SizedBox(width: 4),
          ],
        ),
        body: SafeArea(
          top: false,
          child: Column(
            children: [
              if (_attachments.isNotEmpty)
                SizedBox(
                  height: 64,
                  child: ListView.separated(
                    key: const Key('prompt-editor-attachments'),
                    padding: const EdgeInsets.symmetric(
                      horizontal: 12,
                      vertical: 8,
                    ),
                    scrollDirection: Axis.horizontal,
                    itemCount: _attachments.length,
                    separatorBuilder: (_, _) => const SizedBox(width: 6),
                    itemBuilder: (context, index) {
                      final attachment = _attachments[index];
                      return _PendingAttachmentChip(
                        attachment: attachment,
                        onRemove: () =>
                            setState(() => _attachments.removeAt(index)),
                      );
                    },
                  ),
                ),
              Expanded(
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(12, 8, 12, 12),
                  child: TextField(
                    key: const Key('prompt-editor-field'),
                    controller: _controller,
                    autofocus: true,
                    expands: true,
                    minLines: null,
                    maxLines: null,
                    keyboardType: TextInputType.multiline,
                    textAlignVertical: TextAlignVertical.top,
                    textCapitalization: TextCapitalization.sentences,
                    decoration: const InputDecoration(
                      hintText: 'Write your OpenCode prompt…',
                      alignLabelWithHint: true,
                      contentPadding: EdgeInsets.all(16),
                    ),
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }
}
