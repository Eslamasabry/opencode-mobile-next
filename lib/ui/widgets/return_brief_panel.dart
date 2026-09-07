import 'package:flutter/material.dart';

import '../../domain/return_brief.dart';
import '../../state/connection.dart';
import '../screens/activity_screen.dart' show showQuestionSheet;
import '../screens/chat/form_flow.dart';
import '../screens/chat/permission_sheet.dart';
import '../screens/run_result_screen.dart';
import 'request_routes.dart';
import 'return_brief_card.dart';

/// Workspace owns placement; this panel owns only the displayed snapshot and
/// explicit dismissal. It starts no refresh, polling, or model request.
class ReturnBriefPanel extends StatefulWidget {
  const ReturnBriefPanel({super.key, required this.controller});
  final ConnectionController controller;
  @override
  State<ReturnBriefPanel> createState() => _ReturnBriefPanelState();
}

class _ReturnBriefPanelState extends State<ReturnBriefPanel> {
  bool _saving = false;
  bool _saveFailed = false;
  Object? _scope;

  bool _current((String, String, int) scope) =>
      mounted &&
      widget.controller.returnBriefScope == scope &&
      widget.controller.isProfileReadable(scope.$1);

  Future<void> _dismiss(ReturnBrief shown, (String, String, int) scope) async {
    if (_saving || !_current(scope)) return;
    setState(() {
      _saving = true;
      _saveFailed = false;
    });
    try {
      await widget.controller.dismissReturnBrief(shown, expectedScope: scope);
    } catch (_) {
      if (_current(scope)) setState(() => _saveFailed = true);
    } finally {
      if (_current(scope)) setState(() => _saving = false);
    }
  }

  void _answer(ReturnBriefRequest shown, (String, String, int) scope) {
    if (!_current(scope)) return;
    final c = widget.controller;
    switch (shown.blocker) {
      case ReturnBriefBlocker.permission:
        final request = c.permissions[shown.requestID];
        if (request == null || request.sessionID != shown.session.id) return;
        showPermissionSheet(context, permission: request, controller: c);
      case ReturnBriefBlocker.question:
        final request = c.questions[shown.requestID];
        if (request == null || request.sessionID != shown.session.id) return;
        showQuestionSheet(context, c, request);
      case ReturnBriefBlocker.form:
        final request = c.forms[shown.requestID];
        if (!c.capabilities.forms ||
            request == null ||
            request.sessionID != shown.session.id) {
          return;
        }
        presentConnectionForm(context, c, request);
    }
  }

  Future<void> _review(ReturnBriefRun run, (String, String, int) scope) async {
    if (!_current(scope)) return;
    final c = widget.controller;
    // The reused result screen loads server history. Retire its route when
    // its owner/location changes, including while a page is in flight.
    final routes = RequestRoutes(changes: c, isPending: () => _current(scope));
    try {
      await Navigator.of(context).push<void>(
        MaterialPageRoute(
          builder: (context) {
            routes.own(ModalRoute.of(context));
            return RunResultScreen(controller: c, sessionID: run.session.id);
          },
        ),
      );
    } finally {
      routes.close();
    }
  }

  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: widget.controller,
    builder: (context, _) {
      final c = widget.controller;
      final scope = c.returnBriefScope;
      if (_scope != scope) {
        _scope = scope;
        _saving = false;
        _saveFailed = false;
      }
      if (!c.isProfileReadable(scope.$1)) return const SizedBox.shrink();
      final partial =
          c.hasMoreSessions || c.sessionsLoading || c.sessionsError != null;
      final stale =
          !c.isConnected ||
          c.permissionsLoading ||
          c.questionsLoading ||
          c.formsLoading ||
          c.permissionsError != null ||
          c.questionsError != null ||
          c.formsError != null;
      final brief = ReturnBrief.build(
        sessions: c.sortedSessions(),
        readStateKnown: c.supportsSessionReadState,
        inventoryPartial: partial,
        isUnread: c.isSessionUnread,
        isBusy: c.busySessions.contains,
        blockerOf: (id) {
          final permissions = c.permissionsForSession(id);
          if (permissions.isNotEmpty) {
            return (ReturnBriefBlocker.permission, permissions.first.id);
          }
          final question = c.questionForSession(id);
          if (question != null) {
            return (ReturnBriefBlocker.question, question.id);
          }
          final form = c.capabilities.forms ? c.formForSession(id) : null;
          return form == null ? null : (ReturnBriefBlocker.form, form.id);
        },
        ack: c.returnBriefAcknowledgement,
      );
      return ReturnBriefCard(
        brief: brief,
        stale: stale,
        saving: _saving,
        saveFailed: _saveFailed,
        onDismiss: () => _dismiss(brief, scope),
        onReview: (run) => _review(run, scope),
        onAnswer: (request) => _answer(request, scope),
        onContinue: (id) {
          if (_current(scope)) Navigator.of(context).pushNamed('/chat/$id');
        },
      );
    },
  );
}
