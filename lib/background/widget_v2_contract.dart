import 'package:flutter/services.dart';

/// The launcher carries identifiers, never credentials or drafts. Text exists
/// only for the duration of an explicit overlay submission.
class WidgetAction {
  const WidgetAction({
    required this.id,
    required this.action,
    required this.profileID,
    required this.scopeToken,
    this.sessionID = '',
    this.requestID = '',
    this.runID = '',
    this.gateID = '',
    this.lastProjectID = '',
    this.expectedStartedAt = 0,
    this.text = '',
  });
  final String id, action, profileID, scopeToken, sessionID, requestID;
  final String runID, gateID, lastProjectID, text;
  final int expectedStartedAt;
  static const actions = {
    'allowOnce',
    'reject',
    'stop',
    'steer',
    'voice',
    'approvePlan',
    'resume',
  };
  static WidgetAction? fromPlatform(Object? value) {
    if (value is! Map) return null;
    String string(String key) =>
        value[key] is String ? value[key] as String : '';
    final id = string('id'), action = string('action');
    final profile = string('profileID'), scope = string('scopeToken');
    if (id.isEmpty ||
        id.length > 200 ||
        !actions.contains(action) ||
        profile.isEmpty ||
        profile.length > 200 ||
        scope.isEmpty ||
        scope.length > 200) {
      return null;
    }
    for (final key in [
      'sessionID',
      'requestID',
      'runID',
      'gateID',
      'lastProjectID',
    ]) {
      if (string(key).length > 512) return null;
    }
    if (string('text').length > 8000) return null;
    final started = value['expectedStartedAt'];
    if (started != null && (started is! int || started < 0)) return null;
    return WidgetAction(
      id: id,
      action: action,
      profileID: profile,
      scopeToken: scope,
      sessionID: string('sessionID'),
      requestID: string('requestID'),
      runID: string('runID'),
      gateID: string('gateID'),
      lastProjectID: string('lastProjectID'),
      expectedStartedAt: started is int ? started : 0,
      text: string('text').trim(),
    );
  }
}

class WidgetActionResult {
  const WidgetActionResult(this.id, this.action, this.status, [this.error]);
  final String id, action, status;
  final String? error;
  Map<String, Object?> toJson() => {
    'id': id,
    'action': action,
    'status': status,
    if (error != null) 'error': error,
  };
}

/// Port for the builder-owned transparent Flutter entrypoint. Native binds all
/// routing identities; submitAction deliberately accepts text only.
abstract final class WidgetOverlayPort {
  static const channel = MethodChannel('oc/widgets');
  static Future<Map<String, dynamic>> getLaunchArguments() async =>
      await channel.invokeMapMethod<String, dynamic>('getLaunchArguments') ??
      {};
  static Future<void> saveStyle({
    required String background,
    required String accent,
  }) async {
    if (!const {'solid', 'tinted', 'transparent'}.contains(background) ||
        !const {
          'wallpaper',
          'app',
          'green',
          'teal',
          'blue',
          'amber',
        }.contains(accent)) {
      throw ArgumentError('Invalid widget style');
    }
    await channel.invokeMethod<void>('saveStyle', {
      'background': background,
      'accent': accent,
    });
  }

  static Future<void> saveProject(String project) async {
    if (project.length > 200) throw ArgumentError('Invalid project');
    await channel.invokeMethod<void>('saveProject', {'project': project});
  }

  static Future<Map<String, dynamic>> submitAction(String text) async {
    if (text.trim().isEmpty || text.length > 8000) {
      throw ArgumentError('Invalid message');
    }
    return await channel.invokeMapMethod<String, dynamic>('submitAction', {
          'text': text.trim(),
        }) ??
        {};
  }

  static Future<String> dictate({String? languageTag}) async {
    if (languageTag != null &&
        !RegExp(r'^[A-Za-z]{2,8}(-[A-Za-z0-9]{1,8})*$').hasMatch(languageTag)) {
      throw ArgumentError('Invalid language tag');
    }
    final result = await channel.invokeMapMethod<String, dynamic>('dictate', {
      'languageTag': ?languageTag,
    });
    return result?['text'] is String ? result!['text'] as String : '';
  }

  static Future<void> close() => channel.invokeMethod<void>('close');
}
