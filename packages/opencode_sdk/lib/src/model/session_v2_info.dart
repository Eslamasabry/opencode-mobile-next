//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/location_ref.dart';
import 'package:opencode_sdk/src/model/session_tokens.dart';
import 'package:opencode_sdk/src/model/session_v2_info_time.dart';
import 'package:opencode_sdk/src/model/model_ref.dart';
import 'package:opencode_sdk/src/model/revert_state.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'session_v2_info.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SessionV2Info {
  /// Returns a new [SessionV2Info] instance.
  SessionV2Info({
    required this.id,

    this.parentID,

    required this.projectID,

    this.agent,

    this.model,

    required this.cost,

    required this.tokens,

    required this.time,

    required this.title,

    required this.location,

    this.subpath,

    this.revert,
  });

  @JsonKey(name: r'id', required: true, includeIfNull: false)
  final String id;

  @JsonKey(name: r'parentID', required: false, includeIfNull: false)
  final String? parentID;

  @JsonKey(name: r'projectID', required: true, includeIfNull: false)
  final String projectID;

  @JsonKey(name: r'agent', required: false, includeIfNull: false)
  final String? agent;

  @JsonKey(name: r'model', required: false, includeIfNull: false)
  final ModelRef? model;

  @JsonKey(name: r'cost', required: true, includeIfNull: false)
  final num cost;

  @JsonKey(name: r'tokens', required: true, includeIfNull: false)
  final SessionTokens tokens;

  @JsonKey(name: r'time', required: true, includeIfNull: false)
  final SessionV2InfoTime time;

  @JsonKey(name: r'title', required: true, includeIfNull: false)
  final String title;

  @JsonKey(name: r'location', required: true, includeIfNull: false)
  final LocationRef location;

  @JsonKey(name: r'subpath', required: false, includeIfNull: false)
  final String? subpath;

  @JsonKey(name: r'revert', required: false, includeIfNull: false)
  final RevertState? revert;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SessionV2Info &&
            runtimeType == other.runtimeType &&
            equals(
              [
                id,
                parentID,
                projectID,
                agent,
                model,
                cost,
                tokens,
                time,
                title,
                location,
                subpath,
                revert,
              ],
              [
                other.id,
                other.parentID,
                other.projectID,
                other.agent,
                other.model,
                other.cost,
                other.tokens,
                other.time,
                other.title,
                other.location,
                other.subpath,
                other.revert,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([
        id,
        parentID,
        projectID,
        agent,
        model,
        cost,
        tokens,
        time,
        title,
        location,
        subpath,
        revert,
      ]);

  factory SessionV2Info.fromJson(Map<String, dynamic> json) =>
      _$SessionV2InfoFromJson(json);

  Map<String, dynamic> toJson() => _$SessionV2InfoToJson(this);

  String toString() {
    return toJson().toString();
  }
}
