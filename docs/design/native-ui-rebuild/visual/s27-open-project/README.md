# s27 — Open a project (B-182)

Left: Flutter goldens `test/goldens/folder_browser_<state>_dark.png` (origin/master). Right: the native page,
rendered by `native/feature/projects/src/desktopTest/.../OpenProjectScreenTest.kt` at 412 × 915, dark, density 1
(written to `native/feature/projects/build/s27-open-project/`).

| State | Side by side |
|---|---|
| Where is the project? (new; Flutter has no page) | [servers](servers-side-by-side.png) |
| The server's projects | [projects](projects-side-by-side.png) |
| Inside a folder, "Open opencode-mobile" | [inside](inside-side-by-side.png) |
| Loading | [loading](loading-side-by-side.png) |
| Still reading (slow) | [slow](slow-side-by-side.png) |
| Empty project space | [empty](empty-side-by-side.png) |
| Cannot be shown, Try again | [error](error-side-by-side.png) |

Known differences: native shows the flow as a page (bar with back = up one folder), Flutter as a bottom sheet;
native keeps New folder, Clone a repository and Type a path as visible rows under the folders (Flutter has New project
on its start page and "Enter a path" in the ⋯ menu, and no clone); the projects page shows each path under its mark.
