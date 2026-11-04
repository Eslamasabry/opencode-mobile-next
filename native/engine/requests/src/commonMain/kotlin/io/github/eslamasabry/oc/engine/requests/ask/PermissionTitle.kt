package io.github.eslamasabry.oc.engine.requests.ask

/**
 * A permission request's title is its plain action (Flutter `permissionRequestTitle`, COPY-15; B-L10N-016). Each
 * known id has its own words key in `:ui:words` (`e7PermissionAction1..5`, `chatRequestTitle*`); an empty id and an
 * id no server contract defines are both "Permission needed" (`e7PermissionAction6`): the id itself belongs behind
 * Details, never in the title. Ids are matched exactly (no case folding): `BASH` is not a known id.
 */
public enum class PermissionTitle(public val wordsKey: String) {
    Shell("e7PermissionAction1"),
    Edit("e7PermissionAction2"),
    Read("e7PermissionAction3"),
    ExternalDirectory("e7PermissionAction4"),
    DoomLoop("e7PermissionAction5"),
    Fetch("chatRequestTitleFetch"),
    WebSearch("chatRequestTitleWebSearch"),
    Search("chatRequestTitleSearch"),
    Task("chatRequestTitleTask"),
    Skill("chatRequestTitleSkill"),
    Mode("chatRequestTitleMode"),
    Generic("e7PermissionAction6"),
    ;

    public companion object {
        public fun of(permission: String): PermissionTitle =
            when (permission) {
                "bash", "shell" -> Shell
                "edit" -> Edit
                "read" -> Read
                "external_directory" -> ExternalDirectory
                "doom_loop" -> DoomLoop
                "webfetch" -> Fetch
                "websearch" -> WebSearch
                "grep", "glob" -> Search
                "task" -> Task
                "skill" -> Skill
                "mode" -> Mode
                else -> Generic
            }
    }
}
