//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'model_v2_info_request.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class ModelV2InfoRequest {
  /// Returns a new [ModelV2InfoRequest] instance.
  ModelV2InfoRequest({required this.headers, required this.body, this.variant});

  @JsonKey(name: r'headers', required: true, includeIfNull: false)
  final Map<String, String> headers;

  @JsonKey(name: r'body', required: true, includeIfNull: false)
  final Object body;

  @JsonKey(name: r'variant', required: false, includeIfNull: false)
  final String? variant;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is ModelV2InfoRequest &&
            runtimeType == other.runtimeType &&
            equals(
              [headers, body, variant],
              [other.headers, other.body, other.variant],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([headers, body, variant]);

  factory ModelV2InfoRequest.fromJson(Map<String, dynamic> json) =>
      _$ModelV2InfoRequestFromJson(json);

  Map<String, dynamic> toJson() => _$ModelV2InfoRequestToJson(this);

  String toString() {
    return toJson().toString();
  }
}
