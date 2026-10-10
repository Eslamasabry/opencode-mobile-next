import 'dart:async';

import 'package:clock/clock.dart';
import 'package:flutter/material.dart';

import '../../../domain/relative_age.dart';
import '../../../domain/server_gateway.dart';
import '../../../l10n/app_localizations.dart';
import '../../app_iconography.dart';
import '../../app_theme.dart' show AppStatusTone;
import '../../kit/kit.dart';
import '../../widgets/product_states.dart' show productErrorDetails;

/// Opens "Import from Claude Code" and returns the id of the conversation
/// that was imported, or null when the person left without importing.
Future<String?> showClaudeImport(
  BuildContext context,
  ProviderConversationImportGateway gateway,
) => pushKitPage<String>(context, (_) => ClaudeImportScreen(gateway: gateway));

/// The conversations the person started in Claude Code on their computer, in
/// this project, newest first: each row says what it was about, its project
/// folder and when it was last used, never an id. A tap imports it into this
/// server's list and the screen closes with its id so the conversation opens.
class ClaudeImportScreen extends StatefulWidget {
  const ClaudeImportScreen({super.key, required this.gateway});

  final ProviderConversationImportGateway gateway;

  @override
  State<ClaudeImportScreen> createState() => _ClaudeImportScreenState();
}

class _ClaudeImportScreenState extends State<ClaudeImportScreen> {
  ImportableConversations? _found;
  Object? _loadError;
  String? _importing;
  bool _importFailed = false;

  @override
  void initState() {
    super.initState();
    unawaited(_load());
  }

  Future<void> _load() async {
    setState(() {
      _found = null;
      _loadError = null;
    });
    try {
      final found = await widget.gateway.importableConversations();
      if (mounted) setState(() => _found = found);
    } catch (error) {
      if (mounted) setState(() => _loadError = error);
    }
  }

  Future<void> _import(ImportableConversation conversation) async {
    if (_importing != null) return;
    setState(() {
      _importing = conversation.handle;
      _importFailed = false;
    });
    try {
      final session = await widget.gateway.importConversation(conversation);
      if (!mounted) return;
      Navigator.of(context).pop(session.id);
    } catch (_) {
      if (mounted) {
        setState(() {
          _importing = null;
          _importFailed = true;
        });
      }
    }
  }

  static String _folderName(String directory) {
    final parts = directory.split('/').where((part) => part.isNotEmpty);
    return parts.isEmpty ? directory : parts.last;
  }

  @override
  Widget build(BuildContext context) {
    final copy = AppLocalizations.of(context);
    return KitScreen(
      topBar: KitTopBar(title: copy.claudeImportTitle),
      width: KitScreenWidth.reading,
      body: _body(context, copy),
    );
  }

  Widget _body(BuildContext context, AppLocalizations copy) {
    final tokens = KitTokens.of(context);
    final error = _loadError;
    if (error != null) {
      return KitStateView.error(
        title: copy.claudeImportLoadFailedTitle,
        body: copy.claudeImportLoadFailedBody,
        error: error,
        details: productErrorDetails(error),
        retry: KitAction(
          key: const Key('claude-import-retry'),
          label: copy.commonRetry,
          onPressed: () => unawaited(_load()),
        ),
      );
    }
    final found = _found;
    if (found == null) {
      return ListView(
        key: const Key('claude-import-loading'),
        padding: KitScreen.padding(context),
        children: const [KitSkeletonRows(count: 6)],
      );
    }
    if (found.items.isEmpty) {
      return KitStateView(
        key: const Key('claude-import-empty'),
        icon: AppIconography.history,
        title: copy.claudeImportEmptyTitle,
        body: copy.claudeImportEmptyBody,
      );
    }
    final project = _folderName(widget.gateway.providerImportDirectory);
    final now = clock.now();
    return ListView(
      padding: EdgeInsets.only(
        top: tokens.space3,
        bottom: KitScreen.endPadding(context),
      ),
      children: [
        Padding(
          padding: EdgeInsets.symmetric(horizontal: tokens.gutter),
          child: KitText(
            copy.claudeImportIntro(project),
            role: KitTextRole.secondary,
          ),
        ),
        SizedBox(height: tokens.space3),
        if (_importFailed)
          Padding(
            padding: EdgeInsets.only(
              left: tokens.gutter,
              right: tokens.gutter,
              bottom: tokens.space3,
            ),
            child: KitNotice(
              key: const Key('claude-import-failed'),
              tone: AppStatusTone.failure,
              message: copy.claudeImportFailed,
            ),
          ),
        KitRowGroup(
          children: [
            for (final item in found.items)
              KitRow(
                key: ValueKey('claude-import-${item.handle}'),
                leading: KitRow.icon(context, AppIconography.history),
                title: item.displayTitle ?? copy.claudeImportUntitled,
                titleMaxLines: 2,
                supporting: TextSpan(
                  text: _importing == item.handle
                      ? copy.claudeImportWorking
                      : copy.claudeImportRowDetail(
                          _folderName(item.directory),
                          relativeAgeLabel(
                            now.difference(item.lastUsed),
                            at: item.lastUsed,
                            l10n: copy,
                          ),
                        ),
                ),
                supportingMaxLines: 2,
                enabled: _importing == null,
                onTap: () => unawaited(_import(item)),
              ),
          ],
        ),
        if (found.alreadyImported > 0)
          KitGroupNote(
            key: const Key('claude-import-already'),
            message: copy.claudeImportAlready(found.alreadyImported),
          ),
      ],
    );
  }
}
