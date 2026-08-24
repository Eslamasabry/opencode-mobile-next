//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'assistant_message_path.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class AssistantMessagePath {
  /// Returns a new [AssistantMessagePath] instance.
  AssistantMessagePath({required this.cwd, required this.root});

  @JsonKey(name: r'cwd', required: true, includeIfNull: false)
  final String cwd;

  @JsonKey(name: r'root', required: true, includeIfNull: false)
  final String root;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is AssistantMessagePath &&
            runtimeType == other.runtimeType &&
            equals([cwd, root], [other.cwd, other.root]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([cwd, root]);

  factory AssistantMessagePath.fromJson(Map<String, dynamic> json) =>
      _$AssistantMessagePathFromJson(json);

  Map<String, dynamic> toJson() => _$AssistantMessagePathToJson(this);

  String toString() {
    return toJson().toString();
  }
}
