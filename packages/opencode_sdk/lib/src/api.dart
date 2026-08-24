//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

import 'package:dio/dio.dart';
import 'package:opencode_sdk/src/auth/api_key_auth.dart';
import 'package:opencode_sdk/src/auth/basic_auth.dart';
import 'package:opencode_sdk/src/auth/bearer_auth.dart';
import 'package:opencode_sdk/src/auth/oauth.dart';
import 'package:opencode_sdk/src/http/errors.dart';
import 'package:opencode_sdk/src/api/commands_api.dart';
import 'package:opencode_sdk/src/api/config_api.dart';
import 'package:opencode_sdk/src/api/control_api.dart';
import 'package:opencode_sdk/src/api/control_plane_api.dart';
import 'package:opencode_sdk/src/api/event_api.dart';
import 'package:opencode_sdk/src/api/events_api.dart';
import 'package:opencode_sdk/src/api/experimental_api.dart';
import 'package:opencode_sdk/src/api/file_api.dart';
import 'package:opencode_sdk/src/api/filesystem_api.dart';
import 'package:opencode_sdk/src/api/global_api.dart';
import 'package:opencode_sdk/src/api/instance_api.dart';
import 'package:opencode_sdk/src/api/integrations_api.dart';
import 'package:opencode_sdk/src/api/mcp_api.dart';
import 'package:opencode_sdk/src/api/messages_api.dart';
import 'package:opencode_sdk/src/api/models_api.dart';
import 'package:opencode_sdk/src/api/opencode_http_api_api.dart';
import 'package:opencode_sdk/src/api/permission_api.dart';
import 'package:opencode_sdk/src/api/permissions_api.dart';
import 'package:opencode_sdk/src/api/project_api.dart';
import 'package:opencode_sdk/src/api/project_copy_api.dart';
import 'package:opencode_sdk/src/api/provider_api.dart';
import 'package:opencode_sdk/src/api/providers_api.dart';
import 'package:opencode_sdk/src/api/pty_api.dart';
import 'package:opencode_sdk/src/api/question_api.dart';
import 'package:opencode_sdk/src/api/reference_api.dart';
import 'package:opencode_sdk/src/api/session_api.dart';
import 'package:opencode_sdk/src/api/session_questions_api.dart';
import 'package:opencode_sdk/src/api/sessions_api.dart';
import 'package:opencode_sdk/src/api/skills_api.dart';
import 'package:opencode_sdk/src/api/sync_api.dart';
import 'package:opencode_sdk/src/api/tui_api.dart';
import 'package:opencode_sdk/src/api/workspace_api.dart';

class OpencodeSdk {
  static const String basePath = r'http://localhost:4096';

  final Dio dio;
  OpencodeSdk({
    Dio? dio,
    String? basePathOverride,
    List<Interceptor>? interceptors,
  }) : this.dio =
           dio ??
           Dio(
             BaseOptions(
               baseUrl: basePathOverride ?? basePath,
               connectTimeout: const Duration(milliseconds: 5000),
             ),
           ) {
    if (interceptors == null) {
      this.dio.interceptors.addAll([
        OAuthInterceptor(),
        BasicAuthInterceptor(),
        BearerAuthInterceptor(),
        ApiKeyAuthInterceptor(),
      ]);
    } else {
      this.dio.interceptors.addAll(interceptors);
    }
    if (!this.dio.interceptors.any(
      (item) => item is OpenCodeApiErrorInterceptor,
    )) {
      this.dio.interceptors.add(OpenCodeApiErrorInterceptor());
    }
  }

  void setOAuthToken(String name, String token) {
    if (this.dio.interceptors.any((i) => i is OAuthInterceptor)) {
      (this.dio.interceptors.firstWhere((i) => i is OAuthInterceptor)
                  as OAuthInterceptor)
              .tokens[name] =
          token;
    }
  }

  void setBearerAuth(String name, String token) {
    if (this.dio.interceptors.any((i) => i is BearerAuthInterceptor)) {
      (this.dio.interceptors.firstWhere((i) => i is BearerAuthInterceptor)
                  as BearerAuthInterceptor)
              .tokens[name] =
          token;
    }
  }

  void setBasicAuth(String name, String username, String password) {
    if (this.dio.interceptors.any((i) => i is BasicAuthInterceptor)) {
      (this.dio.interceptors.firstWhere((i) => i is BasicAuthInterceptor)
              as BasicAuthInterceptor)
          .authInfo[name] = BasicAuthInfo(
        username,
        password,
      );
    }
  }

  void setApiKey(String name, String apiKey) {
    if (this.dio.interceptors.any((i) => i is ApiKeyAuthInterceptor)) {
      (this.dio.interceptors.firstWhere(
                    (element) => element is ApiKeyAuthInterceptor,
                  )
                  as ApiKeyAuthInterceptor)
              .apiKeys[name] =
          apiKey;
    }
  }

  /// Get CommandsApi instance, base route and serializer can be overridden by a given but be careful,
  /// by doing that all interceptors will not be executed
  CommandsApi getCommandsApi() {
    return CommandsApi(dio);
  }

  /// Get ConfigApi instance, base route and serializer can be overridden by a given but be careful,
  /// by doing that all interceptors will not be executed
  ConfigApi getConfigApi() {
    return ConfigApi(dio);
  }

  /// Get ControlApi instance, base route and serializer can be overridden by a given but be careful,
  /// by doing that all interceptors will not be executed
  ControlApi getControlApi() {
    return ControlApi(dio);
  }

  /// Get ControlPlaneApi instance, base route and serializer can be overridden by a given but be careful,
  /// by doing that all interceptors will not be executed
  ControlPlaneApi getControlPlaneApi() {
    return ControlPlaneApi(dio);
  }

  /// Get EventApi instance, base route and serializer can be overridden by a given but be careful,
  /// by doing that all interceptors will not be executed
  EventApi getEventApi() {
    return EventApi(dio);
  }

  /// Get EventsApi instance, base route and serializer can be overridden by a given but be careful,
  /// by doing that all interceptors will not be executed
  EventsApi getEventsApi() {
    return EventsApi(dio);
  }

  /// Get ExperimentalApi instance, base route and serializer can be overridden by a given but be careful,
  /// by doing that all interceptors will not be executed
  ExperimentalApi getExperimentalApi() {
    return ExperimentalApi(dio);
  }

  /// Get FileApi instance, base route and serializer can be overridden by a given but be careful,
  /// by doing that all interceptors will not be executed
  FileApi getFileApi() {
    return FileApi(dio);
  }

  /// Get FilesystemApi instance, base route and serializer can be overridden by a given but be careful,
  /// by doing that all interceptors will not be executed
  FilesystemApi getFilesystemApi() {
    return FilesystemApi(dio);
  }

  /// Get GlobalApi instance, base route and serializer can be overridden by a given but be careful,
  /// by doing that all interceptors will not be executed
  GlobalApi getGlobalApi() {
    return GlobalApi(dio);
  }

  /// Get InstanceApi instance, base route and serializer can be overridden by a given but be careful,
  /// by doing that all interceptors will not be executed
  InstanceApi getInstanceApi() {
    return InstanceApi(dio);
  }

  /// Get IntegrationsApi instance, base route and serializer can be overridden by a given but be careful,
  /// by doing that all interceptors will not be executed
  IntegrationsApi getIntegrationsApi() {
    return IntegrationsApi(dio);
  }

  /// Get McpApi instance, base route and serializer can be overridden by a given but be careful,
  /// by doing that all interceptors will not be executed
  McpApi getMcpApi() {
    return McpApi(dio);
  }

  /// Get MessagesApi instance, base route and serializer can be overridden by a given but be careful,
  /// by doing that all interceptors will not be executed
  MessagesApi getMessagesApi() {
    return MessagesApi(dio);
  }

  /// Get ModelsApi instance, base route and serializer can be overridden by a given but be careful,
  /// by doing that all interceptors will not be executed
  ModelsApi getModelsApi() {
    return ModelsApi(dio);
  }

  /// Get OpencodeHttpApiApi instance, base route and serializer can be overridden by a given but be careful,
  /// by doing that all interceptors will not be executed
  OpencodeHttpApiApi getOpencodeHttpApiApi() {
    return OpencodeHttpApiApi(dio);
  }

  /// Get PermissionApi instance, base route and serializer can be overridden by a given but be careful,
  /// by doing that all interceptors will not be executed
  PermissionApi getPermissionApi() {
    return PermissionApi(dio);
  }

  /// Get PermissionsApi instance, base route and serializer can be overridden by a given but be careful,
  /// by doing that all interceptors will not be executed
  PermissionsApi getPermissionsApi() {
    return PermissionsApi(dio);
  }

  /// Get ProjectApi instance, base route and serializer can be overridden by a given but be careful,
  /// by doing that all interceptors will not be executed
  ProjectApi getProjectApi() {
    return ProjectApi(dio);
  }

  /// Get ProjectCopyApi instance, base route and serializer can be overridden by a given but be careful,
  /// by doing that all interceptors will not be executed
  ProjectCopyApi getProjectCopyApi() {
    return ProjectCopyApi(dio);
  }

  /// Get ProviderApi instance, base route and serializer can be overridden by a given but be careful,
  /// by doing that all interceptors will not be executed
  ProviderApi getProviderApi() {
    return ProviderApi(dio);
  }

  /// Get ProvidersApi instance, base route and serializer can be overridden by a given but be careful,
  /// by doing that all interceptors will not be executed
  ProvidersApi getProvidersApi() {
    return ProvidersApi(dio);
  }

  /// Get PtyApi instance, base route and serializer can be overridden by a given but be careful,
  /// by doing that all interceptors will not be executed
  PtyApi getPtyApi() {
    return PtyApi(dio);
  }

  /// Get QuestionApi instance, base route and serializer can be overridden by a given but be careful,
  /// by doing that all interceptors will not be executed
  QuestionApi getQuestionApi() {
    return QuestionApi(dio);
  }

  /// Get ReferenceApi instance, base route and serializer can be overridden by a given but be careful,
  /// by doing that all interceptors will not be executed
  ReferenceApi getReferenceApi() {
    return ReferenceApi(dio);
  }

  /// Get SessionApi instance, base route and serializer can be overridden by a given but be careful,
  /// by doing that all interceptors will not be executed
  SessionApi getSessionApi() {
    return SessionApi(dio);
  }

  /// Get SessionQuestionsApi instance, base route and serializer can be overridden by a given but be careful,
  /// by doing that all interceptors will not be executed
  SessionQuestionsApi getSessionQuestionsApi() {
    return SessionQuestionsApi(dio);
  }

  /// Get SessionsApi instance, base route and serializer can be overridden by a given but be careful,
  /// by doing that all interceptors will not be executed
  SessionsApi getSessionsApi() {
    return SessionsApi(dio);
  }

  /// Get SkillsApi instance, base route and serializer can be overridden by a given but be careful,
  /// by doing that all interceptors will not be executed
  SkillsApi getSkillsApi() {
    return SkillsApi(dio);
  }

  /// Get SyncApi instance, base route and serializer can be overridden by a given but be careful,
  /// by doing that all interceptors will not be executed
  SyncApi getSyncApi() {
    return SyncApi(dio);
  }

  /// Get TuiApi instance, base route and serializer can be overridden by a given but be careful,
  /// by doing that all interceptors will not be executed
  TuiApi getTuiApi() {
    return TuiApi(dio);
  }

  /// Get WorkspaceApi instance, base route and serializer can be overridden by a given but be careful,
  /// by doing that all interceptors will not be executed
  WorkspaceApi getWorkspaceApi() {
    return WorkspaceApi(dio);
  }
}
