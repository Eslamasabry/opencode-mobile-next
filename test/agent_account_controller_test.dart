import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/agent_account.dart';
import 'package:opencode_mobile/state/agent_account.dart';

import 'support/account_fakes.dart';

void main() {
  late FakeAccountSession session;
  late AgentAccountController controller;
  setUp(() {
    session = FakeAccountSession();
    controller = AgentAccountController(session);
  });
  tearDown(() {
    controller.dispose();
  });

  test('refresh never starts sign-in and reported zero is preserved', () async {
    await controller.refresh();
    expect(controller.canSignIn, isTrue);
    expect(session.starts, 0);
    expect(session.limitReads, 0);
    session.account = fixtureSignedIn;
    session.tokens = const AccountTokenUsage(lifetimeTokens: 0);
    session.buckets = [
      const AccountRateBucket(null, AccountRateWindow(0, null, null), null),
    ];
    await controller.refresh();
    expect(controller.canSignIn, isFalse);
    expect(controller.usage!.lifetimeTokens, 0);
    expect(controller.usage!.peakDailyTokens, isNull);
    expect(controller.limits!.single.primary!.usedPercent, 0);
    expect(session.starts, 0);
  });

  test(
    'independent unavailable usage leaves fresh account and limits',
    () async {
      session.account = fixtureSignedIn;
      session.buckets = [
        const AccountRateBucket(
          'Codex',
          AccountRateWindow(25, 300, null),
          null,
        ),
      ];
      session.onUsage = () async =>
          throw const AgentAccountException(AgentAccountFailure.unavailable);
      await controller.refresh();
      expect(controller.status, AccountPanelStatus.ready);
      expect(controller.account!.email, fixtureSignedIn.email);
      expect(controller.limits!.single.primary!.usedPercent, 25);
      expect(controller.usage, isNull);
      expect(controller.metricsLoading, isFalse);
      session.onUsage = null;
      session.tokens = const AccountTokenUsage(peakDailyTokens: 42);
      session.onLimits = () async => throw StateError('fixture');
      await controller.refresh();
      expect(controller.limits, isNull);
      expect(controller.usage!.peakDailyTokens, 42);
    },
  );

  test(
    'unsupported runtime and API-key auth do not probe subscription metrics',
    () async {
      session.supported = false;
      await controller.refresh();
      expect(controller.status, AccountPanelStatus.unavailable);
      expect(session.reads, 0);
      expect(controller.canSignIn, isFalse);
      session.supported = true;
      session.account = const AgentAccount(
        type: 'apiKey',
        requiresSignIn: true,
      );
      await controller.refresh();
      expect(controller.account!.signedIn, isTrue);
      expect(session.limitReads, 0);
      expect(session.usageReads, 0);
    },
  );

  test(
    'owned completion before start response never restores the code',
    () async {
      await controller.refresh();
      final held = Completer<AccountDeviceCode>();
      session.onStart = () => held.future;
      final pending = controller.signIn();
      expect(controller.loginStatus, AccountLoginStatus.starting);
      session.account = fixtureSignedIn;
      session.notifications.add(
        const AccountEvent(AccountEventKind.loginCompleted, success: true),
      );
      held.complete(fixtureCode);
      await pending;
      await pumpEventQueue();
      expect(controller.deviceCode, isNull);
      expect(controller.loginStatus, AccountLoginStatus.completed);
      expect(controller.account!.signedIn, isTrue);
    },
  );

  test(
    'cancel while starting suppresses the late code until owned receipt settles',
    () async {
      await controller.refresh();
      final held = Completer<AccountDeviceCode>();
      session.onStart = () => held.future;
      session.onCancel = () async => false;
      final pending = controller.signIn();
      await controller.cancel();
      expect(controller.loginStatus, AccountLoginStatus.uncertain);
      expect(controller.canSignIn, isFalse);
      held.complete(fixtureCode);
      await pending;
      expect(controller.deviceCode, isNull);
      session.notifications.add(
        const AccountEvent(AccountEventKind.loginCancelled, success: true),
      );
      await pumpEventQueue();
      expect(controller.loginStatus, AccountLoginStatus.cancelled);
      expect(controller.canSignIn, isTrue);
    },
  );

  test(
    'failed cancellation stays uncertain and allows only explicit retry',
    () async {
      await controller.refresh();
      await controller.signIn();
      session.onCancel = () async => throw StateError('fixture');
      await controller.cancel();
      expect(controller.deviceCode, isNull);
      expect(controller.loginStatus, AccountLoginStatus.uncertain);
      expect(controller.canCancel, isTrue);
      await controller.signIn();
      expect(session.starts, 1);
      session.onCancel = () async => true;
      await controller.cancel();
      expect(controller.loginStatus, AccountLoginStatus.cancelled);
      expect(session.cancels, 2);
    },
  );

  test(
    'account change clears stale metrics before delayed read completes',
    () async {
      session.account = fixtureSignedIn;
      final held = Completer<List<AccountRateBucket>>();
      session.onLimits = () => held.future;
      final oldRead = controller.refresh();
      await pumpEventQueue();
      expect(controller.account!.signedIn, isTrue);
      session.account = fixtureSignedOut;
      session.notifications.add(const AccountEvent(AccountEventKind.changed));
      expect(controller.account, isNull);
      expect(controller.limits, isNull);
      expect(controller.usage, isNull);
      held.complete([
        const AccountRateBucket(
          'Old account',
          AccountRateWindow(80, 60, null),
          null,
        ),
      ]);
      await oldRead;
      await pumpEventQueue();
      expect(controller.account!.signedIn, isFalse);
      expect(controller.limits, isNull);
      expect(session.usageReads, 0);
    },
  );

  test(
    'account change while waiting clears code and cancels only this session',
    () async {
      await controller.refresh();
      await controller.signIn();
      final held = Completer<bool>();
      session.onCancel = () => held.future;
      session.account = fixtureSignedIn;
      session.notifications.add(const AccountEvent(AccountEventKind.changed));
      expect(controller.deviceCode, isNull);
      expect(session.cancels, 1);
      held.complete(false);
      await pumpEventQueue();
      expect(controller.loginStatus, AccountLoginStatus.uncertain);
      expect(controller.account!.signedIn, isTrue);
      expect(session.starts, 1);
    },
  );

  test(
    'disconnect invalidates in-flight login and reads with no replay',
    () async {
      await controller.refresh();
      final held = Completer<AccountDeviceCode>();
      session.onStart = () => held.future;
      final pending = controller.signIn();
      session.active = false;
      session.notifications.add(
        const AccountEvent(AccountEventKind.disconnected),
      );
      held.complete(fixtureCode);
      await pending;
      expect(controller.status, AccountPanelStatus.disconnected);
      expect(controller.deviceCode, isNull);
      expect(controller.account, isNull);
      expect(controller.canSignIn, isFalse);
      expect(controller.canCancel, isFalse);
      await controller.refresh();
      expect(session.starts, 1);
    },
  );

  test('disposing during login never publishes a late code', () async {
    await controller.refresh();
    final held = Completer<AccountDeviceCode>();
    session.onStart = () => held.future;
    final pending = controller.signIn();
    controller.dispose();
    // Replace the disposed controller so shared teardown owns a live instance.
    final disposed = controller;
    controller = AgentAccountController(FakeAccountSession());
    held.complete(fixtureCode);
    await pending;
    expect(session.closed, isTrue);
    expect(disposed.deviceCode, isNull);
  });
}
