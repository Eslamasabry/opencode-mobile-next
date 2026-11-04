import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/git/github_device_flow.dart';

import 'git_test_support.dart';

/// Answers GitHub's three endpoints from queues; records what was sent.
class _GitHub implements HttpClientAdapter {
  final codeAnswers = <Object>[];
  final tokenAnswers = <Object>[];
  Object userAnswer = {'login': 'octocat'};
  List<Object> reposAnswer = const [
    {
      'full_name': 'octocat/alpha',
      'html_url': 'https://github.com/octocat/alpha',
      'clone_url': 'https://github.com/octocat/alpha.git',
      'private': false,
      'default_branch': 'main',
    },
    {
      'full_name': 'octocat/beta',
      'html_url': 'https://github.com/octocat/beta',
      'clone_url': 'https://github.com/octocat/beta.git',
      'private': true,
      'default_branch': 'dev',
    },
  ];
  int userStatus = 200;
  final requests = <({String method, String url, String body, Map headers})>[];

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    final body = requestStream == null
        ? ''
        : utf8.decode(await requestStream.expand((c) => c).toList());
    requests.add((
      method: options.method,
      url: options.uri.toString(),
      body: body,
      headers: options.headers,
    ));
    Object answer;
    var status = 200;
    // The repo list adds query parameters; match on the address without them.
    switch ('${options.uri.origin}${options.uri.path}') {
      case gitHubDeviceCodeUrl:
        answer = codeAnswers.removeAt(0);
      case gitHubAccessTokenUrl:
        answer = tokenAnswers.removeAt(0);
      case gitHubUserUrl:
        answer = userAnswer;
        status = userStatus;
      case gitHubUserReposUrl:
        answer = reposAnswer;
      default:
        throw StateError('unexpected ${options.uri}');
    }
    if (answer is DioExceptionType) {
      throw DioException(requestOptions: options, type: answer);
    }
    return ResponseBody.fromString(
      jsonEncode(answer),
      status,
      headers: {
        Headers.contentTypeHeader: ['application/json'],
      },
    );
  }

  @override
  void close({bool force = false}) {}
}

const _code = {
  'device_code': 'dc_FAKE_device_code_123',
  'user_code': 'WDJB-MJHT',
  'verification_uri': 'https://github.com/login/device',
  'expires_in': 900,
  'interval': 5,
};

void main() {
  late _GitHub github;
  late List<Duration> sleeps;
  late DateTime now;

  GitHubDeviceFlow flow() {
    final dio = Dio()..httpClientAdapter = github;
    return GitHubDeviceFlow(
      dio: dio,
      sleep: (d) async {
        sleeps.add(d);
        now = now.add(d);
      },
      now: () => now,
    );
  }

  setUp(() {
    github = _GitHub();
    sleeps = [];
    now = DateTime.utc(2026, 10, 19, 2);
  });

  test('success: code, pending, slow_down, token, login', () async {
    github.codeAnswers.add(_code);
    github.tokenAnswers.addAll([
      {'error': 'authorization_pending'},
      {'error': 'slow_down', 'interval': 10},
      {'error': 'authorization_pending'},
      {
        'access_token': fakeGitHubToken,
        'token_type': 'bearer',
        'scope': 'repo',
      },
    ]);
    final f = flow();
    final code = await f.requestCode();
    expect(code.userCode, 'WDJB-MJHT');
    expect(code.verificationUri.toString(), 'https://github.com/login/device');
    expect(code.expiresAt, now.add(const Duration(seconds: 900)));
    expect(code.toString(), isNot(contains('dc_FAKE')));

    final credential = await f.pollForToken(code);
    expect(credential.accessToken, fakeGitHubToken);
    expect(credential.scope, 'repo');
    expect(credential.toString(), isNot(contains(fakeGitHubToken)));
    // 5 s, 5 s, then GitHub's slow_down interval (10 s) from then on.
    expect(sleeps, const [
      Duration(seconds: 5),
      Duration(seconds: 5),
      Duration(seconds: 10),
      Duration(seconds: 10),
    ]);

    expect(await f.fetchLogin(credential.accessToken), 'octocat');

    final first = github.requests.first;
    expect(first.method, 'POST');
    expect(first.body, contains('client_id=Ov23liG1iMtZQSsgpXz4'));
    expect(first.body, contains('scope=repo'));
    expect(first.body, isNot(contains('secret')));
    final poll = github.requests[1];
    expect(poll.body, contains('device_code=dc_FAKE_device_code_123'));
    expect(
      poll.body,
      contains(
        'grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Adevice_code',
      ),
    );
    final user = github.requests.last;
    expect(user.headers['Authorization'], 'Bearer $fakeGitHubToken');
  });

  test(
    'user repos are listed with remote URLs ready for a Git remote',
    () async {
      final repos = await flow().fetchUserRepositories(fakeGitHubToken);
      expect(repos.map((r) => r.fullName), ['octocat/alpha', 'octocat/beta']);
      expect(repos.first.remoteUrl, 'https://github.com/octocat/alpha.git');
      expect(repos.last.remoteUrl, 'https://github.com/octocat/beta.git');
      final request = github.requests.last;
      expect(request.method, 'GET');
      expect(request.url, contains(gitHubUserReposUrl));
      expect(request.headers['Authorization'], 'Bearer $fakeGitHubToken');
    },
  );

  test('slow_down without an interval adds five seconds', () async {
    github.codeAnswers.add({..._code, 'interval': 2});
    github.tokenAnswers.addAll([
      {'error': 'slow_down'},
      {'access_token': fakeGitHubToken},
    ]);
    final f = flow();
    await f.pollForToken(await f.requestCode());
    expect(sleeps, const [Duration(seconds: 2), Duration(seconds: 7)]);
  });

  test('expired_token ends the wait', () async {
    github.codeAnswers.add(_code);
    github.tokenAnswers.add({'error': 'expired_token'});
    final f = flow();
    final code = await f.requestCode();
    await expectLater(
      f.pollForToken(code),
      throwsA(
        isA<GitHubSignInException>().having(
          (e) => e.problem,
          'problem',
          GitHubSignInProblem.expired,
        ),
      ),
    );
  });

  test('the code running out locally ends the wait too', () async {
    github.codeAnswers.add({..._code, 'expires_in': 8, 'interval': 5});
    github.tokenAnswers.add({'error': 'authorization_pending'});
    final f = flow();
    final code = await f.requestCode();
    await expectLater(
      f.pollForToken(code),
      throwsA(
        isA<GitHubSignInException>().having(
          (e) => e.problem,
          'problem',
          GitHubSignInProblem.expired,
        ),
      ),
    );
    expect(github.tokenAnswers, isEmpty);
  });

  test('access_denied is typed', () async {
    github.codeAnswers.add(_code);
    github.tokenAnswers.add({'error': 'access_denied'});
    final f = flow();
    await expectLater(
      f.pollForToken(await f.requestCode()),
      throwsA(
        isA<GitHubSignInException>().having(
          (e) => e.problem,
          'problem',
          GitHubSignInProblem.denied,
        ),
      ),
    );
  });

  test('device flow switched off on the app is typed', () async {
    github.codeAnswers.add({'error': 'device_flow_disabled'});
    await expectLater(
      flow().requestCode(),
      throwsA(
        isA<GitHubSignInException>().having(
          (e) => e.problem,
          'problem',
          GitHubSignInProblem.deviceFlowDisabled,
        ),
      ),
    );
  });

  test(
    'a network blip while polling is retried; three in a row end it',
    () async {
      github.codeAnswers.add(_code);
      github.tokenAnswers.addAll([
        DioExceptionType.connectionError,
        {'access_token': fakeGitHubToken},
      ]);
      final f = flow();
      expect(
        (await f.pollForToken(await f.requestCode())).accessToken,
        fakeGitHubToken,
      );

      github.codeAnswers.add(_code);
      github.tokenAnswers.addAll(
        List.filled(3, DioExceptionType.connectionError),
      );
      await expectLater(
        f.pollForToken(await f.requestCode()),
        throwsA(
          isA<GitHubSignInException>().having(
            (e) => e.problem,
            'problem',
            GitHubSignInProblem.network,
          ),
        ),
      );
    },
  );

  test('cancelling stops before the next poll', () async {
    github.codeAnswers.add(_code);
    var cancelled = false;
    final f = flow();
    final code = await f.requestCode();
    cancelled = true;
    await expectLater(
      f.pollForToken(code, cancelled: () => cancelled),
      throwsA(
        isA<GitHubSignInException>().having(
          (e) => e.problem,
          'problem',
          GitHubSignInProblem.cancelled,
        ),
      ),
    );
    expect(github.requests, hasLength(1));
  });

  test('a rejected token on GET /user is typed', () async {
    github.userStatus = 401;
    github.userAnswer = {'message': 'Bad credentials'};
    await expectLater(
      flow().fetchLogin(fakeGitHubToken),
      throwsA(
        isA<GitHubSignInException>().having(
          (e) => e.problem,
          'problem',
          GitHubSignInProblem.tokenRejected,
        ),
      ),
    );
  });

  test(
    'expiring tokens: refresh rotates the pair and keeps the login',
    () async {
      github.tokenAnswers.add({
        'access_token': 'ghu_NEWaccess0123456789',
        'refresh_token': 'ghr_NEWrefresh0123456789',
        'expires_in': 28800,
        'refresh_token_expires_in': 15897600,
      });
      final refreshed = await flow().refresh(
        const GitHubCredential(
          accessToken: 'ghu_OLDaccess0123456789',
          login: 'octocat',
          refreshToken: 'ghr_OLDrefresh0123456789',
        ),
      );
      expect(refreshed.accessToken, 'ghu_NEWaccess0123456789');
      expect(refreshed.refreshToken, 'ghr_NEWrefresh0123456789');
      expect(refreshed.login, 'octocat');
      expect(refreshed.expiresAt, now.add(const Duration(hours: 8)));
      expect(github.requests.single.body, contains('grant_type=refresh_token'));
    },
  );
}
