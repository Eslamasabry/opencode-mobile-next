part of '../chat_screen.dart';

// The composer's "+" tools list, attachment thumbnails, and returning a
// withdrawn prompt to the draft.

/// Decoded image previews, once per attachment rather than per keystroke.
final _thumbnailCache = Expando<Uint8List>('composer-thumbnail');

final _thumbnailTried = Expando<bool>('composer-thumbnail-tried');

Uint8List? _thumbnailBytes(PromptAttachment attachment) {
  if (!attachment.mime.startsWith('image/') ||
      !attachment.url.startsWith('data:')) {
    return null;
  }
  if (_thumbnailTried[attachment] == true) return _thumbnailCache[attachment];
  _thumbnailTried[attachment] = true;
  try {
    final bytes = Uri.parse(attachment.url).data?.contentAsBytes();
    if (bytes != null) _thumbnailCache[attachment] = bytes;
    return bytes;
  } on FormatException {
    return null;
  }
}

/// The "+" sheet's rows, most used first (map prompt-tools-sheet): attach,
/// photos, camera and voice; the door to commands; then the prompt shelf
/// and the rarer tools folded under their own rows. A tool this server or
/// this moment cannot run stays in its place and says why (STATE-8).
class _PromptToolsList extends StatelessWidget {
  const _PromptToolsList({
    required this.attachBlocked,
    required this.attachmentsSupported,
    this.imagesOnly = false,
    required this.webSourcesSupported,
    required this.voiceBlocked,
    required this.conversationBlocked,
    required this.attachmentCount,
    required this.canReusePrompt,
    required this.canClearText,
    required this.canStash,
    required this.canOpenStash,
    required this.onPick,
    this.agentName,
  });

  /// The agent a text-only conversation talks to (Claude Code), named in
  /// place of "this server".
  final String? agentName;

  final bool attachBlocked;
  final bool attachmentsSupported;
  final bool imagesOnly;
  final bool webSourcesSupported;
  final bool voiceBlocked;
  final bool conversationBlocked;
  final int attachmentCount;
  final bool canReusePrompt;
  final bool canClearText;
  final bool canStash;
  final bool canOpenStash;
  final ValueChanged<_PromptTool> onPick;

  @override
  Widget build(BuildContext context) {
    final l10n = _chatL10n(context);
    final running = l10n.chatUiAvailableWhenTheCurrentRunFinishes;

    Widget tool(
      _PromptTool value, {
      required String key,
      required IconData icon,
      required String title,
      String? supporting,
      String? blockedBy,
    }) => KitRow(
      key: Key('composer-tool-$key'),
      leading: KitRowIcon(icon),
      title: title,
      supporting: supporting == null ? null : TextSpan(text: supporting),
      supportingMaxLines: 2,
      enabled: blockedBy == null,
      disabledReason: blockedBy,
      onTap: blockedBy == null ? () => onPick(value) : null,
    );

    final photos = platformCapabilities.supportsPromptPhotos;
    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        KitRowGroup(
          children: [
            if (attachmentsSupported && !imagesOnly)
              tool(
                _PromptTool.attach,
                key: 'attach',
                icon: AppIconography.attach,
                title: l10n.chatUiAttachFile,
                supporting: attachmentCount == 0
                    ? l10n.chatUiAddAnImageOrFileToThe
                    : l10n.chatUiAttachedCount(attachmentCount),
                blockedBy: attachBlocked ? running : null,
              )
            else
              KitRow.unavailable(
                key: const Key('composer-tool-attach'),
                leading: const KitRowIcon(AppIconography.attach),
                title: l10n.chatUiAttachFile,
                reason: imagesOnly
                    ? l10n.composerToolsAgentPicturesOnly(
                        KitBidi.auto(agentName ?? ''),
                      )
                    : agentName == null
                    ? l10n.composerToolsTextOnly
                    : l10n.composerToolsAgentTextOnly(KitBidi.auto(agentName!)),
              ),
            if (attachmentsSupported && photos) ...[
              tool(
                _PromptTool.gallery,
                key: 'gallery',
                icon: AppIconography.images,
                title: l10n.photoLibraryAction,
                supporting: l10n.photoLibraryDescription,
                blockedBy: attachBlocked ? running : null,
              ),
              tool(
                _PromptTool.camera,
                key: 'camera',
                icon: AppIconography.camera,
                title: l10n.photoCameraAction,
                blockedBy: attachBlocked ? running : null,
              ),
            ],
            // Speech capture runs on Android only (`oc/voice`).
            if (platformCapabilities.supportsVoice)
              tool(
                _PromptTool.voice,
                key: 'voice',
                icon: AppIconography.mic,
                title: l10n.chatUiVoiceInput,
                supporting: l10n.chatUiRecordsAndTranscribesOnThisDevice,
                blockedBy: voiceBlocked ? running : null,
              ),
            tool(
              _PromptTool.commands,
              key: 'commands',
              icon: AppIcons.run,
              title: l10n.composerToolCommandsTitle,
              supporting: l10n.chatUiSlashCommandsAndAgents,
            ),
          ],
        ),
        if (canReusePrompt || canOpenStash || canClearText)
          KitExpandRow(
            headerKey: const Key('composer-tools-prompts'),
            leading: const KitRowIcon(AppIconography.bookmarks),
            title: l10n.usagePrompts,
            children: [
              if (canReusePrompt)
                tool(
                  _PromptTool.history,
                  key: 'history',
                  icon: AppIconography.history,
                  title: l10n.composerReuseTitle,
                  supporting: l10n.composerReuseSubtitle,
                ),
              if (canOpenStash) ...[
                tool(
                  _PromptTool.saved,
                  key: 'saved',
                  icon: AppIconography.bookmarks,
                  title: l10n.promptStashTitle,
                  supporting: l10n.composerToolSavedSubtitle,
                ),
                tool(
                  _PromptTool.stash,
                  key: 'stash',
                  icon: AppIconography.package,
                  title: l10n.composerToolSaveForLater,
                  supporting: l10n.promptStashDescription,
                  blockedBy: canStash ? null : l10n.composerToolNothingToSave,
                ),
              ],
              if (canClearText)
                tool(
                  _PromptTool.clearText,
                  key: 'clear',
                  icon: AppIconography.textSnippet,
                  title: l10n.composerClearTextTitle,
                  supporting: l10n.composerClearTextSubtitle,
                ),
            ],
          ),
        KitExpandRow(
          headerKey: const Key('composer-tools-advanced'),
          leading: const KitRowIcon(AppIconography.layers),
          title: l10n.composerToolsMore,
          children: [
            if (webSourcesSupported)
              tool(
                _PromptTool.webSources,
                key: 'web-sources',
                icon: AppIconography.link,
                title: l10n.webSourcesTitle,
                supporting: l10n.webSourcesEntryDetail,
                blockedBy: attachBlocked ? running : null,
              ),
            if (platformCapabilities.supportsVoiceConversation)
              tool(
                _PromptTool.conversation,
                key: 'conversation',
                icon: AppIconography.speakUser,
                title: l10n.voiceConversationTitle,
                supporting: l10n.voiceConversationDescription,
                blockedBy: conversationBlocked ? running : null,
              ),
          ],
        ),
      ],
    );
  }
}

/// P4.3's composer half: a withdrawn waiting message (an edited offline
/// draft, a cancelled server send) goes back into the draft, ahead of what
/// was typed since, with "Returned to your draft · Undo". Undo puts the
/// draft back as it was and runs [onUndo] (the host queues the message
/// again). Nothing is sent.
void returnWithdrawnToDraft(
  BuildContext context, {
  required TextEditingController composer,
  required String text,
  FocusNode? focus,
  FutureOr<void> Function()? onUndo,
}) {
  if (text.trim().isEmpty) return;
  final before = composer.value;
  final current = before.text;
  final next = current.trim().isEmpty ? text : '$text\n$current';
  composer.value = TextEditingValue(
    text: next,
    selection: TextSelection.collapsed(offset: next.length),
  );
  focus?.requestFocus();
  showKitUndo(
    context,
    message: lookupAppLocalizations(
      Localizations.localeOf(context),
    ).composerReturnedToDraft,
    key: const Key('composer-returned-undo'),
    onUndo: () async {
      // Only the text this call added comes out again; later typing stays.
      if (composer.text == next) composer.value = before;
      await onUndo?.call();
    },
  );
}
