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
import 'package:opencode_sdk/src/model/v2_reference_list200_response.dart';

class ReferenceApi {
  final Dio _dio;

  const ReferenceApi(this._dio);

  /// List references
  /// List references available in the requested location.
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
  /// Returns a [Future] containing a [Response] with a [V2ReferenceList200Response] as data
  /// Throws [DioException] if API call or serialization fails
  Future<Response<V2ReferenceList200Response>> v2ReferenceList({
    String? locationLeftSquareBracketDirectoryRightSquareBracket,
    String? locationLeftSquareBracketWorkspaceRightSquareBracket,
    CancelToken? cancelToken,
    Map<String, dynamic>? headers,
    Map<String, dynamic>? extra,
    ValidateStatus? validateStatus,
    ProgressCallback? onSendProgress,
    ProgressCallback? onReceiveProgress,
  }) async {
    const _operationId = r'v2.reference.list';
    final _path = r'/api/reference';
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

    V2ReferenceList200Response? _responseData;

    try {
      final rawData = _response.data;
      _responseData = rawData == null
          ? null
          : deserialize<V2ReferenceList200Response, V2ReferenceList200Response>(
              rawData,
              'V2ReferenceList200Response',
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

    return Response<V2ReferenceList200Response>(
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
