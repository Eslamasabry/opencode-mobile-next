# Jetpack APIs used ahead of Compose Multiplatform (ADR §7.6)

Android-only `androidx.*` APIs that `androidMain` of `:ui:kit*` or `:ui:shell` uses before CMP republishes them. One row
per use. A row moves out when the named CMP version lets the call move to `commonMain`.

| API | androidx version | Module / file | Reason | CMP version that frees it |
|---|---|---|---|---|
| (none yet — slice 0) | | | | |
