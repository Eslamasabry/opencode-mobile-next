part of '../gateway.dart';

/// Native questions travel over Paseo's permission wire but are never tool
/// grants. Keep their payload and lifecycle separate from approval requests.
class _PaseoQuestion {
  final int epoch;
  final PendingQuestion question;
  final Map<String, dynamic> json;
  final List<String> answerKeys;
  final bool plan;

  const _PaseoQuestion({
    required this.epoch,
    required this.question,
    required this.json,
    required this.answerKeys,
    required this.plan,
  });
}

extension _PaseoQuestions on PaseoGateway {
  void _addQuestion(String sessionID, Map<String, dynamic> request) {
    final id = paseoString(request['id'], max: 512);
    final previous = _questions[id];
    if ((previous != null && previous.question.sessionID != sessionID) ||
        _permissions.containsKey(id) ||
        _answered.contains(id)) {
      return;
    }
    _PaseoQuestion? pending;
    try {
      pending = _parseQuestion(sessionID, id, request);
    } on PaseoFailure {
      // A malformed new revision must not leave the old prompt answerable.
    }
    if (pending == null) {
      if (previous != null) _resolveQuestion(id);
      return;
    }
    if (previous != null &&
        previous.epoch == pending.epoch &&
        previous.plan == pending.plan &&
        jsonEncode(previous.json) == jsonEncode(pending.json) &&
        jsonEncode(previous.answerKeys) == jsonEncode(pending.answerKeys)) {
      return;
    }
    // Match the bounded permission inventory; an oversized daemon snapshot
    // cannot turn into unbounded retained question text.
    if (previous == null && _questions.length >= 64) return;
    _questions[id] = pending;
    _emit('question.v2.asked', _questionJson(pending));
    _nativeQuestionChanges.add(null);
  }

  _PaseoQuestion? _parseQuestion(
    String sessionID,
    String id,
    Map<String, dynamic> request,
  ) {
    final provider = _agents[sessionID]?['provider'];
    if (!{'claude', 'pi', 'omp'}.contains(provider) ||
        (request['provider'] != null && request['provider'] != provider)) {
      return null;
    }
    final input = paseoObject(request['input']);
    final plan = request['kind'] == 'plan';
    final prompts = <Map<String, dynamic>>[];
    final keys = <String>[];
    if (plan) {
      if (provider != 'claude' || request['name'] != 'ExitPlanMode') {
        return null;
      }
      final actions = paseoList(request['actions'], max: 16);
      if (!actions.any(
            (a) =>
                a is Map && a['id'] == 'implement' && a['behavior'] == 'allow',
          ) ||
          !actions.any(
            (a) => a is Map && a['id'] == 'reject' && a['behavior'] == 'deny',
          )) {
        return null;
      }
      final metadata = request['metadata'];
      final text = paseoString(
        input['plan'] ?? (metadata is Map ? metadata['planText'] : null),
        max: 65536,
      );
      if (text.trim().isEmpty) return null;
      prompts.add({
        'header': 'Plan',
        'question': text,
        'multiple': false,
        'custom': false,
        'optional': false,
        'options': [
          {
            'label': 'Approve',
            'description': 'Implement this plan with edit permissions.',
          },
          {
            'label': 'Keep planning',
            'description': 'Do not implement this plan.',
          },
        ],
      });
    } else {
      if (provider == 'claude' && request['name'] != 'AskUserQuestion') {
        return null;
      }
      final rawPrompts = paseoList(input['questions'], max: 16);
      if (rawPrompts.isEmpty) return null;
      for (final raw in rawPrompts) {
        final value = paseoObject(raw);
        final question = paseoString(value['question'], max: 16384);
        final header = paseoString(value['header'], max: 512);
        if (question.trim().isEmpty || header.trim().isEmpty) return null;
        for (final flag in [
          'multiSelect',
          'allowOther',
          'allowEmpty',
          'isOther',
        ]) {
          if (value[flag] != null && value[flag] is! bool) return null;
        }
        final key = provider == 'claude' ? question : header;
        if (keys.contains(key)) return null;
        keys.add(key);
        final options = <Map<String, String>>[];
        final labels = <String>{};
        for (final rawOption in paseoList(value['options'], max: 64)) {
          final option = paseoObject(rawOption);
          final label = paseoString(option['label'], max: 512);
          if (label.trim().isEmpty || !labels.add(label)) return null;
          options.add({
            'label': label,
            'description': paseoString(
              option['description'] ?? '',
              max: 4096,
              empty: true,
            ),
          });
        }
        prompts.add({
          'header': header,
          'question': question,
          'multiple': value['multiSelect'] == true,
          'custom':
              options.isEmpty ||
              value['allowOther'] == true ||
              value['isOther'] == true,
          'optional': value['allowEmpty'] == true,
          'options': options,
        });
      }
    }
    final json = <String, dynamic>{
      'id': id,
      'sessionID': sessionID,
      'questions': prompts,
    };
    if (jsonEncode(json).length > 128 * 1024) return null;
    return _PaseoQuestion(
      epoch: transport.epoch,
      question: PendingQuestion.fromJson(json),
      json: json,
      answerKeys: keys,
      plan: plan,
    );
  }

  Map<String, dynamic> _questionJson(_PaseoQuestion pending) =>
      jsonDecode(jsonEncode(pending.json)) as Map<String, dynamic>;

  _PaseoQuestion _currentQuestion(String sessionID, String requestID) {
    final pending = _questions[requestID];
    if (pending == null ||
        pending.question.sessionID != sessionID ||
        _closed ||
        !transport.connected ||
        pending.epoch != transport.epoch) {
      throw PaseoFailure(PaseoFailureKind.staleRequest);
    }
    return pending;
  }

  Future<void> _answerQuestion(
    String sessionID,
    String requestID,
    List<List<String>> answers,
  ) async {
    final pending = _currentQuestion(sessionID, requestID);
    final prompts = pending.question.prompts;
    if (answers.length != prompts.length) {
      throw PaseoFailure(PaseoFailureKind.invalidResponse);
    }
    final values = <String>[];
    for (var i = 0; i < answers.length; i++) {
      final selected = answers[i];
      final prompt = prompts[i];
      if (selected.length > 64 ||
          (!prompt.multiple && selected.length > 1) ||
          selected.toSet().length != selected.length ||
          selected.any((v) => v.length > 16384 || v.trim().isEmpty) ||
          (selected.isEmpty && !prompt.optional) ||
          (!prompt.custom &&
              selected.any((v) => !prompt.choices.any((c) => c.label == v)))) {
        throw PaseoFailure(PaseoFailureKind.invalidResponse);
      }
      values.add(selected.join(', '));
    }
    if (values.fold<int>(0, (n, value) => n + value.length) > 128 * 1024) {
      throw PaseoFailure(PaseoFailureKind.invalidResponse);
    }
    if (pending.plan) {
      final approve = values.single == 'Approve';
      _sendQuestionResponse(pending, {
        'behavior': approve ? 'allow' : 'deny',
        // Never implement_resume: it can restore bypassPermissions.
        'selectedActionId': approve ? 'implement' : 'reject',
      }, rejected: !approve);
    } else {
      _sendQuestionResponse(pending, {
        'behavior': 'allow',
        'updatedInput': {
          'answers': {
            for (var i = 0; i < values.length; i++)
              pending.answerKeys[i]: values[i],
          },
        },
      });
    }
  }

  Future<void> _rejectQuestion(String sessionID, String requestID) async {
    final pending = _currentQuestion(sessionID, requestID);
    _sendQuestionResponse(pending, {
      'behavior': 'deny',
      if (pending.plan) 'selectedActionId': 'reject',
    }, rejected: true);
  }

  void _sendQuestionResponse(
    _PaseoQuestion pending,
    Map<String, dynamic> response, {
    bool rejected = false,
  }) {
    final question = pending.question;
    transport.send('agent_permission_response', {
      'agentId': _real(question.sessionID),
      'requestId': question.id,
      'response': response,
    }, expectedEpoch: pending.epoch);
    _answered.add(question.id);
    while (_answered.length > 256) {
      _answered.remove(_answered.first);
    }
    _resolveQuestion(question.id, rejected: rejected);
  }

  void _resolveQuestion(String requestID, {bool rejected = false}) {
    final removed = _questions.remove(requestID);
    if (removed == null) return;
    _emit(rejected ? 'question.v2.rejected' : 'question.v2.replied', {
      'sessionID': removed.question.sessionID,
      'requestID': requestID,
    });
    _nativeQuestionChanges.add(null);
  }
}
