//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union035.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'tui_command_execute_data.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class TuiCommandExecuteData {
  /// Returns a new [TuiCommandExecuteData] instance.
  TuiCommandExecuteData({required this.command});

  @JsonKey(name: r'command', required: true, includeIfNull: false)
  final OpencodeSdkRawUnion035 command;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is TuiCommandExecuteData &&
            runtimeType == other.runtimeType &&
            equals([command], [other.command]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([command]);

  factory TuiCommandExecuteData.fromJson(Map<String, dynamic> json) =>
      _$TuiCommandExecuteDataFromJson(json);

  Map<String, dynamic> toJson() => _$TuiCommandExecuteDataToJson(this);

  String toString() {
    return toJson().toString();
  }
}
