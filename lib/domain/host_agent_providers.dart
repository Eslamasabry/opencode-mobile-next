/// Host agent availability for the pilot. A provider is not selectable until
/// sign-in and conversation restoration have both been verified on the host.
enum HostAgentProviderAvailability { ready, needsHostSignIn, checking, hidden }

enum HostAgentProviderHiddenReason {
  resumeUnverified,
  resumeUnsupported,
  notPilot,
  unavailable,
  disabled,
}

enum HostAgentLoginState { unknown, ready, needsHostSignIn }

enum HostAgentResumeSupport { unknown, unsupported, loadSession, listAndLoad }

final class HostAgentModel {
  const HostAgentModel({required this.id, required this.name});

  final String id;
  final String name;
}

/// Protocol-neutral host truth, not an inference from an installed executable
/// or a successful connection. Wire adapters must leave missing proof unknown.
final class HostAgentProvider {
  HostAgentProvider({
    required this.id,
    required this.displayName,
    required this.availability,
    this.hiddenReason,
    required this.loginState,
    required this.resumeSupport,
    List<HostAgentModel> models = const [],
  }) : models = List.unmodifiable(models) {
    if (availability == HostAgentProviderAvailability.hidden) {
      if (hiddenReason == null) {
        throw ArgumentError('A hidden host agent needs a reason.');
      }
    } else if (hiddenReason != null) {
      throw ArgumentError('Only a hidden host agent can have a hidden reason.');
    }
    if (availability == HostAgentProviderAvailability.ready &&
        (loginState != HostAgentLoginState.ready || !_canResume)) {
      throw ArgumentError(
        'A ready host agent needs verified sign-in and conversation restoration.',
      );
    }
    if (availability == HostAgentProviderAvailability.needsHostSignIn &&
        (loginState != HostAgentLoginState.needsHostSignIn || !_canResume)) {
      throw ArgumentError(
        'A host sign-in request needs verified conversation restoration.',
      );
    }
  }

  final String id;
  final String displayName;
  final HostAgentProviderAvailability availability;
  final HostAgentProviderHiddenReason? hiddenReason;
  final HostAgentLoginState loginState;
  final HostAgentResumeSupport resumeSupport;
  final List<HostAgentModel> models;

  bool get _canResume =>
      resumeSupport == HostAgentResumeSupport.loadSession ||
      resumeSupport == HostAgentResumeSupport.listAndLoad;

  bool get selectable =>
      availability == HostAgentProviderAvailability.ready &&
      loginState == HostAgentLoginState.ready &&
      _canResume;

  /// App-authored copy. Never substitute raw host errors or agent output here.
  String get reason => switch (availability) {
    HostAgentProviderAvailability.ready => 'Ready',
    HostAgentProviderAvailability.needsHostSignIn =>
      'Sign in on your computer first.',
    HostAgentProviderAvailability.checking => 'Checking your computer.',
    HostAgentProviderAvailability.hidden => switch (hiddenReason!) {
      HostAgentProviderHiddenReason.resumeUnverified =>
        'Conversation restoration has not been checked.',
      HostAgentProviderHiddenReason.resumeUnsupported =>
        'This agent cannot restore a conversation.',
      HostAgentProviderHiddenReason.notPilot =>
        'This agent is not available in this preview.',
      HostAgentProviderHiddenReason.unavailable =>
        'This agent is not available on your computer.',
      HostAgentProviderHiddenReason.disabled =>
        'This agent is turned off on your computer.',
    },
  };

  /// Technical instructions belong under Details. These are fixed commands
  /// authored by the app, never commands supplied by the host or agent.
  String? get hostSignInCommand => switch (id) {
    'gemini' => 'gemini',
    'omp' || 'omp-acp' => 'omp',
    'fx' => 'fx login',
    _ => null,
  };

  String get signInMessage => switch (id) {
    'gemini' => 'Sign in on your computer: run gemini there, then check again.',
    'omp' || 'omp-acp' =>
      'Run omp on your computer, use /login to sign in, then check again.',
    _ => 'Sign in to the agent on your computer, then check again.',
  };
}

final class HostAgentProviderCatalog {
  HostAgentProviderCatalog({required List<HostAgentProvider> providers})
    : providers = List.unmodifiable(providers);

  final List<HostAgentProvider> providers;

  List<HostAgentProvider> get selectable =>
      List.unmodifiable(providers.where((provider) => provider.selectable));
}

/// Existing routes continue through their established gateway contract. A pilot
/// needs verified restoration before dispatch, even when a handle is present.
enum HostAgentContinuationState {
  existingRoute,
  resumeUnverified,
  missingHandle,
}

final class HostAgentContinuation {
  const HostAgentContinuation({
    required this.sessionId,
    required this.providerId,
    required this.state,
  });

  final String sessionId;
  final String providerId;
  final HostAgentContinuationState state;

  bool get blocked => state != HostAgentContinuationState.existingRoute;

  String get reason => switch (state) {
    HostAgentContinuationState.existingRoute => '',
    HostAgentContinuationState.resumeUnverified =>
      'This conversation cannot continue until restoration is checked on your computer.',
    HostAgentContinuationState.missingHandle =>
      'This agent cannot reopen this conversation. Check it on your computer.',
  };
}

abstract interface class HostAgentProviderGateway {
  /// Scoped restart state; callers do not inspect native persistence handles.
  Future<HostAgentContinuation> loadHostAgentContinuation(String sessionId);

  Future<HostAgentProviderCatalog> loadHostAgentProviders({
    bool refresh = false,
  });
}

enum HostAgentPermissionBehavior { allowOnce, rejectOnce }

/// The action ID is opaque and only sent back to the same host. Labels and
/// behavior are authored here; arbitrary agent permission copy is not exposed.
final class HostAgentPermissionChoice {
  const HostAgentPermissionChoice({
    required this.actionId,
    required this.behavior,
  });

  final String actionId;
  final HostAgentPermissionBehavior behavior;

  String get label => switch (behavior) {
    HostAgentPermissionBehavior.allowOnce => 'Allow once',
    HostAgentPermissionBehavior.rejectOnce => 'Reject once',
  };
}

final class HostAgentPermissionRequest {
  HostAgentPermissionRequest({
    required this.requestId,
    required this.sessionId,
    required List<HostAgentPermissionChoice> choices,
  }) : choices = List.unmodifiable(choices) {
    final actionIds = <String>{};
    for (final choice in this.choices) {
      if (choice.actionId.trim().isEmpty || !actionIds.add(choice.actionId)) {
        throw ArgumentError('Permission choices need unique, nonempty IDs.');
      }
    }
  }

  final String requestId;
  final String sessionId;
  final List<HostAgentPermissionChoice> choices;
}

abstract interface class HostAgentPermissionGateway {
  Future<List<HostAgentPermissionRequest>> pendingHostAgentPermissions();

  /// Select only an action offered by the pending request. A null selection
  /// rejects the request, including cancellation or dismissal by the user.
  Future<void> respondHostAgentPermission(
    String requestId, {
    String? selectedActionId,
  });
}
