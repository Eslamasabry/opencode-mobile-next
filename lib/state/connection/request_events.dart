part of '../connection.dart';

// Event handlers for permissions, questions, forms and the inbox.

extension _ConnectionControllerRequestEventsImpl on ConnectionController {
  /// Permission, question, form and inbox events.
  void _onRequestEvent(EventEnvelope env, Map<String, dynamic> props) {
    _invalidateFeedDirectoryQuestions(env);
    switch (env.type) {
      case 'permission.asked':
        _handlePermission(props);
        break;

      case 'permission.v2.asked':
        _handlePermissionV2(props);
        break;

      case 'permission.updated':
        _handleLegacyPermission(props);
        break;

      case 'permission.replied':
      case 'permission.v2.replied':
        _handlePermissionReply(props);
        break;

      // ---- OpenCode 2 interaction envelopes ----
      // Emitted by the v2 event adapter (lib/api2/gateway_events.dart):
      //   form.v2.created                 {form: Form.Info (raw v2 JSON)}
      //   form.v2.replied                 {id, sessionID}
      //   form.v2.cancelled               {id, sessionID}
      //   session.inbox.enqueued          {sessionID, inboxID, item}
      //   session.inbox.delivered         {sessionID, inboxID}
      //   session.inbox.cancelled         {sessionID, inboxID}
      //   session.inbox.delivery.changed  {sessionID, inboxID, delivery}
      case 'form.v2.created':
        if (supportsForms) _handleFormCreated(props);
        break;

      case 'form.v2.replied':
      case 'form.v2.cancelled':
        if (!supportsForms) break;
        final formID = props['id']?.toString() ?? '';
        if (formID.isNotEmpty) _resolveForm(formID);
        break;

      case 'session.inbox.enqueued':
        _handleInboxEnqueued(props);
        break;

      case 'session.inbox.delivered':
      case 'session.inbox.cancelled':
        _handleInboxRemoved(props);
        break;

      case 'session.inbox.delivery.changed':
        _handleInboxDeliveryChanged(props);
        break;

      case 'question.asked':
      case 'question.updated':
        questionsLoading = false;
        final question = PendingQuestion.fromJson(props);
        if (question.id.isNotEmpty && question.sessionID.isNotEmpty) {
          _markQuestionChanged(question.id);
          _resolvedQuestionIDs.remove(question.id);
          _v2QuestionSessions.remove(question.id);
          questions[question.id] = question;
          _syncInputAlerts();
          _notifyListeners();
        }
        break;

      case 'question.v2.asked':
        questionsLoading = false;
        final question = PendingQuestion.fromJson(props);
        if (question.id.isNotEmpty && question.sessionID.isNotEmpty) {
          _markQuestionChanged(question.id);
          _resolvedQuestionIDs.remove(question.id);
          _v2QuestionSessions[question.id] = question.sessionID;
          questions[question.id] = question;
          _syncInputAlerts();
          _notifyListeners();
        }
        break;

      case 'question.replied':
      case 'question.rejected':
      case 'question.v2.replied':
      case 'question.v2.rejected':
        questionsLoading = false;
        final id = props['requestID']?.toString() ?? props['id']?.toString();
        if (id != null && id.isNotEmpty) {
          _markQuestionChanged(id);
          _resolvedQuestionIDs.add(id);
          _v2QuestionSessions.remove(id);
          if (questions.remove(id) != null) {
            _syncInputAlerts();
            _notifyListeners();
          }
        }
        break;
    }
  }
}
