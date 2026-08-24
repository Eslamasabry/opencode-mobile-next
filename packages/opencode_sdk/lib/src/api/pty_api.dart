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
import 'package:opencode_sdk/src/model/effect_http_api_error_forbidden.dart';
import 'package:opencode_sdk/src/model/forbidden_error.dart';
import 'package:opencode_sdk/src/model/invalid_request_error.dart';
import 'package:opencode_sdk/src/model/not_found_error.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union060.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union061.dart';
import 'package:opencode_sdk/src/model/pty.dart';
import 'package:opencode_sdk/src/model/pty_create_request.dart';
import 'package:opencode_sdk/src/model/pty_forbidden_error.dart';
import 'package:opencode_sdk/src/model/pty_not_found_error.dart';
import 'package:opencode_sdk/src/model/pty_shells200_response_inner.dart';
import 'package:opencode_sdk/src/model/pty_ticket_connect_token.dart';
import 'package:opencode_sdk/src/model/pty_update_request.dart';
import 'package:opencode_sdk/src/model/unauthorized_error.dart';
import 'package:opencode_sdk/src/model/v2_pty_connect_token200_response.dart';
import 'package:opencode_sdk/src/model/v2_pty_create200_response.dart';
import 'package:opencode_sdk/src/model/v2_pty_get200_response.dart';
import 'package:opencode_sdk/src/model/v2_pty_list200_response.dart';

class PtyApi {
  final Dio _dio;

  const PtyApi(this._dio);

  /// Connect to PTY session
  /// Establish a WebSocket connection to interact with a pseudo-terminal (PTY) session in real-time.
  ///
  /// Parameters:
  /// * [ptyID]
  /// * [directory]
  /// * [workspace]
  /// * [cursor]
  /// * [ticket]
  /// * [cancelToken] - A [CancelToken] that can be used to cancel the operation
  /// * [headers] - Can be used to add additional headers to the request
  /// * [extras] - Can be used to add flags to the request
  /// * [validateStatus] - A [ValidateStatus] callback that can be used to determine request success based on the HTTP status of the response
  /// * [onSendProgress] - A [ProgressCallback] that can be used to get the send progress
  /// * [onReceiveProgress] - A [ProgressCallback] that can be used to get the receive progress
  ///
  /// Returns a [Future] containing a [Response] with a [bool] as data
  /// Throws [DioException] if API call or serialization fails
  Future<Response<bool>> ptyConnect({
    required String ptyID,
    String? directory,
    String? workspace,
    String? cursor,
    String? ticket,
    CancelToken? cancelToken,
    Map<String, dynamic>? headers,
    Map<String, dynamic>? extra,
    ValidateStatus? validateStatus,
    ProgressCallback? onSendProgress,
    ProgressCallback? onReceiveProgress,
  }) async {
    const _operationId = r'pty.connect';
    final _path = r'/pty/{ptyID}/connect'.replaceAll(
      '{'
      r'ptyID'
      '}',
      encodeOpenCodePathSegment(
        serializeOpenCodeQueryParameter(ptyID).toString(),
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
      if (directory != null)
        r'directory': serializeOpenCodeQueryParameter(directory),
      if (workspace != null)
        r'workspace': serializeOpenCodeQueryParameter(workspace),
      if (cursor != null) r'cursor': serializeOpenCodeQueryParameter(cursor),
      if (ticket != null) r'ticket': serializeOpenCodeQueryParameter(ticket),
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

  /// Create PTY WebSocket token
  /// Create a short-lived ticket for opening a PTY WebSocket connection.
  ///
  /// Parameters:
  /// * [ptyID]
  /// * [directory]
  /// * [workspace]
  /// * [cancelToken] - A [CancelToken] that can be used to cancel the operation
  /// * [headers] - Can be used to add additional headers to the request
  /// * [extras] - Can be used to add flags to the request
  /// * [validateStatus] - A [ValidateStatus] callback that can be used to determine request success based on the HTTP status of the response
  /// * [onSendProgress] - A [ProgressCallback] that can be used to get the send progress
  /// * [onReceiveProgress] - A [ProgressCallback] that can be used to get the receive progress
  ///
  /// Returns a [Future] containing a [Response] with a [PtyTicketConnectToken] as data
  /// Throws [DioException] if API call or serialization fails
  Future<Response<PtyTicketConnectToken>> ptyConnectToken({
    required String ptyID,
    String? directory,
    String? workspace,
    CancelToken? cancelToken,
    Map<String, dynamic>? headers,
    Map<String, dynamic>? extra,
    ValidateStatus? validateStatus,
    ProgressCallback? onSendProgress,
    ProgressCallback? onReceiveProgress,
  }) async {
    const _operationId = r'pty.connectToken';
    final _path = r'/pty/{ptyID}/connect-token'.replaceAll(
      '{'
      r'ptyID'
      '}',
      encodeOpenCodePathSegment(
        serializeOpenCodeQueryParameter(ptyID).toString(),
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

    PtyTicketConnectToken? _responseData;

    try {
      final rawData = _response.data;
      _responseData = rawData == null
          ? null
          : deserialize<PtyTicketConnectToken, PtyTicketConnectToken>(
              rawData,
              'PtyTicketConnectToken',
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

    return Response<PtyTicketConnectToken>(
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

  /// Create PTY session
  /// Create a new pseudo-terminal (PTY) session for running shell commands and processes.
  ///
  /// Parameters:
  /// * [directory]
  /// * [workspace]
  /// * [ptyCreateRequest]
  /// * [cancelToken] - A [CancelToken] that can be used to cancel the operation
  /// * [headers] - Can be used to add additional headers to the request
  /// * [extras] - Can be used to add flags to the request
  /// * [validateStatus] - A [ValidateStatus] callback that can be used to determine request success based on the HTTP status of the response
  /// * [onSendProgress] - A [ProgressCallback] that can be used to get the send progress
  /// * [onReceiveProgress] - A [ProgressCallback] that can be used to get the receive progress
  ///
  /// Returns a [Future] containing a [Response] with a [Pty] as data
  /// Throws [DioException] if API call or serialization fails
  Future<Response<Pty>> ptyCreate({
    String? directory,
    String? workspace,
    PtyCreateRequest? ptyCreateRequest,
    CancelToken? cancelToken,
    Map<String, dynamic>? headers,
    Map<String, dynamic>? extra,
    ValidateStatus? validateStatus,
    ProgressCallback? onSendProgress,
    ProgressCallback? onReceiveProgress,
  }) async {
    const _operationId = r'pty.create';
    final _path = r'/pty';
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
      if (ptyCreateRequest != null) {
        _bodyData = jsonEncode(ptyCreateRequest);
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
        includeBody: ptyCreateRequest != null,
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

    Pty? _responseData;

    try {
      final rawData = _response.data;
      _responseData = rawData == null
          ? null
          : deserialize<Pty, Pty>(rawData, 'Pty', growable: true);
    } catch (error, stackTrace) {
      throw DioException(
        requestOptions: _response.requestOptions,
        response: _response,
        type: DioExceptionType.unknown,
        error: error,
        stackTrace: stackTrace,
      );
    }

    return Response<Pty>(
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

  /// Get PTY session
  /// Retrieve detailed information about a specific pseudo-terminal (PTY) session.
  ///
  /// Parameters:
  /// * [ptyID]
  /// * [directory]
  /// * [workspace]
  /// * [cancelToken] - A [CancelToken] that can be used to cancel the operation
  /// * [headers] - Can be used to add additional headers to the request
  /// * [extras] - Can be used to add flags to the request
  /// * [validateStatus] - A [ValidateStatus] callback that can be used to determine request success based on the HTTP status of the response
  /// * [onSendProgress] - A [ProgressCallback] that can be used to get the send progress
  /// * [onReceiveProgress] - A [ProgressCallback] that can be used to get the receive progress
  ///
  /// Returns a [Future] containing a [Response] with a [Pty] as data
  /// Throws [DioException] if API call or serialization fails
  Future<Response<Pty>> ptyGet({
    required String ptyID,
    String? directory,
    String? workspace,
    CancelToken? cancelToken,
    Map<String, dynamic>? headers,
    Map<String, dynamic>? extra,
    ValidateStatus? validateStatus,
    ProgressCallback? onSendProgress,
    ProgressCallback? onReceiveProgress,
  }) async {
    const _operationId = r'pty.get';
    final _path = r'/pty/{ptyID}'.replaceAll(
      '{'
      r'ptyID'
      '}',
      encodeOpenCodePathSegment(
        serializeOpenCodeQueryParameter(ptyID).toString(),
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

    Pty? _responseData;

    try {
      final rawData = _response.data;
      _responseData = rawData == null
          ? null
          : deserialize<Pty, Pty>(rawData, 'Pty', growable: true);
    } catch (error, stackTrace) {
      throw DioException(
        requestOptions: _response.requestOptions,
        response: _response,
        type: DioExceptionType.unknown,
        error: error,
        stackTrace: stackTrace,
      );
    }

    return Response<Pty>(
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

  /// List PTY sessions
  /// Get a list of all active pseudo-terminal (PTY) sessions managed by OpenCode.
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
  /// Returns a [Future] containing a [Response] with a [List<Pty>] as data
  /// Throws [DioException] if API call or serialization fails
  Future<Response<List<Pty>>> ptyList({
    String? directory,
    String? workspace,
    CancelToken? cancelToken,
    Map<String, dynamic>? headers,
    Map<String, dynamic>? extra,
    ValidateStatus? validateStatus,
    ProgressCallback? onSendProgress,
    ProgressCallback? onReceiveProgress,
  }) async {
    const _operationId = r'pty.list';
    final _path = r'/pty';
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

    List<Pty>? _responseData;

    try {
      final rawData = _response.data;
      _responseData = rawData == null
          ? null
          : deserialize<List<Pty>, Pty>(rawData, 'List<Pty>', growable: true);
    } catch (error, stackTrace) {
      throw DioException(
        requestOptions: _response.requestOptions,
        response: _response,
        type: DioExceptionType.unknown,
        error: error,
        stackTrace: stackTrace,
      );
    }

    return Response<List<Pty>>(
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

  /// Remove PTY session
  /// Remove and terminate a specific pseudo-terminal (PTY) session.
  ///
  /// Parameters:
  /// * [ptyID]
  /// * [directory]
  /// * [workspace]
  /// * [cancelToken] - A [CancelToken] that can be used to cancel the operation
  /// * [headers] - Can be used to add additional headers to the request
  /// * [extras] - Can be used to add flags to the request
  /// * [validateStatus] - A [ValidateStatus] callback that can be used to determine request success based on the HTTP status of the response
  /// * [onSendProgress] - A [ProgressCallback] that can be used to get the send progress
  /// * [onReceiveProgress] - A [ProgressCallback] that can be used to get the receive progress
  ///
  /// Returns a [Future] containing a [Response] with a [bool] as data
  /// Throws [DioException] if API call or serialization fails
  Future<Response<bool>> ptyRemove({
    required String ptyID,
    String? directory,
    String? workspace,
    CancelToken? cancelToken,
    Map<String, dynamic>? headers,
    Map<String, dynamic>? extra,
    ValidateStatus? validateStatus,
    ProgressCallback? onSendProgress,
    ProgressCallback? onReceiveProgress,
  }) async {
    const _operationId = r'pty.remove';
    final _path = r'/pty/{ptyID}'.replaceAll(
      '{'
      r'ptyID'
      '}',
      encodeOpenCodePathSegment(
        serializeOpenCodeQueryParameter(ptyID).toString(),
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

  /// List available shells
  /// Get a list of available shells on the system.
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
  /// Returns a [Future] containing a [Response] with a [List<PtyShells200ResponseInner>] as data
  /// Throws [DioException] if API call or serialization fails
  Future<Response<List<PtyShells200ResponseInner>>> ptyShells({
    String? directory,
    String? workspace,
    CancelToken? cancelToken,
    Map<String, dynamic>? headers,
    Map<String, dynamic>? extra,
    ValidateStatus? validateStatus,
    ProgressCallback? onSendProgress,
    ProgressCallback? onReceiveProgress,
  }) async {
    const _operationId = r'pty.shells';
    final _path = r'/pty/shells';
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

    List<PtyShells200ResponseInner>? _responseData;

    try {
      final rawData = _response.data;
      _responseData = rawData == null
          ? null
          : deserialize<
              List<PtyShells200ResponseInner>,
              PtyShells200ResponseInner
            >(rawData, 'List<PtyShells200ResponseInner>', growable: true);
    } catch (error, stackTrace) {
      throw DioException(
        requestOptions: _response.requestOptions,
        response: _response,
        type: DioExceptionType.unknown,
        error: error,
        stackTrace: stackTrace,
      );
    }

    return Response<List<PtyShells200ResponseInner>>(
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

  /// Update PTY session
  /// Update properties of an existing pseudo-terminal (PTY) session.
  ///
  /// Parameters:
  /// * [ptyID]
  /// * [directory]
  /// * [workspace]
  /// * [ptyUpdateRequest]
  /// * [cancelToken] - A [CancelToken] that can be used to cancel the operation
  /// * [headers] - Can be used to add additional headers to the request
  /// * [extras] - Can be used to add flags to the request
  /// * [validateStatus] - A [ValidateStatus] callback that can be used to determine request success based on the HTTP status of the response
  /// * [onSendProgress] - A [ProgressCallback] that can be used to get the send progress
  /// * [onReceiveProgress] - A [ProgressCallback] that can be used to get the receive progress
  ///
  /// Returns a [Future] containing a [Response] with a [Pty] as data
  /// Throws [DioException] if API call or serialization fails
  Future<Response<Pty>> ptyUpdate({
    required String ptyID,
    String? directory,
    String? workspace,
    PtyUpdateRequest? ptyUpdateRequest,
    CancelToken? cancelToken,
    Map<String, dynamic>? headers,
    Map<String, dynamic>? extra,
    ValidateStatus? validateStatus,
    ProgressCallback? onSendProgress,
    ProgressCallback? onReceiveProgress,
  }) async {
    const _operationId = r'pty.update';
    final _path = r'/pty/{ptyID}'.replaceAll(
      '{'
      r'ptyID'
      '}',
      encodeOpenCodePathSegment(
        serializeOpenCodeQueryParameter(ptyID).toString(),
      ),
    );
    final _options = Options(
      method: r'PUT',
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
      if (ptyUpdateRequest != null) {
        _bodyData = jsonEncode(ptyUpdateRequest);
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
        includeBody: ptyUpdateRequest != null,
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

    Pty? _responseData;

    try {
      final rawData = _response.data;
      _responseData = rawData == null
          ? null
          : deserialize<Pty, Pty>(rawData, 'Pty', growable: true);
    } catch (error, stackTrace) {
      throw DioException(
        requestOptions: _response.requestOptions,
        response: _response,
        type: DioExceptionType.unknown,
        error: error,
        stackTrace: stackTrace,
      );
    }

    return Response<Pty>(
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

  /// Connect to PTY session
  /// Establish a WebSocket connection streaming PTY output and accepting terminal input.
  ///
  /// Parameters:
  /// * [ptyID]
  /// * [locationLeftSquareBracketDirectoryRightSquareBracket]
  /// * [locationLeftSquareBracketWorkspaceRightSquareBracket]
  /// * [cursor]
  /// * [ticket]
  /// * [cancelToken] - A [CancelToken] that can be used to cancel the operation
  /// * [headers] - Can be used to add additional headers to the request
  /// * [extras] - Can be used to add flags to the request
  /// * [validateStatus] - A [ValidateStatus] callback that can be used to determine request success based on the HTTP status of the response
  /// * [onSendProgress] - A [ProgressCallback] that can be used to get the send progress
  /// * [onReceiveProgress] - A [ProgressCallback] that can be used to get the receive progress
  ///
  /// Returns a [Future] containing a [Response] with a [bool] as data
  /// Throws [DioException] if API call or serialization fails
  Future<Response<bool>> v2PtyConnect({
    required String ptyID,
    String? locationLeftSquareBracketDirectoryRightSquareBracket,
    String? locationLeftSquareBracketWorkspaceRightSquareBracket,
    String? cursor,
    String? ticket,
    CancelToken? cancelToken,
    Map<String, dynamic>? headers,
    Map<String, dynamic>? extra,
    ValidateStatus? validateStatus,
    ProgressCallback? onSendProgress,
    ProgressCallback? onReceiveProgress,
  }) async {
    const _operationId = r'v2.pty.connect';
    final _path = r'/api/pty/{ptyID}/connect'.replaceAll(
      '{'
      r'ptyID'
      '}',
      encodeOpenCodePathSegment(
        serializeOpenCodeQueryParameter(ptyID).toString(),
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
      if (cursor != null) r'cursor': serializeOpenCodeQueryParameter(cursor),
      if (ticket != null) r'ticket': serializeOpenCodeQueryParameter(ticket),
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

  /// Create PTY WebSocket token
  /// Create a short-lived single-use ticket for opening a PTY WebSocket connection.
  ///
  /// Parameters:
  /// * [ptyID]
  /// * [locationLeftSquareBracketDirectoryRightSquareBracket]
  /// * [locationLeftSquareBracketWorkspaceRightSquareBracket]
  /// * [cancelToken] - A [CancelToken] that can be used to cancel the operation
  /// * [headers] - Can be used to add additional headers to the request
  /// * [extras] - Can be used to add flags to the request
  /// * [validateStatus] - A [ValidateStatus] callback that can be used to determine request success based on the HTTP status of the response
  /// * [onSendProgress] - A [ProgressCallback] that can be used to get the send progress
  /// * [onReceiveProgress] - A [ProgressCallback] that can be used to get the receive progress
  ///
  /// Returns a [Future] containing a [Response] with a [V2PtyConnectToken200Response] as data
  /// Throws [DioException] if API call or serialization fails
  Future<Response<V2PtyConnectToken200Response>> v2PtyConnectToken({
    required String ptyID,
    String? locationLeftSquareBracketDirectoryRightSquareBracket,
    String? locationLeftSquareBracketWorkspaceRightSquareBracket,
    CancelToken? cancelToken,
    Map<String, dynamic>? headers,
    Map<String, dynamic>? extra,
    ValidateStatus? validateStatus,
    ProgressCallback? onSendProgress,
    ProgressCallback? onReceiveProgress,
  }) async {
    const _operationId = r'v2.pty.connectToken';
    final _path = r'/api/pty/{ptyID}/connect-token'.replaceAll(
      '{'
      r'ptyID'
      '}',
      encodeOpenCodePathSegment(
        serializeOpenCodeQueryParameter(ptyID).toString(),
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

    V2PtyConnectToken200Response? _responseData;

    try {
      final rawData = _response.data;
      _responseData = rawData == null
          ? null
          : deserialize<
              V2PtyConnectToken200Response,
              V2PtyConnectToken200Response
            >(rawData, 'V2PtyConnectToken200Response', growable: true);
    } catch (error, stackTrace) {
      throw DioException(
        requestOptions: _response.requestOptions,
        response: _response,
        type: DioExceptionType.unknown,
        error: error,
        stackTrace: stackTrace,
      );
    }

    return Response<V2PtyConnectToken200Response>(
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

  /// Create PTY session
  /// Create a pseudo-terminal session for a location.
  ///
  /// Parameters:
  /// * [ptyCreateRequest]
  /// * [locationLeftSquareBracketDirectoryRightSquareBracket]
  /// * [locationLeftSquareBracketWorkspaceRightSquareBracket]
  /// * [cancelToken] - A [CancelToken] that can be used to cancel the operation
  /// * [headers] - Can be used to add additional headers to the request
  /// * [extras] - Can be used to add flags to the request
  /// * [validateStatus] - A [ValidateStatus] callback that can be used to determine request success based on the HTTP status of the response
  /// * [onSendProgress] - A [ProgressCallback] that can be used to get the send progress
  /// * [onReceiveProgress] - A [ProgressCallback] that can be used to get the receive progress
  ///
  /// Returns a [Future] containing a [Response] with a [V2PtyCreate200Response] as data
  /// Throws [DioException] if API call or serialization fails
  Future<Response<V2PtyCreate200Response>> v2PtyCreate({
    required PtyCreateRequest ptyCreateRequest,
    String? locationLeftSquareBracketDirectoryRightSquareBracket,
    String? locationLeftSquareBracketWorkspaceRightSquareBracket,
    CancelToken? cancelToken,
    Map<String, dynamic>? headers,
    Map<String, dynamic>? extra,
    ValidateStatus? validateStatus,
    ProgressCallback? onSendProgress,
    ProgressCallback? onReceiveProgress,
  }) async {
    const _operationId = r'v2.pty.create';
    final _path = r'/api/pty';
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
      _bodyData = jsonEncode(ptyCreateRequest);
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

    V2PtyCreate200Response? _responseData;

    try {
      final rawData = _response.data;
      _responseData = rawData == null
          ? null
          : deserialize<V2PtyCreate200Response, V2PtyCreate200Response>(
              rawData,
              'V2PtyCreate200Response',
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

    return Response<V2PtyCreate200Response>(
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

  /// Get PTY session
  /// Get one PTY session, including its exit code once exited.
  ///
  /// Parameters:
  /// * [ptyID]
  /// * [locationLeftSquareBracketDirectoryRightSquareBracket]
  /// * [locationLeftSquareBracketWorkspaceRightSquareBracket]
  /// * [cancelToken] - A [CancelToken] that can be used to cancel the operation
  /// * [headers] - Can be used to add additional headers to the request
  /// * [extras] - Can be used to add flags to the request
  /// * [validateStatus] - A [ValidateStatus] callback that can be used to determine request success based on the HTTP status of the response
  /// * [onSendProgress] - A [ProgressCallback] that can be used to get the send progress
  /// * [onReceiveProgress] - A [ProgressCallback] that can be used to get the receive progress
  ///
  /// Returns a [Future] containing a [Response] with a [V2PtyGet200Response] as data
  /// Throws [DioException] if API call or serialization fails
  Future<Response<V2PtyGet200Response>> v2PtyGet({
    required String ptyID,
    String? locationLeftSquareBracketDirectoryRightSquareBracket,
    String? locationLeftSquareBracketWorkspaceRightSquareBracket,
    CancelToken? cancelToken,
    Map<String, dynamic>? headers,
    Map<String, dynamic>? extra,
    ValidateStatus? validateStatus,
    ProgressCallback? onSendProgress,
    ProgressCallback? onReceiveProgress,
  }) async {
    const _operationId = r'v2.pty.get';
    final _path = r'/api/pty/{ptyID}'.replaceAll(
      '{'
      r'ptyID'
      '}',
      encodeOpenCodePathSegment(
        serializeOpenCodeQueryParameter(ptyID).toString(),
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

    V2PtyGet200Response? _responseData;

    try {
      final rawData = _response.data;
      _responseData = rawData == null
          ? null
          : deserialize<V2PtyGet200Response, V2PtyGet200Response>(
              rawData,
              'V2PtyGet200Response',
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

    return Response<V2PtyGet200Response>(
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

  /// List PTY sessions
  /// List PTY sessions for a location, including exited sessions retained until removal.
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
  /// Returns a [Future] containing a [Response] with a [V2PtyList200Response] as data
  /// Throws [DioException] if API call or serialization fails
  Future<Response<V2PtyList200Response>> v2PtyList({
    String? locationLeftSquareBracketDirectoryRightSquareBracket,
    String? locationLeftSquareBracketWorkspaceRightSquareBracket,
    CancelToken? cancelToken,
    Map<String, dynamic>? headers,
    Map<String, dynamic>? extra,
    ValidateStatus? validateStatus,
    ProgressCallback? onSendProgress,
    ProgressCallback? onReceiveProgress,
  }) async {
    const _operationId = r'v2.pty.list';
    final _path = r'/api/pty';
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

    V2PtyList200Response? _responseData;

    try {
      final rawData = _response.data;
      _responseData = rawData == null
          ? null
          : deserialize<V2PtyList200Response, V2PtyList200Response>(
              rawData,
              'V2PtyList200Response',
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

    return Response<V2PtyList200Response>(
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

  /// Remove PTY session
  /// Terminate and remove one PTY session.
  ///
  /// Parameters:
  /// * [ptyID]
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
  Future<Response<void>> v2PtyRemove({
    required String ptyID,
    String? locationLeftSquareBracketDirectoryRightSquareBracket,
    String? locationLeftSquareBracketWorkspaceRightSquareBracket,
    CancelToken? cancelToken,
    Map<String, dynamic>? headers,
    Map<String, dynamic>? extra,
    ValidateStatus? validateStatus,
    ProgressCallback? onSendProgress,
    ProgressCallback? onReceiveProgress,
  }) async {
    const _operationId = r'v2.pty.remove';
    final _path = r'/api/pty/{ptyID}'.replaceAll(
      '{'
      r'ptyID'
      '}',
      encodeOpenCodePathSegment(
        serializeOpenCodeQueryParameter(ptyID).toString(),
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

  /// Update PTY session
  /// Update the title or viewport size of one PTY session.
  ///
  /// Parameters:
  /// * [ptyID]
  /// * [ptyUpdateRequest]
  /// * [locationLeftSquareBracketDirectoryRightSquareBracket]
  /// * [locationLeftSquareBracketWorkspaceRightSquareBracket]
  /// * [cancelToken] - A [CancelToken] that can be used to cancel the operation
  /// * [headers] - Can be used to add additional headers to the request
  /// * [extras] - Can be used to add flags to the request
  /// * [validateStatus] - A [ValidateStatus] callback that can be used to determine request success based on the HTTP status of the response
  /// * [onSendProgress] - A [ProgressCallback] that can be used to get the send progress
  /// * [onReceiveProgress] - A [ProgressCallback] that can be used to get the receive progress
  ///
  /// Returns a [Future] containing a [Response] with a [V2PtyGet200Response] as data
  /// Throws [DioException] if API call or serialization fails
  Future<Response<V2PtyGet200Response>> v2PtyUpdate({
    required String ptyID,
    required PtyUpdateRequest ptyUpdateRequest,
    String? locationLeftSquareBracketDirectoryRightSquareBracket,
    String? locationLeftSquareBracketWorkspaceRightSquareBracket,
    CancelToken? cancelToken,
    Map<String, dynamic>? headers,
    Map<String, dynamic>? extra,
    ValidateStatus? validateStatus,
    ProgressCallback? onSendProgress,
    ProgressCallback? onReceiveProgress,
  }) async {
    const _operationId = r'v2.pty.update';
    final _path = r'/api/pty/{ptyID}'.replaceAll(
      '{'
      r'ptyID'
      '}',
      encodeOpenCodePathSegment(
        serializeOpenCodeQueryParameter(ptyID).toString(),
      ),
    );
    final _options = Options(
      method: r'PUT',
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
      _bodyData = jsonEncode(ptyUpdateRequest);
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

    V2PtyGet200Response? _responseData;

    try {
      final rawData = _response.data;
      _responseData = rawData == null
          ? null
          : deserialize<V2PtyGet200Response, V2PtyGet200Response>(
              rawData,
              'V2PtyGet200Response',
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

    return Response<V2PtyGet200Response>(
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
