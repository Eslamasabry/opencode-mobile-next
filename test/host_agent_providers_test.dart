import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/host_agent_providers.dart';

void main() {
  HostAgentProvider provider({
    HostAgentProviderAvailability availability =
        HostAgentProviderAvailability.ready,
    HostAgentProviderHiddenReason? hiddenReason,
    HostAgentLoginState loginState = HostAgentLoginState.ready,
    HostAgentResumeSupport resumeSupport = HostAgentResumeSupport.loadSession,
    String id = 'gemini',
    List<HostAgentModel> models = const [],
  }) => HostAgentProvider(
    id: id,
    displayName: 'Host agent',
    availability: availability,
    hiddenReason: hiddenReason,
    loginState: loginState,
    resumeSupport: resumeSupport,
    models: models,
  );

  test('unknown login or restoration cannot become ready', () {
    expect(
      () => provider(loginState: HostAgentLoginState.unknown),
      throwsArgumentError,
    );
    expect(
      () => provider(loginState: HostAgentLoginState.needsHostSignIn),
      throwsArgumentError,
    );
    for (final support in [
      HostAgentResumeSupport.unknown,
      HostAgentResumeSupport.unsupported,
    ]) {
      expect(() => provider(resumeSupport: support), throwsArgumentError);
    }
    expect(provider().selectable, isTrue);
    expect(
      provider(resumeSupport: HostAgentResumeSupport.listAndLoad).selectable,
      isTrue,
    );
  });

  test('sign-in cannot disguise missing restoration proof', () {
    expect(
      () => provider(
        availability: HostAgentProviderAvailability.needsHostSignIn,
        loginState: HostAgentLoginState.needsHostSignIn,
        resumeSupport: HostAgentResumeSupport.unknown,
      ),
      throwsArgumentError,
    );
    final needsSignIn = provider(
      availability: HostAgentProviderAvailability.needsHostSignIn,
      loginState: HostAgentLoginState.needsHostSignIn,
    );
    expect(needsSignIn.selectable, isFalse);
    expect(needsSignIn.reason, 'Sign in on your computer first.');
  });

  test('hidden and checking agents remain unavailable despite known login', () {
    final hidden = provider(
      availability: HostAgentProviderAvailability.hidden,
      hiddenReason: HostAgentProviderHiddenReason.disabled,
    );
    final checking = provider(
      availability: HostAgentProviderAvailability.checking,
    );
    expect(hidden.selectable, isFalse);
    expect(checking.selectable, isFalse);
    expect(
      () => provider(availability: HostAgentProviderAvailability.hidden),
      throwsArgumentError,
    );
    expect(
      () => provider(hiddenReason: HostAgentProviderHiddenReason.disabled),
      throwsArgumentError,
    );
  });

  test('catalog and model lists cannot be changed by caller mutation', () {
    final models = <HostAgentModel>[
      const HostAgentModel(id: 'model-1', name: 'Model one'),
    ];
    final ready = provider(models: models);
    models.clear();
    expect(ready.models.single.id, 'model-1');
    expect(ready.models.clear, throwsUnsupportedError);

    final source = <HostAgentProvider>[
      ready,
      provider(availability: HostAgentProviderAvailability.checking),
    ];
    final catalog = HostAgentProviderCatalog(providers: source);
    source.clear();
    expect(catalog.providers.length, 2);
    expect(catalog.selectable.single, same(ready));
    expect(catalog.providers.clear, throwsUnsupportedError);
    expect(catalog.selectable.clear, throwsUnsupportedError);
  });

  test('sign-in details use only fixed app commands', () {
    expect(provider().hostSignInCommand, 'gemini');
    expect(provider(id: 'omp').hostSignInCommand, 'omp');
    expect(provider(id: 'fx').hostSignInCommand, 'fx login');
    expect(provider(id: 'unknown-agent').hostSignInCommand, isNull);
    expect(provider(id: 'fx; arbitrary command').hostSignInCommand, isNull);
    expect(
      provider().signInMessage,
      'Sign in on your computer: run gemini there, then check again.',
    );
    expect(
      provider(id: 'omp').signInMessage,
      'Run omp on your computer, use /login to sign in, then check again.',
    );
  });

  test('permission choices expose fixed once-only labels and copied lists', () {
    const allow = HostAgentPermissionChoice(
      actionId: 'host-action-allow',
      behavior: HostAgentPermissionBehavior.allowOnce,
    );
    const reject = HostAgentPermissionChoice(
      actionId: 'host-action-reject',
      behavior: HostAgentPermissionBehavior.rejectOnce,
    );
    final choices = [allow, reject];
    final request = HostAgentPermissionRequest(
      requestId: 'request-1',
      sessionId: 'session-1',
      choices: choices,
    );
    choices.clear();
    expect(request.choices.map((choice) => choice.label), [
      'Allow once',
      'Reject once',
    ]);
    expect(request.choices.clear, throwsUnsupportedError);
    expect(
      () => HostAgentPermissionRequest(
        requestId: 'request-2',
        sessionId: 'session-1',
        choices: [allow, allow],
      ),
      throwsArgumentError,
    );
    expect(
      () => HostAgentPermissionRequest(
        requestId: 'request-3',
        sessionId: 'session-1',
        choices: [
          const HostAgentPermissionChoice(
            actionId: ' ',
            behavior: HostAgentPermissionBehavior.allowOnce,
          ),
        ],
      ),
      throwsArgumentError,
    );
  });
}
