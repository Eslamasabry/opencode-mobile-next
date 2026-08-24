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

import 'package:opencode_sdk/src/model/experimental_control_plane_move_session_request.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union039.dart';

class ControlPlaneApi {
  final Dio _dio;

  const ControlPlaneApi(this._dio);

  /// Move session
  /// Move a session to another project directory, optionally transferring local changes.
  ///
  /// Parameters:
  /// * [experimentalControlPlaneMoveSessionRequest]
  /// * [cancelToken] - A [CancelToken] that can be used to cancel the operation
  /// * [headers] - Can be used to add additional headers to the request
  /// * [extras] - Can be used to add flags to the request
  /// * [validateStatus] - A [ValidateStatus] callback that can be used to determine request success based on the HTTP status of the response
  /// * [onSendProgress] - A [ProgressCallback] that can be used to get the send progress
  /// * [onReceiveProgress] - A [ProgressCallback] that can be used to get the receive progress
  ///
  /// Returns a [Future]
  /// Throws [DioException] if API call or serialization fails
  Future<Response<void>> experimentalControlPlaneMoveSession({
    ExperimentalControlPlaneMoveSessionRequest?
    experimentalControlPlaneMoveSessionRequest,
    CancelToken? cancelToken,
    Map<String, dynamic>? headers,
    Map<String, dynamic>? extra,
    ValidateStatus? validateStatus,
    ProgressCallback? onSendProgress,
    ProgressCallback? onReceiveProgress,
  }) async {
    const _operationId = r'experimental.controlPlane.moveSession';
    final _path = r'/experimental/control-plane/move-session';
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

    dynamic _bodyData;

    try {
      if (experimentalControlPlaneMoveSessionRequest != null) {
        _bodyData = jsonEncode(experimentalControlPlaneMoveSessionRequest);
      }
    } catch (error, stackTrace) {
      throw DioException(
        requestOptions: _options.compose(_dio.options, _path),
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
        includeBody: experimentalControlPlaneMoveSessionRequest != null,
        bodyData: _bodyData,
        options: _options,
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
}
