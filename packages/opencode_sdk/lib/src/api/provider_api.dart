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

import 'package:opencode_sdk/src/model/bad_request_error.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union065.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union066.dart';
import 'package:opencode_sdk/src/model/provider_auth_authorization.dart';
import 'package:opencode_sdk/src/model/provider_auth_method.dart';
import 'package:opencode_sdk/src/model/provider_list200_response.dart';
import 'package:opencode_sdk/src/model/provider_oauth_authorize_request.dart';
import 'package:opencode_sdk/src/model/provider_oauth_callback_request.dart';

class ProviderApi {
  final Dio _dio;

  const ProviderApi(this._dio);

  /// Get provider auth methods
  /// Retrieve available authentication methods for all AI providers.
  ///
  /// Parameters:
  /// * [directory]
  /// * [workspace]
  /// * [cancelToken] - A [CancelToken] that can be used to cancel the operation
  /// * [headers] - Can be used to add additional headers to the request
  /// * [extras] - Can be used to add flags to the request
  /// * [validateStatus] - A [ValidateStatus] callback that can be used to determine request success based on the HTTP status of the response
  /// * [onSendProgress] - A [ProgressCallback] that can be used to get the send progress
  /// * [onReceiveProgress] - A [ProgressCallback] that can be used to get the receive progress
  ///
  /// Returns a [Future] containing a [Response] with a [Map<String, List<ProviderAuthMethod>>] as data
  /// Throws [DioException] if API call or serialization fails
  Future<Response<Map<String, List<ProviderAuthMethod>>>> providerAuth({
    String? directory,
    String? workspace,
    CancelToken? cancelToken,
    Map<String, dynamic>? headers,
    Map<String, dynamic>? extra,
    ValidateStatus? validateStatus,
    ProgressCallback? onSendProgress,
    ProgressCallback? onReceiveProgress,
  }) async {
    const _operationId = r'provider.auth';
    final _path = r'/provider/auth';
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
      if (directory != null)
        r'directory': serializeOpenCodeQueryParameter(directory),
      if (workspace != null)
        r'workspace': serializeOpenCodeQueryParameter(workspace),
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

    Map<String, List<ProviderAuthMethod>>? _responseData;

    try {
      final rawData = _response.data;
      _responseData = rawData == null
          ? null
          : deserialize<Map<String, List<ProviderAuthMethod>>, List>(
              rawData,
              'Map<String, List<ProviderAuthMethod>>',
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

    return Response<Map<String, List<ProviderAuthMethod>>>(
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

  /// List providers
  /// Get a list of all available AI providers, including both available and connected ones.
  ///
  /// Parameters:
  /// * [directory]
  /// * [workspace]
  /// * [cancelToken] - A [CancelToken] that can be used to cancel the operation
  /// * [headers] - Can be used to add additional headers to the request
  /// * [extras] - Can be used to add flags to the request
  /// * [validateStatus] - A [ValidateStatus] callback that can be used to determine request success based on the HTTP status of the response
  /// * [onSendProgress] - A [ProgressCallback] that can be used to get the send progress
  /// * [onReceiveProgress] - A [ProgressCallback] that can be used to get the receive progress
  ///
  /// Returns a [Future] containing a [Response] with a [ProviderList200Response] as data
  /// Throws [DioException] if API call or serialization fails
  Future<Response<ProviderList200Response>> providerList({
    String? directory,
    String? workspace,
    CancelToken? cancelToken,
    Map<String, dynamic>? headers,
    Map<String, dynamic>? extra,
    ValidateStatus? validateStatus,
    ProgressCallback? onSendProgress,
    ProgressCallback? onReceiveProgress,
  }) async {
    const _operationId = r'provider.list';
    final _path = r'/provider';
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
      if (directory != null)
        r'directory': serializeOpenCodeQueryParameter(directory),
      if (workspace != null)
        r'workspace': serializeOpenCodeQueryParameter(workspace),
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

    ProviderList200Response? _responseData;

    try {
      final rawData = _response.data;
      _responseData = rawData == null
          ? null
          : deserialize<ProviderList200Response, ProviderList200Response>(
              rawData,
              'ProviderList200Response',
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

    return Response<ProviderList200Response>(
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

  /// Start OAuth authorization
  /// Start the OAuth authorization flow for a provider.
  ///
  /// Parameters:
  /// * [providerID]
  /// * [directory]
  /// * [workspace]
  /// * [providerOauthAuthorizeRequest]
  /// * [cancelToken] - A [CancelToken] that can be used to cancel the operation
  /// * [headers] - Can be used to add additional headers to the request
  /// * [extras] - Can be used to add flags to the request
  /// * [validateStatus] - A [ValidateStatus] callback that can be used to determine request success based on the HTTP status of the response
  /// * [onSendProgress] - A [ProgressCallback] that can be used to get the send progress
  /// * [onReceiveProgress] - A [ProgressCallback] that can be used to get the receive progress
  ///
  /// Returns a [Future] containing a [Response] with a [ProviderAuthAuthorization] as data
  /// Throws [DioException] if API call or serialization fails
  Future<Response<ProviderAuthAuthorization>> providerOauthAuthorize({
    required String providerID,
    String? directory,
    String? workspace,
    ProviderOauthAuthorizeRequest? providerOauthAuthorizeRequest,
    CancelToken? cancelToken,
    Map<String, dynamic>? headers,
    Map<String, dynamic>? extra,
    ValidateStatus? validateStatus,
    ProgressCallback? onSendProgress,
    ProgressCallback? onReceiveProgress,
  }) async {
    const _operationId = r'provider.oauth.authorize';
    final _path = r'/provider/{providerID}/oauth/authorize'.replaceAll(
      '{'
      r'providerID'
      '}',
      encodeOpenCodePathSegment(
        serializeOpenCodeQueryParameter(providerID).toString(),
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
      if (directory != null)
        r'directory': serializeOpenCodeQueryParameter(directory),
      if (workspace != null)
        r'workspace': serializeOpenCodeQueryParameter(workspace),
    };

    dynamic _bodyData;

    try {
      if (providerOauthAuthorizeRequest != null) {
        _bodyData = jsonEncode(providerOauthAuthorizeRequest);
      }
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
        includeBody: providerOauthAuthorizeRequest != null,
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

    ProviderAuthAuthorization? _responseData;

    try {
      final rawData = _response.data;
      _responseData = rawData == null
          ? null
          : deserialize<ProviderAuthAuthorization, ProviderAuthAuthorization>(
              rawData,
              'ProviderAuthAuthorization',
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

    return Response<ProviderAuthAuthorization>(
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

  /// Handle OAuth callback
  /// Handle the OAuth callback from a provider after user authorization.
  ///
  /// Parameters:
  /// * [providerID]
  /// * [directory]
  /// * [workspace]
  /// * [providerOauthCallbackRequest]
  /// * [cancelToken] - A [CancelToken] that can be used to cancel the operation
  /// * [headers] - Can be used to add additional headers to the request
  /// * [extras] - Can be used to add flags to the request
  /// * [validateStatus] - A [ValidateStatus] callback that can be used to determine request success based on the HTTP status of the response
  /// * [onSendProgress] - A [ProgressCallback] that can be used to get the send progress
  /// * [onReceiveProgress] - A [ProgressCallback] that can be used to get the receive progress
  ///
  /// Returns a [Future] containing a [Response] with a [bool] as data
  /// Throws [DioException] if API call or serialization fails
  Future<Response<bool>> providerOauthCallback({
    required String providerID,
    String? directory,
    String? workspace,
    ProviderOauthCallbackRequest? providerOauthCallbackRequest,
    CancelToken? cancelToken,
    Map<String, dynamic>? headers,
    Map<String, dynamic>? extra,
    ValidateStatus? validateStatus,
    ProgressCallback? onSendProgress,
    ProgressCallback? onReceiveProgress,
  }) async {
    const _operationId = r'provider.oauth.callback';
    final _path = r'/provider/{providerID}/oauth/callback'.replaceAll(
      '{'
      r'providerID'
      '}',
      encodeOpenCodePathSegment(
        serializeOpenCodeQueryParameter(providerID).toString(),
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
      if (directory != null)
        r'directory': serializeOpenCodeQueryParameter(directory),
      if (workspace != null)
        r'workspace': serializeOpenCodeQueryParameter(workspace),
    };

    dynamic _bodyData;

    try {
      if (providerOauthCallbackRequest != null) {
        _bodyData = jsonEncode(providerOauthCallbackRequest);
      }
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
        includeBody: providerOauthCallbackRequest != null,
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

    bool? _responseData;

    try {
      final rawData = _response.data;
      _responseData = rawData == null
          ? null
          : deserialize<bool, bool>(rawData, 'bool', growable: true);
    } catch (error, stackTrace) {
      throw DioException(
        requestOptions: _response.requestOptions,
        response: _response,
        type: DioExceptionType.unknown,
        error: error,
        stackTrace: stackTrace,
      );
    }

    return Response<bool>(
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
