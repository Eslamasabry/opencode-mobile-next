//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'pty_exited_data.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class PtyExitedData {
  /// Returns a new [PtyExitedData] instance.
  PtyExitedData({required this.id, required this.exitCode});

  @JsonKey(name: r'id', required: true, includeIfNull: false)
  final String id;

  // minimum: 0
  @JsonKey(name: r'exitCode', required: true, includeIfNull: false)
  final int exitCode;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is PtyExitedData &&
            runtimeType == other.runtimeType &&
            equals([id, exitCode], [other.id, other.exitCode]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([id, exitCode]);

  factory PtyExitedData.fromJson(Map<String, dynamic> json) =>
      _$PtyExitedDataFromJson(json);

  Map<String, dynamic> toJson() => _$PtyExitedDataToJson(this);

  String toString() {
    return toJson().toString();
  }
}
