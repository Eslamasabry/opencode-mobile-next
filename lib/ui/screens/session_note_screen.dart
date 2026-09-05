import 'dart:async';

import 'package:flutter/material.dart';

import '../../domain/server_gateway.dart';
import '../../api2/transport.dart';
import '../../l10n/app_localizations.dart';
import '../../state/connection.dart';
import '../widgets/product_states.dart';

/// One editor for the mobile-owned note, never a generic instruction browser.
class SessionNoteScreen extends StatefulWidget {
  final ConnectionController controller;
  final String sessionID;
  const SessionNoteScreen({
    super.key,
    required this.controller,
    required this.sessionID,
  });
  @override
  State<SessionNoteScreen> createState() => _SessionNoteScreenState();
}

class _SessionNoteScreenState extends State<SessionNoteScreen> {
  final _text = TextEditingController();
  late final int _location = widget.controller.locationRevision;
  SessionNoteReview? _review;
  Object? _error;
  bool _loading = true;
  bool _saving = false;
  bool _allowLeave = false;
  bool _reviewRefreshed = false;
  int _maxBytes = SessionNoteGateway.maxBytes;
  bool get _dirty => _text.text != (_review?.value ?? '');
  bool get _sameLocation => widget.controller.locationRevision == _location;

  @override
  void initState() {
    super.initState();
    unawaited(_load());
  }

  Future<void> _load() async {
    if (!_sameLocation) return;
    final keepDraft = _review != null;
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final review = await widget.controller.loadSessionNote(widget.sessionID);
      if (!mounted || !_sameLocation) return;
      setState(() {
        _review = review;
        _reviewRefreshed = keepDraft;
        if (!keepDraft) _text.text = review.value ?? '';
      });
    } catch (error) {
      if (mounted) setState(() => _error = error);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _leave() async {
    final navigator = Navigator.of(context);
    final route = ModalRoute.of(context);
    setState(() => _allowLeave = true);
    await WidgetsBinding.instance.endOfFrame;
    if (!mounted || route == null || !route.isActive) return;
    if (route.isCurrent) {
      navigator.pop();
    } else {
      navigator.removeRoute(route);
    }
  }

  Future<void> _confirmLeave() async {
    if (_saving) return;
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final discard = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(l10n.sessionNoteDiscard),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: Text(l10n.sessionNoteKeepEditing),
          ),
          TextButton(
            onPressed: () => Navigator.pop(context, true),
            child: Text(l10n.sessionNoteDiscardAction),
          ),
        ],
      ),
    );
    if (mounted && discard == true) await _leave();
  }

  Future<void> _save({bool remove = false}) async {
    final review = _review;
    if (_saving || review == null || !_sameLocation) return;
    setState(() {
      _saving = true;
      _error = null;
    });
    try {
      await widget.controller.saveSessionNote(
        review,
        remove ? null : _text.text,
      );
      if (mounted) await _leave();
    } catch (error) {
      if (mounted) {
        setState(() {
          _error = error;
          if (error is SessionNoteException &&
              error.failure == SessionNoteFailure.tooLarge) {
            _maxBytes = error.maxBytes.clamp(1, SessionNoteGateway.maxBytes);
          }
        });
      }
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  String _errorText(Object error, AppLocalizations l10n) {
    if (error is Api2Error) {
      return error.statusCode == 401 || error.statusCode == 403
          ? l10n.sessionNoteAuthorization
          : error.message;
    }
    if (error is! SessionNoteException) return productErrorText(error);
    return switch (error.failure) {
      SessionNoteFailure.unsupported => l10n.sessionNoteUnsupported,
      SessionNoteFailure.changed => l10n.sessionNoteChanged,
      SessionNoteFailure.invalidValue => l10n.sessionNoteInvalid,
      SessionNoteFailure.tooLarge => l10n.sessionNoteTooLarge,
      SessionNoteFailure.busy => l10n.sessionNoteBusy,
    };
  }

  @override
  void dispose() {
    _text.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    return ListenableBuilder(
      listenable: widget.controller,
      builder: (context, _) {
        final current =
            _sameLocation &&
            (_review == null ||
                widget.controller.isSessionNoteReviewCurrent(_review!));
        final bytes = SessionNoteGateway.encodedBytes(_text.text);
        final enabled = current && !_loading && !_saving && _review != null;
        return PopScope(
          canPop: _allowLeave || (!_saving && !_dirty),
          onPopInvokedWithResult: (didPop, _) {
            if (!didPop) unawaited(_confirmLeave());
          },
          child: Scaffold(
            appBar: AppBar(title: Text(l10n.sessionNoteTitle)),
            body: SafeArea(
              child: Align(
                alignment: Alignment.topCenter,
                child: ConstrainedBox(
                  constraints: const BoxConstraints(maxWidth: 720),
                  child: SingleChildScrollView(
                    padding: const EdgeInsets.all(20),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.stretch,
                      children: [
                        Text(l10n.sessionNoteDescription),
                        const SizedBox(height: 20),
                        if (_loading) const LinearProgressIndicator(),
                        if (!current)
                          Text(
                            l10n.sessionNoteChanged,
                            style: TextStyle(
                              color: Theme.of(context).colorScheme.error,
                            ),
                          ),
                        if (_error case final error?) ...[
                          Text(
                            _errorText(error, l10n),
                            style: TextStyle(
                              color: Theme.of(context).colorScheme.error,
                            ),
                          ),
                          const SizedBox(height: 12),
                        ],
                        if (_reviewRefreshed && _review != null) ...[
                          Text(
                            l10n.sessionNoteSavedVersion,
                            style: Theme.of(context).textTheme.titleSmall,
                          ),
                          const SizedBox(height: 8),
                          SelectableText(
                            _review!.value ?? l10n.sessionNoteNone,
                          ),
                          const SizedBox(height: 20),
                        ],
                        if (_review != null) ...[
                          TextField(
                            key: const ValueKey('session-note-editor'),
                            controller: _text,
                            readOnly: _saving || !_sameLocation,
                            minLines: 5,
                            maxLines: 12,
                            textCapitalization: TextCapitalization.sentences,
                            onChanged: (_) => setState(() {}),
                            decoration: InputDecoration(
                              labelText: l10n.sessionNoteTitle,
                              hintText: l10n.sessionNoteHint,
                              alignLabelWithHint: true,
                              border: const OutlineInputBorder(),
                              counterText: l10n.sessionNoteBytes(
                                bytes,
                                _maxBytes,
                              ),
                              errorText: bytes > _maxBytes
                                  ? l10n.sessionNoteTooLarge
                                  : null,
                            ),
                          ),
                          const SizedBox(height: 20),
                          FilledButton.icon(
                            key: const ValueKey('save-session-note'),
                            onPressed:
                                enabled &&
                                    _dirty &&
                                    _text.text.trim().isNotEmpty &&
                                    bytes <= _maxBytes
                                ? _save
                                : null,
                            icon: _saving
                                ? const SizedBox.square(
                                    dimension: 18,
                                    child: CircularProgressIndicator(
                                      strokeWidth: 2,
                                    ),
                                  )
                                : const Icon(Icons.check_rounded),
                            label: Text(l10n.sessionNoteSave),
                          ),
                          if (_review!.value != null)
                            TextButton(
                              key: const ValueKey('remove-session-note'),
                              onPressed: enabled
                                  ? () => _save(remove: true)
                                  : null,
                              child: Text(l10n.sessionNoteRemove),
                            ),
                        ],
                        if (_sameLocation &&
                            !_loading &&
                            !_saving &&
                            (_error != null || !current))
                          TextButton(
                            onPressed: _load,
                            child: Text(l10n.sessionNoteRefresh),
                          ),
                      ],
                    ),
                  ),
                ),
              ),
            ),
          ),
        );
      },
    );
  }
}
