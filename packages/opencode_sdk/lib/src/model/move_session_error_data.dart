//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'move_session_error_data.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class MoveSessionErrorData {
  /// Returns a new [MoveSessionErrorData] instance.
  MoveSessionErrorData({required this.message});

  @JsonKey(name: r'message', required: true, includeIfNull: false)
  final String message;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is MoveSessionErrorData &&
            runtimeType == other.runtimeType &&
            equals([message], [other.message]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([message]);

  factory MoveSessionErrorData.fromJson(Map<String, dynamic> json) =>
      _$MoveSessionErrorDataFromJson(json);

  Map<String, dynamic> toJson() => _$MoveSessionErrorDataToJson(this);

  String toString() {
    return toJson().toString();
  }
}
