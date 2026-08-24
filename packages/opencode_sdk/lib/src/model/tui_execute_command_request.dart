//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'tui_execute_command_request.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class TuiExecuteCommandRequest {
  /// Returns a new [TuiExecuteCommandRequest] instance.
  TuiExecuteCommandRequest({required this.command});

  @JsonKey(name: r'command', required: true, includeIfNull: false)
  final String command;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is TuiExecuteCommandRequest &&
            runtimeType == other.runtimeType &&
            equals([command], [other.command]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([command]);

  factory TuiExecuteCommandRequest.fromJson(Map<String, dynamic> json) =>
      _$TuiExecuteCommandRequestFromJson(json);

  Map<String, dynamic> toJson() => _$TuiExecuteCommandRequestToJson(this);

  String toString() {
    return toJson().toString();
  }
}
