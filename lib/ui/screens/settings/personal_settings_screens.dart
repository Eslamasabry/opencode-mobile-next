part of '../settings_screen.dart';

/// Privacy and local data (`privacy-settings`): read-state sync, the unsent
/// work this device holds on the person's behalf with its size and a way
/// to delete it, and the privacy policy. Durable grants ("Always allowed
/// actions") live under Conversation defaults in the hub: they are about
/// how the agent works, not about privacy.
class PrivacySettingsScreen extends StatefulWidget {
  final ConnectionController controller;
  const PrivacySettingsScreen({super.key, required this.controller});

  @override
  State<PrivacySettingsScreen> createState() => _PrivacySettingsScreenState();
}

class _PrivacySettingsScreenState extends State<PrivacySettingsScreen> {
  bool _busy = false;

  /// What the last delete did, said on the page (KIT-34: no snackbar for a
  /// delete that cannot be undone).
  ({String message, bool ok})? _result;

  ConnectionController get _controller => widget.controller;

  /// Rounded the way a phone's storage screens round: one decimal past a
  /// kilobyte, and never "0 B" for something that exists.
  static String formatBytes(int bytes) {
    if (bytes <= 0) return '0 B';
    if (bytes < 1024) return '$bytes B';
    if (bytes < 1024 * 1024) {
      return '${(bytes / 1024).toStringAsFixed(1)} KB';
    }
    return '${(bytes / (1024 * 1024)).toStringAsFixed(1)} MB';
  }

  /// Asks, then clears; true when the person confirmed and it cleared.
  Future<bool> _confirmAndClear({
    required String title,
    required String body,
    required String confirmLabel,
    required String confirmKey,
    required Future<bool> Function() clear,
    required String cleared,
    required String failed,
  }) async {
    final ok = await showKitConfirm(
      context,
      title: title,
      body: body,
      confirmLabel: confirmLabel,
      icon: AppIconography.delete,
      kind: KitConfirmKind.destructive,
      confirmKey: ValueKey(confirmKey),
    );
    if (!ok || !mounted) return false;
    setState(() {
      _busy = true;
      _result = null;
    });
    final succeeded = await clear();
    if (!mounted) return succeeded;
    setState(() {
      _busy = false;
      _result = (message: succeeded ? cleared : failed, ok: succeeded);
    });
    return succeeded;
  }

  /// Read-state sharing is each server's own consent, kept on this phone: it
  /// is switched on that server's page, not here (G4). This row says where
  /// and how many servers share, and opens the one server or the list.
  Widget _readState(BuildContext context) => ListenableBuilder(
    listenable: Listenable.merge([
      _controller,
      _controller.store.appPreferences.readStateConsent,
    ]),
    builder: (context, _) {
      final servers = settingsServers(_controller);
      if (servers.isEmpty) return const SizedBox.shrink();
      final l10n =
          Localizations.of<AppLocalizations>(context, AppLocalizations) ??
          lookupAppLocalizations(const Locale('en'));
      final tokens = KitTokens.of(context);
      final consent = _controller.store.appPreferences.readStateConsent;
      final sharing = servers.where((p) => consent.enabled(p.id)).length;
      final value = servers.length == 1
          ? (sharing == 1 ? l10n.settingsV3On : l10n.settingsV3Off)
          : l10n.settingsV3SharingCount(sharing, servers.length);
      return Padding(
        padding: EdgeInsetsDirectional.only(bottom: tokens.sectionGap),
        child: KitRowGroup(
          label: l10n.privacySharedSection,
          children: [
            KitRow(
              key: const ValueKey('share-session-views'),
              leading: KitRow.icon(context, AppIconography.visible),
              title: l10n.shareSessionViewsTitle,
              trailing: KitRowValue(value),
              onTap: () => servers.length == 1
                  ? unawaited(
                      openServerPage(
                        context,
                        _controller,
                        servers.single.id,
                        arrive: 'server-row-share-read',
                      ),
                    )
                  : unawaited(
                      pushKitPage<void>(
                        context,
                        (_) => ByServerScreen(
                          controller: _controller,
                          kind: ByServerKind.readState,
                        ),
                      ),
                    ),
            ),
          ],
        ),
      );
    },
  );

  @override
  Widget build(BuildContext context) {
    final copy = _settingsCopy(context);
    final tokens = KitTokens.of(context);
    final scope = SearchScope.of(context, _controller);
    final policy = searchIndex(
      copy,
      scope,
    ).where((entry) => entry.id == 'settings-privacy-data-use').firstOrNull;
    final result = _result;
    return KitScreen(
      topBar: KitTopBar(title: copy.settingsHubPrivacyRow),
      width: KitScreenWidth.reading,
      loading: _busy,
      loadingLabel: copy.privacyDeleting,
      body: ListView(
        key: const ValueKey('privacy-settings'),
        padding: EdgeInsetsDirectional.only(
          top: tokens.space2,
          bottom: KitScreen.endPadding(context),
        ),
        children: [
          if (result != null)
            Padding(
              padding: EdgeInsetsDirectional.only(
                start: tokens.gutter,
                end: tokens.gutter,
                bottom: tokens.sectionGap,
              ),
              child: KitNotice(
                key: const ValueKey('privacy-clear-result'),
                tone: result.ok ? AppStatusTone.ok : AppStatusTone.failure,
                message: result.message,
                onDismiss: () => setState(() => _result = null),
                dismissLabel: copy.notifyDismiss,
              ),
            ),
          _readState(context),
          // Queued prompts carry attachment data URLs and drafts carry
          // whatever was typed but never sent. Both are the user's content,
          // held indefinitely until a server answers, so both get a size and
          // a way out that does not require deleting the server.
          ListenableBuilder(
            listenable: _controller,
            builder: (context, _) {
              final queued = _controller.totalQueuedPromptCount;
              final drafts = _controller.totalSessionDraftCount;
              final queuedBytes = _controller.queuedPromptBytes;
              final queueReadable = _controller.queuedPromptStorageReadable;
              final l10n = lookupAppLocalizations(
                Localizations.localeOf(context),
              );
              final draftBytes = _controller.sessionDraftBytes;
              return KitRowGroup(
                label: copy.e7SettingsUi76,
                children: [
                  KitRow(
                    key: const ValueKey('local-storage-usage'),
                    leading: KitRow.icon(context, AppIconography.storage),
                    title: copy.e7SettingsUi77,
                    supporting: TextSpan(
                      text: !queueReadable
                          ? l10n.queueStorageCountUnknown
                          : copy.e7SettingsStorageSummary(
                              formatBytes(queuedBytes + draftBytes),
                              queued,
                              formatBytes(queuedBytes),
                              drafts,
                              formatBytes(draftBytes),
                              OfflineQueueStore.maxAge.inDays,
                            ),
                    ),
                    // A readout, not a door: the whole sentence stays.
                    supportingMaxLines: 4,
                  ),
                  // Destructive (§2): confirmed first, never primary. With
                  // nothing to delete the row rests, and its own line says
                  // so.
                  _DestructiveRow(
                    rowKey: 'clear-queued-prompts',
                    icon: AppIconography.outbox,
                    title: copy.e7SettingsUi78,
                    subtitle: !queueReadable
                        ? l10n.queueStorageUnreadable
                        : queued == 0
                        ? copy.e7SettingsUi79
                        : copy.e7SettingsQueueDeleteSummary(queued),
                    enabled: (queued > 0 || !queueReadable) && !_busy,
                    onTap: () => _confirmAndClear(
                      title: copy.e7SettingsUi80,
                      body: !queueReadable
                          ? l10n.queueStorageDiscardUnreadable
                          : copy.e7SettingsQueueDeleteBody(queued),
                      // The counted verb (privacy-settings-clear-queued-sheet).
                      confirmLabel: copy.privacyDeleteQueuedCount(
                        queueReadable ? queued : 0,
                      ),
                      confirmKey: 'privacy-clear-queued-confirm',
                      clear: _controller.clearAllQueuedPrompts,
                      cleared: copy.e7SettingsUi81,
                      failed: copy.e7SettingsUi82,
                    ),
                  ),
                  _DestructiveRow(
                    rowKey: 'clear-session-drafts',
                    icon: AppIconography.editNote,
                    title: copy.e7SettingsUi83,
                    subtitle: drafts == 0
                        ? copy.e7SettingsUi84
                        : copy.e7SettingsDraftDeleteSummary(drafts),
                    enabled: drafts > 0 && !_busy,
                    onTap: () => _confirmAndClear(
                      title: copy.e7SettingsUi85,
                      body: copy.e7SettingsDraftDeleteBody(drafts),
                      confirmLabel: copy.privacyDeleteDraftsCount(drafts),
                      confirmKey: 'privacy-clear-drafts-confirm',
                      clear: _controller.clearAllSessionDrafts,
                      cleared: copy.e7SettingsUi86,
                      failed: copy.e7SettingsUi87,
                    ),
                  ),
                ],
              );
            },
          ),
          // The policy itself: About's old Privacy tab lives here now
          // (P3.10), so what the app keeps and what it sends sit together.
          if (policy != null) ...[
            SizedBox(height: tokens.sectionGap),
            KitRowGroup(
              children: [
                KitRow(
                  key: const ValueKey('privacy-policy'),
                  leading: KitRow.icon(context, policy.icon),
                  title: policy.title,
                  supporting: TextSpan(text: copy.e7SettingsUi93),
                  supportingMaxLines: 2,
                  trailing: const KitChevron(),
                  onTap: () => policy.open(context, scope),
                ),
              ],
            ),
          ],
        ],
      ),
    );
  }
}

/// A row that deletes something this device holds (design standard §2):
/// the kit's destructive row, always confirmed by its action, never a
/// primary button.
class _DestructiveRow extends StatelessWidget {
  const _DestructiveRow({
    required this.rowKey,
    required this.icon,
    required this.title,
    required this.subtitle,
    required this.enabled,
    required this.onTap,
  });

  final String rowKey;
  final IconData icon;
  final String title;
  final String subtitle;
  final bool enabled;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) => KitRow(
    key: ValueKey(rowKey),
    leading: KitRow.icon(context, icon),
    title: title,
    supporting: TextSpan(text: subtitle),
    supportingMaxLines: 2,
    destructive: true,
    enabled: enabled,
    onTap: onTap,
  );
}
