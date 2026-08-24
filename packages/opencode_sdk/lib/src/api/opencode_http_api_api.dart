//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

import 'dart:async';

// ignore: unused_import
import 'dart:convert';
import 'package:opencode_sdk/src/deserialize.dart';
import 'package:dio/dio.dart';
import 'package:opencode_sdk/src/http/errors.dart';
import 'package:opencode_sdk/src/http/wire.dart';

import 'package:opencode_sdk/src/model/invalid_request_error.dart';
import 'package:opencode_sdk/src/model/location_info.dart';
import 'package:opencode_sdk/src/model/unauthorized_error.dart';
import 'package:opencode_sdk/src/model/v2_agent_list200_response.dart';
import 'package:opencode_sdk/src/model/v2_credential_update_request.dart';
import 'package:opencode_sdk/src/model/v2_health_get200_response.dart';

class OpencodeHttpApiApi {
  final Dio _dio;

  const OpencodeHttpApiApi(this._dio);

  /// List agents
  /// Retrieve currently registered agents.
  ///
  /// Parameters:
  /// * [locationLeftSquareBracketDirectoryRightSquareBracket]
  /// * [locationLeftSquareBracketWorkspaceRightSquareBracket]
  /// * [cancelToken] - A [CancelToken] that can be used to cancel the operation
  /// * [headers] - Can be used to add additional headers to the request
  /// * [extras] - Can be used to add flags to the request
  /// * [validateStatus] - A [ValidateStatus] callback that can be used to determine request success based on the HTTP status of the response
  /// * [onSendProgress] - A [ProgressCallback] that can be used to get the send progress
  /// * [onReceiveProgress] - A [ProgressCallback] that can be used to get the receive progress
  ///
  /// Returns a [Future] containing a [Response] with a [V2AgentList200Response] as data
  /// Throws [DioException] if API call or serialization fails
  Future<Response<V2AgentList200Response>> v2AgentList({
    String? locationLeftSquareBracketDirectoryRightSquareBracket,
    String? locationLeftSquareBracketWorkspaceRightSquareBracket,
    CancelToken? cancelToken,
    Map<String, dynamic>? headers,
    Map<String, dynamic>? extra,
    ValidateStatus? validateStatus,
    ProgressCallback? onSendProgress,
    ProgressCallback? onReceiveProgress,
  }) async {
    const _operationId = r'v2.agent.list';
    final _path = r'/api/agent';
    final _options = Options(
      method: r'GET',
      headers: <String, dynamic>{...?headers},
      extra: <String, dynamic>{
        'secure': <Map<String, String>>[],
        ...?extra,
        'operationId': _operationId,
      },
      validateStatus: validateStatus,
    );

    final _queryParameters = <String, dynamic>{
      if (locationLeftSquareBracketDirectoryRightSquareBracket != null)
        r'location[directory]': serializeOpenCodeQueryParameter(
          locationLeftSquareBracketDirectoryRightSquareBracket,
        ),
      if (locationLeftSquareBracketWorkspaceRightSquareBracket != null)
        r'location[workspace]': serializeOpenCodeQueryParameter(
          locationLeftSquareBracketWorkspaceRightSquareBracket,
        ),
    };

    late Response<Object> _response;
    try {
      _response = await requestOpenCode<Object>(
        _dio,
        _path,
        includeBody: false,
        options: _options,
        queryParameters: _queryParameters,
        cancelToken: cancelToken,
        onSendProgress: onSendProgress,
        onReceiveProgress: onReceiveProgress,
      );
    } on DioException catch (error) {
      rethrowOpenCodeApiException(error, operationId: _operationId);
    }
    throwIfOpenCodeApiError(_response, operationId: _operationId);

    V2AgentList200Response? _responseData;

    try {
      final rawData = _response.data;
      _responseData = rawData == null
          ? null
          : deserialize<V2AgentList200Response, V2AgentList200Response>(
              rawData,
              'V2AgentList200Response',
              growable: true,
            );
    } catch (error, stackTrace) {
      throw DioException(
        requestOptions: _response.requestOptions,
        response: _response,
        type: DioExceptionType.unknown,
        error: error,
        stackTrace: stackTrace,
      );
    }

    return Response<V2AgentList200Response>(
      data: _responseData,
      headers: _response.headers,
      isRedirect: _response.isRedirect,
      requestOptions: _response.requestOptions,
      redirects: _response.redirects,
      statusCode: _response.statusCode,
      statusMessage: _response.statusMessage,
      extra: _response.extra,
    );
  }

  /// Remove credential
  /// Remove a stored integration credential.
  ///
  /// Parameters:
  /// * [credentialID]
  /// * [locationLeftSquareBracketDirectoryRightSquareBracket]
  /// * [locationLeftSquareBracketWorkspaceRightSquareBracket]
  /// * [cancelToken] - A [CancelToken] that can be used to cancel the operation
  /// * [headers] - Can be used to add additional headers to the request
  /// * [extras] - Can be used to add flags to the request
  /// * [validateStatus] - A [ValidateStatus] callback that can be used to determine request success based on the HTTP status of the response
  /// * [onSendProgress] - A [ProgressCallback] that can be used to get the send progress
  /// * [onReceiveProgress] - A [ProgressCallback] that can be used to get the receive progress
  ///
  /// Returns a [Future]
  /// Throws [DioException] if API call or serialization fails
  Future<Response<void>> v2CredentialRemove({
    required String credentialID,
    String? locationLeftSquareBracketDirectoryRightSquareBracket,
    String? locationLeftSquareBracketWorkspaceRightSquareBracket,
    CancelToken? cancelToken,
    Map<String, dynamic>? headers,
    Map<String, dynamic>? extra,
    ValidateStatus? validateStatus,
    ProgressCallback? onSendProgress,
    ProgressCallback? onReceiveProgress,
  }) async {
    const _operationId = r'v2.credential.remove';
    final _path = r'/api/credential/{credentialID}'.replaceAll(
      '{'
      r'credentialID'
      '}',
      encodeOpenCodePathSegment(
        serializeOpenCodeQueryParameter(credentialID).toString(),
      ),
    );
    final _options = Options(
      method: r'DELETE',
      headers: <String, dynamic>{...?headers},
      extra: <String, dynamic>{
        'secure': <Map<String, String>>[],
        ...?extra,
        'operationId': _operationId,
      },
      validateStatus: validateStatus,
    );

    final _queryParameters = <String, dynamic>{
      if (locationLeftSquareBracketDirectoryRightSquareBracket != null)
        r'location[directory]': serializeOpenCodeQueryParameter(
          locationLeftSquareBracketDirectoryRightSquareBracket,
        ),
      if (locationLeftSquareBracketWorkspaceRightSquareBracket != null)
        r'location[workspace]': serializeOpenCodeQueryParameter(
          locationLeftSquareBracketWorkspaceRightSquareBracket,
        ),
    };

    late Response<Object> _response;
    try {
      _response = await requestOpenCode<Object>(
        _dio,
        _path,
        includeBody: false,
        options: _options,
        queryParameters: _queryParameters,
        cancelToken: cancelToken,
        onSendProgress: onSendProgress,
        onReceiveProgress: onReceiveProgress,
      );
    } on DioException catch (error) {
      rethrowOpenCodeApiException(error, operationId: _operationId);
    }
    throwIfOpenCodeApiError(_response, operationId: _operationId);

    return _response;
  }

  /// Update credential
  /// Update a stored credential label.
  ///
  /// Parameters:
  /// * [credentialID]
  /// * [v2CredentialUpdateRequest]
  /// * [locationLeftSquareBracketDirectoryRightSquareBracket]
  /// * [locationLeftSquareBracketWorkspaceRightSquareBracket]
  /// * [cancelToken] - A [CancelToken] that can be used to cancel the operation
  /// * [headers] - Can be used to add additional headers to the request
  /// * [extras] - Can be used to add flags to the request
  /// * [validateStatus] - A [ValidateStatus] callback that can be used to determine request success based on the HTTP status of the response
  /// * [onSendProgress] - A [ProgressCallback] that can be used to get the send progress
  /// * [onReceiveProgress] - A [ProgressCallback] that can be used to get the receive progress
  ///
  /// Returns a [Future]
  /// Throws [DioException] if API call or serialization fails
  Future<Response<void>> v2CredentialUpdate({
    required String credentialID,
    required V2CredentialUpdateRequest v2CredentialUpdateRequest,
    String? locationLeftSquareBracketDirectoryRightSquareBracket,
    String? locationLeftSquareBracketWorkspaceRightSquareBracket,
    CancelToken? cancelToken,
    Map<String, dynamic>? headers,
    Map<String, dynamic>? extra,
    ValidateStatus? validateStatus,
    ProgressCallback? onSendProgress,
    ProgressCallback? onReceiveProgress,
  }) async {
    const _operationId = r'v2.credential.update';
    final _path = r'/api/credential/{credentialID}'.replaceAll(
      '{'
      r'credentialID'
      '}',
      encodeOpenCodePathSegment(
        serializeOpenCodeQueryParameter(credentialID).toString(),
      ),
    );
    final _options = Options(
      method: r'PATCH',
      headers: <String, dynamic>{...?headers},
      extra: <String, dynamic>{
        'secure': <Map<String, String>>[],
        ...?extra,
        'operationId': _operationId,
      },
      contentType: 'application/json',
      validateStatus: validateStatus,
    );

    final _queryParameters = <String, dynamic>{
      if (locationLeftSquareBracketDirectoryRightSquareBracket != null)
        r'location[directory]': serializeOpenCodeQueryParameter(
          locationLeftSquareBracketDirectoryRightSquareBracket,
        ),
      if (locationLeftSquareBracketWorkspaceRightSquareBracket != null)
        r'location[workspace]': serializeOpenCodeQueryParameter(
          locationLeftSquareBracketWorkspaceRightSquareBracket,
        ),
    };

    dynamic _bodyData;

    try {
      _bodyData = jsonEncode(v2CredentialUpdateRequest);
    } catch (error, stackTrace) {
      throw DioException(
        requestOptions: _options.compose(
          _dio.options,
          _path,
          queryParameters: _queryParameters,
        ),
        type: DioExceptionType.unknown,
        error: error,
        stackTrace: stackTrace,
      );
    }

    late Response<Object> _response;
    try {
      _response = await requestOpenCode<Object>(
        _dio,
        _path,
        includeBody: true,
        bodyData: _bodyData,
        options: _options,
        queryParameters: _queryParameters,
        cancelToken: cancelToken,
        onSendProgress: onSendProgress,
        onReceiveProgress: onReceiveProgress,
      );
    } on DioException catch (error) {
      rethrowOpenCodeApiException(error, operationId: _operationId);
    }
    throwIfOpenCodeApiError(_response, operationId: _operationId);

    return _response;
  }

  /// Check server health
  /// Check whether the API server is ready to accept requests.
  ///
  /// Parameters:
  /// * [cancelToken] - A [CancelToken] that can be used to cancel the operation
  /// * [headers] - Can be used to add additional headers to the request
  /// * [extras] - Can be used to add flags to the request
  /// * [validateStatus] - A [ValidateStatus] callback that can be used to determine request success based on the HTTP status of the response
  /// * [onSendProgress] - A [ProgressCallback] that can be used to get the send progress
  /// * [onReceiveProgress] - A [ProgressCallback] that can be used to get the receive progress
  ///
  /// Returns a [Future] containing a [Response] with a [V2HealthGet200Response] as data
  /// Throws [DioException] if API call or serialization fails
  Future<Response<V2HealthGet200Response>> v2HealthGet({
    CancelToken? cancelToken,
    Map<String, dynamic>? headers,
    Map<String, dynamic>? extra,
    ValidateStatus? validateStatus,
    ProgressCallback? onSendProgress,
    ProgressCallback? onReceiveProgress,
  }) async {
    const _operationId = r'v2.health.get';
    final _path = r'/api/health';
    final _options = Options(
      method: r'GET',
      headers: <String, dynamic>{...?headers},
      extra: <String, dynamic>{
        'secure': <Map<String, String>>[],
        ...?extra,
        'operationId': _operationId,
      },
      validateStatus: validateStatus,
    );

    late Response<Object> _response;
    try {
      _response = await requestOpenCode<Object>(
        _dio,
        _path,
        includeBody: false,
        options: _options,
        cancelToken: cancelToken,
        onSendProgress: onSendProgress,
        onReceiveProgress: onReceiveProgress,
      );
    } on DioException catch (error) {
      rethrowOpenCodeApiException(error, operationId: _operationId);
    }
    throwIfOpenCodeApiError(_response, operationId: _operationId);

    V2HealthGet200Response? _responseData;

    try {
      final rawData = _response.data;
      _responseData = rawData == null
          ? null
          : deserialize<V2HealthGet200Response, V2HealthGet200Response>(
              rawData,
              'V2HealthGet200Response',
              growable: true,
            );
    } catch (error, stackTrace) {
      throw DioException(
        requestOptions: _response.requestOptions,
        response: _response,
        type: DioExceptionType.unknown,
        error: error,
        stackTrace: stackTrace,
      );
    }

    return Response<V2HealthGet200Response>(
      data: _responseData,
      headers: _response.headers,
      isRedirect: _response.isRedirect,
      requestOptions: _response.requestOptions,
      redirects: _response.redirects,
      statusCode: _response.statusCode,
      statusMessage: _response.statusMessage,
      extra: _response.extra,
    );
  }

  /// Get location
  /// Resolve the requested location or the server default location.
  ///
  /// Parameters:
  /// * [locationLeftSquareBracketDirectoryRightSquareBracket]
  /// * [locationLeftSquareBracketWorkspaceRightSquareBracket]
  /// * [cancelToken] - A [CancelToken] that can be used to cancel the operation
  /// * [headers] - Can be used to add additional headers to the request
  /// * [extras] - Can be used to add flags to the request
  /// * [validateStatus] - A [ValidateStatus] callback that can be used to determine request success based on the HTTP status of the response
  /// * [onSendProgress] - A [ProgressCallback] that can be used to get the send progress
  /// * [onReceiveProgress] - A [ProgressCallback] that can be used to get the receive progress
  ///
  /// Returns a [Future] containing a [Response] with a [LocationInfo] as data
  /// Throws [DioException] if API call or serialization fails
  Future<Response<LocationInfo>> v2LocationGet({
    String? locationLeftSquareBracketDirectoryRightSquareBracket,
    String? locationLeftSquareBracketWorkspaceRightSquareBracket,
    CancelToken? cancelToken,
    Map<String, dynamic>? headers,
    Map<String, dynamic>? extra,
    ValidateStatus? validateStatus,
    ProgressCallback? onSendProgress,
    ProgressCallback? onReceiveProgress,
  }) async {
    const _operationId = r'v2.location.get';
    final _path = r'/api/location';
    final _options = Options(
      method: r'GET',
      headers: <String, dynamic>{...?headers},
      extra: <String, dynamic>{
        'secure': <Map<String, String>>[],
        ...?extra,
        'operationId': _operationId,
      },
      validateStatus: validateStatus,
    );

    final _queryParameters = <String, dynamic>{
      if (locationLeftSquareBracketDirectoryRightSquareBracket != null)
        r'location[directory]': serializeOpenCodeQueryParameter(
          locationLeftSquareBracketDirectoryRightSquareBracket,
        ),
      if (locationLeftSquareBracketWorkspaceRightSquareBracket != null)
        r'location[workspace]': serializeOpenCodeQueryParameter(
          locationLeftSquareBracketWorkspaceRightSquareBracket,
        ),
    };

    late Response<Object> _response;
    try {
      _response = await requestOpenCode<Object>(
        _dio,
        _path,
        includeBody: false,
        options: _options,
        queryParameters: _queryParameters,
        cancelToken: cancelToken,
        onSendProgress: onSendProgress,
        onReceiveProgress: onReceiveProgress,
      );
    } on DioException catch (error) {
      rethrowOpenCodeApiException(error, operationId: _operationId);
    }
    throwIfOpenCodeApiError(_response, operationId: _operationId);

    LocationInfo? _responseData;

    try {
      final rawData = _response.data;
      _responseData = rawData == null
          ? null
          : deserialize<LocationInfo, LocationInfo>(
              rawData,
              'LocationInfo',
              growable: true,
            );
    } catch (error, stackTrace) {
      throw DioException(
        requestOptions: _response.requestOptions,
        response: _response,
        type: DioExceptionType.unknown,
        error: error,
        stackTrace: stackTrace,
      );
    }

    return Response<LocationInfo>(
      data: _responseData,
      headers: _response.headers,
      isRedirect: _response.isRedirect,
      requestOptions: _response.requestOptions,
      redirects: _response.redirects,
      statusCode: _response.statusCode,
      statusMessage: _response.statusMessage,
      extra: _response.extra,
    );
  }
}
