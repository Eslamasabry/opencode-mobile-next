part of '../product_repository.dart';

mixin _SdkSessionOps on _SdkCore {
  @override
  Future<List<CommandInfo>> listCommands() =>
      _guard('Could not load commands', () async {
        final response = await _client.getCommandsApi().v2CommandList(
          locationLeftSquareBracketDirectoryRightSquareBracket: _directory,
          locationLeftSquareBracketWorkspaceRightSquareBracket: _workspace,
        );
        return (response.data?.data ?? const [])
            .map(
              (command) => CommandInfo(
                name: command.name,
                description: command.description,
                agent: command.agent,
                subtask: command.subtask == true,
              ),
            )
            .toList();
      });

  @override
  Future<List<SkillInfo>> listSkills() =>
      _guard('Could not load skills', () async {
        final response = await _client.getSkillsApi().v2SkillList(
          locationLeftSquareBracketDirectoryRightSquareBracket: _directory,
          locationLeftSquareBracketWorkspaceRightSquareBracket: _workspace,
        );
        return (response.data?.data ?? const [])
            .map(
              (skill) => SkillInfo(
                name: skill.name,
                description: skill.description,
                location: skill.location,
                content: skill.content,
                slashCommand: skill.slash == true,
              ),
            )
            .toList();
      });

  @override
  Future<List<ReferenceInfo>> listReferences() =>
      _guard('Could not load references', () async {
        final response = await _client.getReferenceApi().v2ReferenceList(
          locationLeftSquareBracketDirectoryRightSquareBracket: _directory,
          locationLeftSquareBracketWorkspaceRightSquareBracket: _workspace,
        );
        return (response.data?.data ?? const [])
            .where((reference) => reference.hidden != true)
            .map(
              (reference) => ReferenceInfo(
                name: reference.name,
                path: reference.path,
                description: reference.description,
              ),
            )
            .toList();
      });

  @override
  Future<List<PendingQuestion>> listQuestions() =>
      _guard('Could not load pending questions', () async {
        final response = await _client.getQuestionApi().questionList(
          directory: _directory,
          workspace: _workspace,
        );
        return (response.data ?? const [])
            .map((question) => PendingQuestion.fromJson(question.toJson()))
            .toList();
      });

  @override
  Future<List<SavedPermission>> listSavedPermissions() =>
      _guard('Could not load always allowed actions', () async {
        final projectResponse = await _client.getProjectApi().projectCurrent(
          directory: _directory,
          workspace: _workspace,
        );
        final projectID = projectResponse.data?.id ?? '';
        if (projectID.trim().isEmpty) {
          throw const ProductException('OpenCode returned no current project');
        }
        final response = await _client
            .getPermissionsApi()
            .v2PermissionSavedList(projectID: projectID);
        return (response.data?.data ?? const [])
            .where((permission) => permission.projectID == projectID)
            .map(
              (permission) => SavedPermission(
                id: permission.id,
                projectID: permission.projectID,
                action: permission.action,
                resource: permission.resource,
              ),
            )
            .toList();
      });

  @override
  Future<void> removeSavedPermission(String id) =>
      _guard('Could not revoke the always allowed action', () async {
        if (id.trim().isEmpty) {
          throw const ProductException('Saved permission ID is missing');
        }
        await _client.getPermissionsApi().v2PermissionSavedRemove(id: id);
      });

  @override
  Future<void> answerQuestion(String id, List<List<String>> answers) => _guard(
    'Could not send the answer',
    () async => _client.getQuestionApi().questionReply(
      requestID: id,
      directory: _directory,
      workspace: _workspace,
      questionReplyRequest: sdk.QuestionReplyRequest(answers: answers),
    ),
  );

  @override
  Future<void> rejectQuestion(String id) => _guard(
    'Could not dismiss the question',
    () async => _client.getQuestionApi().questionReject(
      requestID: id,
      directory: _directory,
      workspace: _workspace,
    ),
  );

  @override
  Future<String?> shareSession(String id) =>
      _guard('Could not share the session', () async {
        final response = await _client.getSessionApi().sessionShare(
          sessionID: id,
          directory: _directory,
          workspace: _workspace,
        );
        return response.data?.share?.url;
      });

  @override
  Future<void> unshareSession(String id) => _guard(
    'Could not stop sharing the session',
    () async => _client.getSessionApi().sessionUnshare(
      sessionID: id,
      directory: _directory,
      workspace: _workspace,
    ),
  );

  @override
  Future<void> archiveSession(String id) => _guard(
    'Could not archive the session',
    () async => _client.getSessionApi().sessionUpdate(
      sessionID: id,
      directory: _directory,
      workspace: _workspace,
      sessionUpdateRequest: sdk.SessionUpdateRequest(
        time: sdk.SessionUpdateRequestTime(
          archived: DateTime.now().millisecondsSinceEpoch,
        ),
      ),
    ),
  );

  @override
  Future<String> forkSession(String id, {String? messageID}) =>
      _guard('Could not fork the session', () async {
        final response = await _client.getSessionApi().sessionFork(
          sessionID: id,
          directory: _directory,
          workspace: _workspace,
          sessionForkRequest: sdk.SessionForkRequest(messageID: messageID),
        );
        final fork = response.data;
        if (fork == null) {
          throw const ProductException('Server returned no forked session');
        }
        // The server dates a fork like a brand-new session; give it a plain
        // name (best effort: a failed rename never fails the fork).
        try {
          final original = await getSessionDetails(id);
          final title = forkedSessionTitle(original.title);
          if (title != null) {
            await _client.getSessionApi().sessionUpdate(
              sessionID: fork.id,
              directory: _directory,
              workspace: _workspace,
              sessionUpdateRequest: sdk.SessionUpdateRequest(title: title),
            );
          }
        } on Object {
          // Leave the server's title; the display layer hides its stamp.
        }
        return fork.id;
      });

  @override
  Future<void> deleteMessage({
    required String sessionID,
    required String messageID,
  }) =>
      // A declared refusal (for example a message still owned by an active
      // response) stays in the cause for redacted Details, never the copy.
      _guardWorktree(
        'Could not delete the message',
        () async => _client.getSessionApi().sessionDeleteMessage(
          sessionID: sessionID,
          messageID: messageID,
          directory: _directory,
          workspace: _workspace,
        ),
      );

  @override
  Future<void> revertSession(String id, String messageID) => _guard(
    'Could not revert the session',
    () async => _client.getSessionApi().sessionRevert(
      sessionID: id,
      directory: _directory,
      workspace: _workspace,
      sessionRevertRequest: sdk.SessionRevertRequest(messageID: messageID),
    ),
  );

  @override
  Future<void> restoreSession(String id) => _guard(
    'Could not restore the session',
    () async => _client.getSessionApi().sessionUnrevert(
      sessionID: id,
      directory: _directory,
      workspace: _workspace,
    ),
  );

  @override
  Future<void> compactSession(
    String id, {
    required String providerID,
    required String modelID,
  }) => _guard(
    'Could not compact the session',
    () async => _client.getSessionApi().sessionSummarize(
      sessionID: id,
      directory: _directory,
      workspace: _workspace,
      sessionSummarizeRequest: sdk.SessionSummarizeRequest(
        providerID: providerID,
        modelID: modelID,
      ),
    ),
  );
}
