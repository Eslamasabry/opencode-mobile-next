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
import 'package:opencode_sdk/src/model/unauthorized_error.dart';
import 'package:opencode_sdk/src/model/v2_integration_attempt_complete_request.dart';
import 'package:opencode_sdk/src/model/v2_integration_attempt_status200_response.dart';
import 'package:opencode_sdk/src/model/v2_integration_connect_key_request.dart';
import 'package:opencode_sdk/src/model/v2_integration_connect_oauth200_response.dart';
import 'package:opencode_sdk/src/model/v2_integration_connect_oauth_request.dart';
import 'package:opencode_sdk/src/model/v2_integration_get200_response.dart';
import 'package:opencode_sdk/src/model/v2_integration_list200_response.dart';

class IntegrationsApi {
  final Dio _dio;

  const IntegrationsApi(this._dio);

  /// Cancel OAuth connection
  /// Cancel an OAuth attempt and release its resources.
  ///
  /// Parameters:
  /// * [attemptID]
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
  Future<Response<void>> v2IntegrationAttemptCancel({
    required String attemptID,
    String? locationLeftSquareBracketDirectoryRightSquareBracket,
    String? locationLeftSquareBracketWorkspaceRightSquareBracket,
    CancelToken? cancelToken,
    Map<String, dynamic>? headers,
    Map<String, dynamic>? extra,
    ValidateStatus? validateStatus,
    ProgressCallback? onSendProgress,
    ProgressCallback? onReceiveProgress,
  }) async {
    const _operationId = r'v2.integration.attempt.cancel';
    final _path = r'/api/integration/attempt/{attemptID}'.replaceAll(
      '{'
      r'attemptID'
      '}',
      encodeOpenCodePathSegment(
        serializeOpenCodeQueryParameter(attemptID).toString(),
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

  /// Complete OAuth connection
  /// Complete a code-based OAuth attempt and store the resulting credential.
  ///
  /// Parameters:
  /// * [attemptID]
  /// * [v2IntegrationAttemptCompleteRequest]
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
  Future<Response<void>> v2IntegrationAttemptComplete({
    required String attemptID,
    required V2IntegrationAttemptCompleteRequest
    v2IntegrationAttemptCompleteRequest,
    String? locationLeftSquareBracketDirectoryRightSquareBracket,
    String? locationLeftSquareBracketWorkspaceRightSquareBracket,
    CancelToken? cancelToken,
    Map<String, dynamic>? headers,
    Map<String, dynamic>? extra,
    ValidateStatus? validateStatus,
    ProgressCallback? onSendProgress,
    ProgressCallback? onReceiveProgress,
  }) async {
    const _operationId = r'v2.integration.attempt.complete';
    final _path = r'/api/integration/attempt/{attemptID}/complete'.replaceAll(
      '{'
      r'attemptID'
      '}',
      encodeOpenCodePathSegment(
        serializeOpenCodeQueryParameter(attemptID).toString(),
      ),
    );
    final _options = Options(
      method: r'POST',
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
      _bodyData = jsonEncode(v2IntegrationAttemptCompleteRequest);
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

  /// Get OAuth attempt status
  /// Poll the current status of an OAuth attempt.
  ///
  /// Parameters:
  /// * [attemptID]
  /// * [locationLeftSquareBracketDirectoryRightSquareBracket]
  /// * [locationLeftSquareBracketWorkspaceRightSquareBracket]
  /// * [cancelToken] - A [CancelToken] that can be used to cancel the operation
  /// * [headers] - Can be used to add additional headers to the request
  /// * [extras] - Can be used to add flags to the request
  /// * [validateStatus] - A [ValidateStatus] callback that can be used to determine request success based on the HTTP status of the response
  /// * [onSendProgress] - A [ProgressCallback] that can be used to get the send progress
  /// * [onReceiveProgress] - A [ProgressCallback] that can be used to get the receive progress
  ///
  /// Returns a [Future] containing a [Response] with a [V2IntegrationAttemptStatus200Response] as data
  /// Throws [DioException] if API call or serialization fails
  Future<Response<V2IntegrationAttemptStatus200Response>>
  v2IntegrationAttemptStatus({
    required String attemptID,
    String? locationLeftSquareBracketDirectoryRightSquareBracket,
    String? locationLeftSquareBracketWorkspaceRightSquareBracket,
    CancelToken? cancelToken,
    Map<String, dynamic>? headers,
    Map<String, dynamic>? extra,
    ValidateStatus? validateStatus,
    ProgressCallback? onSendProgress,
    ProgressCallback? onReceiveProgress,
  }) async {
    const _operationId = r'v2.integration.attempt.status';
    final _path = r'/api/integration/attempt/{attemptID}'.replaceAll(
      '{'
      r'attemptID'
      '}',
      encodeOpenCodePathSegment(
        serializeOpenCodeQueryParameter(attemptID).toString(),
      ),
    );
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

    V2IntegrationAttemptStatus200Response? _responseData;

    try {
      final rawData = _response.data;
      _responseData = rawData == null
          ? null
          : deserialize<
              V2IntegrationAttemptStatus200Response,
              V2IntegrationAttemptStatus200Response
            >(rawData, 'V2IntegrationAttemptStatus200Response', growable: true);
    } catch (error, stackTrace) {
      throw DioException(
        requestOptions: _response.requestOptions,
        response: _response,
        type: DioExceptionType.unknown,
        error: error,
        stackTrace: stackTrace,
      );
    }

    return Response<V2IntegrationAttemptStatus200Response>(
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

  /// Connect with key
  /// Run a key authentication method and store the resulting credential.
  ///
  /// Parameters:
  /// * [integrationID]
  /// * [v2IntegrationConnectKeyRequest]
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
  Future<Response<void>> v2IntegrationConnectKey({
    required String integrationID,
    required V2IntegrationConnectKeyRequest v2IntegrationConnectKeyRequest,
    String? locationLeftSquareBracketDirectoryRightSquareBracket,
    String? locationLeftSquareBracketWorkspaceRightSquareBracket,
    CancelToken? cancelToken,
    Map<String, dynamic>? headers,
    Map<String, dynamic>? extra,
    ValidateStatus? validateStatus,
    ProgressCallback? onSendProgress,
    ProgressCallback? onReceiveProgress,
  }) async {
    const _operationId = r'v2.integration.connect.key';
    final _path = r'/api/integration/{integrationID}/connect/key'.replaceAll(
      '{'
      r'integrationID'
      '}',
      encodeOpenCodePathSegment(
        serializeOpenCodeQueryParameter(integrationID).toString(),
      ),
    );
    final _options = Options(
      method: r'POST',
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
      _bodyData = jsonEncode(v2IntegrationConnectKeyRequest);
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

  /// Begin OAuth connection
  /// Start an OAuth attempt and return the authorization details.
  ///
  /// Parameters:
  /// * [integrationID]
  /// * [v2IntegrationConnectOauthRequest]
  /// * [locationLeftSquareBracketDirectoryRightSquareBracket]
  /// * [locationLeftSquareBracketWorkspaceRightSquareBracket]
  /// * [cancelToken] - A [CancelToken] that can be used to cancel the operation
  /// * [headers] - Can be used to add additional headers to the request
  /// * [extras] - Can be used to add flags to the request
  /// * [validateStatus] - A [ValidateStatus] callback that can be used to determine request success based on the HTTP status of the response
  /// * [onSendProgress] - A [ProgressCallback] that can be used to get the send progress
  /// * [onReceiveProgress] - A [ProgressCallback] that can be used to get the receive progress
  ///
  /// Returns a [Future] containing a [Response] with a [V2IntegrationConnectOauth200Response] as data
  /// Throws [DioException] if API call or serialization fails
  Future<Response<V2IntegrationConnectOauth200Response>>
  v2IntegrationConnectOauth({
    required String integrationID,
    required V2IntegrationConnectOauthRequest v2IntegrationConnectOauthRequest,
    String? locationLeftSquareBracketDirectoryRightSquareBracket,
    String? locationLeftSquareBracketWorkspaceRightSquareBracket,
    CancelToken? cancelToken,
    Map<String, dynamic>? headers,
    Map<String, dynamic>? extra,
    ValidateStatus? validateStatus,
    ProgressCallback? onSendProgress,
    ProgressCallback? onReceiveProgress,
  }) async {
    const _operationId = r'v2.integration.connect.oauth';
    final _path = r'/api/integration/{integrationID}/connect/oauth'.replaceAll(
      '{'
      r'integrationID'
      '}',
      encodeOpenCodePathSegment(
        serializeOpenCodeQueryParameter(integrationID).toString(),
      ),
    );
    final _options = Options(
      method: r'POST',
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
      _bodyData = jsonEncode(v2IntegrationConnectOauthRequest);
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

    V2IntegrationConnectOauth200Response? _responseData;

    try {
      final rawData = _response.data;
      _responseData = rawData == null
          ? null
          : deserialize<
              V2IntegrationConnectOauth200Response,
              V2IntegrationConnectOauth200Response
            >(rawData, 'V2IntegrationConnectOauth200Response', growable: true);
    } catch (error, stackTrace) {
      throw DioException(
        requestOptions: _response.requestOptions,
        response: _response,
        type: DioExceptionType.unknown,
        error: error,
        stackTrace: stackTrace,
      );
    }

    return Response<V2IntegrationConnectOauth200Response>(
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

  /// Get integration
  /// Retrieve one integration and its authentication methods.
  ///
  /// Parameters:
  /// * [integrationID]
  /// * [locationLeftSquareBracketDirectoryRightSquareBracket]
  /// * [locationLeftSquareBracketWorkspaceRightSquareBracket]
  /// * [cancelToken] - A [CancelToken] that can be used to cancel the operation
  /// * [headers] - Can be used to add additional headers to the request
  /// * [extras] - Can be used to add flags to the request
  /// * [validateStatus] - A [ValidateStatus] callback that can be used to determine request success based on the HTTP status of the response
  /// * [onSendProgress] - A [ProgressCallback] that can be used to get the send progress
  /// * [onReceiveProgress] - A [ProgressCallback] that can be used to get the receive progress
  ///
  /// Returns a [Future] containing a [Response] with a [V2IntegrationGet200Response] as data
  /// Throws [DioException] if API call or serialization fails
  Future<Response<V2IntegrationGet200Response>> v2IntegrationGet({
    required String integrationID,
    String? locationLeftSquareBracketDirectoryRightSquareBracket,
    String? locationLeftSquareBracketWorkspaceRightSquareBracket,
    CancelToken? cancelToken,
    Map<String, dynamic>? headers,
    Map<String, dynamic>? extra,
    ValidateStatus? validateStatus,
    ProgressCallback? onSendProgress,
    ProgressCallback? onReceiveProgress,
  }) async {
    const _operationId = r'v2.integration.get';
    final _path = r'/api/integration/{integrationID}'.replaceAll(
      '{'
      r'integrationID'
      '}',
      encodeOpenCodePathSegment(
        serializeOpenCodeQueryParameter(integrationID).toString(),
      ),
    );
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

    V2IntegrationGet200Response? _responseData;

    try {
      final rawData = _response.data;
      _responseData = rawData == null
          ? null
          : deserialize<
              V2IntegrationGet200Response,
              V2IntegrationGet200Response
            >(rawData, 'V2IntegrationGet200Response', growable: true);
    } catch (error, stackTrace) {
      throw DioException(
        requestOptions: _response.requestOptions,
        response: _response,
        type: DioExceptionType.unknown,
        error: error,
        stackTrace: stackTrace,
      );
    }

    return Response<V2IntegrationGet200Response>(
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

  /// List integrations
  /// Retrieve available integrations and their authentication methods.
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
  /// Returns a [Future] containing a [Response] with a [V2IntegrationList200Response] as data
  /// Throws [DioException] if API call or serialization fails
  Future<Response<V2IntegrationList200Response>> v2IntegrationList({
    String? locationLeftSquareBracketDirectoryRightSquareBracket,
    String? locationLeftSquareBracketWorkspaceRightSquareBracket,
    CancelToken? cancelToken,
    Map<String, dynamic>? headers,
    Map<String, dynamic>? extra,
    ValidateStatus? validateStatus,
    ProgressCallback? onSendProgress,
    ProgressCallback? onReceiveProgress,
  }) async {
    const _operationId = r'v2.integration.list';
    final _path = r'/api/integration';
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

    V2IntegrationList200Response? _responseData;

    try {
      final rawData = _response.data;
      _responseData = rawData == null
          ? null
          : deserialize<
              V2IntegrationList200Response,
              V2IntegrationList200Response
            >(rawData, 'V2IntegrationList200Response', growable: true);
    } catch (error, stackTrace) {
      throw DioException(
        requestOptions: _response.requestOptions,
        response: _response,
        type: DioExceptionType.unknown,
        error: error,
        stackTrace: stackTrace,
      );
    }

    return Response<V2IntegrationList200Response>(
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
