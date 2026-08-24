//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'tui_control_next200_response.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class TuiControlNext200Response {
  /// Returns a new [TuiControlNext200Response] instance.
  TuiControlNext200Response({required this.path, required this.body});

  @JsonKey(name: r'path', required: true, includeIfNull: false)
  final String path;

  @JsonKey(name: r'body', required: true, includeIfNull: true)
  final Object? body;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is TuiControlNext200Response &&
            runtimeType == other.runtimeType &&
            equals([path, body], [other.path, other.body]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([path, body]);

  factory TuiControlNext200Response.fromJson(Map<String, dynamic> json) =>
      _$TuiControlNext200ResponseFromJson(json);

  Map<String, dynamic> toJson() => _$TuiControlNext200ResponseToJson(this);

  String toString() {
    return toJson().toString();
  }
}
