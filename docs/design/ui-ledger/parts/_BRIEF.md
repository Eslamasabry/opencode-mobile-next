# UI ledger: extraction brief for area agents

You are one of 13 agents building a UI ledger of a Flutter app (package
`opencode_mobile`). Repository root (a git worktree, use absolute paths):

    /home/eslam/Storage/Code/oc_app/.claude/worktrees/agent-ae9d46939f3782892

This is static analysis and documentation. Do NOT change any Dart code, do NOT
run flutter/dart builds or tests, do NOT commit, do NOT touch any file other
than your single output file. Do not delegate to further subagents.

## Your output

Exactly one JSON file: `docs/design/ui-ledger/parts/<your-area>.json` with shape

```json
{
  "area": "<your-area>",
  "pages": [ Page, ... ],
  "notPages": [ {"file": "lib/ui/widgets/x.dart", "reason": "pure rendering helper, no surface and no interactive element"} ],
  "notes": ["anything you could not resolve, with file:line"]
}
```

Write the file early (after your first file or two) and rewrite it as you go, so
nothing is lost if you are interrupted. Always keep it valid JSON; verify with
`python3 -m json.tool <file> > /dev/null` at the end.

Every file assigned to you must end up either as the `file` of at least one
Page, or as the `file` of an element inside a page (see "embedded widgets"), or
in `notPages` with a reason.

## Page

```json
{
  "id": "servers",
  "title": "Servers",
  "kind": "screen|sheet|dialog|tab|overlay|onboarding-step|menu",
  "file": "lib/ui/screens/servers_screen.dart",
  "widget": "ServersScreen",
  "presentedBy": ["showServerEditor"],
  "line": 42,
  "purpose": "one sentence",
  "reachedFrom": [{"page": "more", "element": "more-servers-tile"}],
  "gates": ["capabilities.fileBrowsing", "platform: android only", "backend: openCode only"],
  "elements": [ Element, ... ]
}
```

A Page is any surface with its own visual context:
- a full screen (pushed via `Navigator.push`/`MaterialPageRoute`/named route, or a root tab),
- a tab inside a screen when the tab has its own body (`kind: "tab"`),
- a bottom sheet (`showModalBottomSheet`, and wrappers such as `showConfirmSheet`) -> `kind: "sheet"`,
- a dialog (`showDialog`, `AlertDialog`, `SimpleDialog`, `showAboutDialog`, `showLicensePage`, date/time pickers) -> `kind: "dialog"`,
- a `PopupMenuButton` / `showMenu` / `MenuAnchor` / context menu -> do NOT make it a page: record the opener as one element of type `icon-button` (action `other`, effect "opens overflow menu") and each menu entry as its own element of type `menu-item` on the SAME page, with gates,
- overlays / banners with their own buttons that float over other pages -> `kind: "overlay"`,
- steps of a multi-step wizard or onboarding flow -> `kind: "onboarding-step"` (one page per step when steps have different controls).

Rules:
- `id`: stable kebab-case. For a screen class `FooBarScreen` use `foo-bar`; for `FooSheet` / `showFooSheet` use `foo-sheet`; for dialogs `<owner-page>-<what>-dialog` (e.g. `servers-delete-dialog`); for tabs `<owner>-<tab>-tab`. Small inline confirm dialogs/sheets ARE pages (they have buttons), but keep them compact.
- `widget`: the Dart class name, or for an inline builder the enclosing function/method name (e.g. `_confirmDelete`).
- `presentedBy`: names of top-level/public functions or methods that open this page (e.g. `showModelPicker`, `openTerminal`), so other agents' references can be resolved. Empty list if none.
- `title`: the user-visible title in English. Resolve l10n (see below). If none, a short descriptive title in square brackets, e.g. `[Delete server confirmation]`.
- `line`: line of the class declaration or of the `show*` call for inline surfaces.
- `reachedFrom`: inbound edges you can prove from code you read. Use `{"page": "<page id>", "element": "<element id>"}`. If the opener lives in a file you were not assigned, run a quick grep for the widget/`show*` name across `lib/` and record `{"page": "?", "element": null, "widget": "<ClassOrFunctionThatOpensIt>", "file": "lib/...", "line": N}`; the coordinator resolves it. Non-UI triggers (notification tap, deep link, launch intent, keyboard shortcut, automatic on state change) are recorded as `{"page": "system", "element": null, "trigger": "notification tap ..."}`.
- `gates`: conditions under which the whole page is reachable/shown, exactly as coded (quote the expression: `conn.capabilities.terminal`, `platformCapabilities.supportsTermux`, `conn.backendKind == BackendKind.codex`, `kDebugMode`, connection state, feature flags).

## Element

```json
{
  "id": "servers-add-button",
  "type": "button|icon-button|fab|list-tile|menu-item|chip|toggle|text-field|slider|dropdown|tab|link|gesture|card|banner",
  "label": "Add server",
  "key": "ValueKey string if any, else null",
  "file": "only when the element is defined in a different file than the page (embedded widget)",
  "line": 512,
  "action": "navigate|open-sheet|open-dialog|mutate|toggle-setting|submit|copy|external-link|dismiss|other",
  "target": "server-editor",
  "targetWidget": "ServerEditorScreen",
  "effect": "what it does, one sentence, naming the controller/state method called",
  "gates": ["shown only when ..."],
  "destructive": false
}
```

Rules:
- Record EVERY interactive element: anything with `onPressed`, `onTap`, `onChanged`, `onSelected`, `onLongPress`, `onSubmitted`, `onDoubleTap`, `onDismissed`, `onRefresh` (pull to refresh -> type `gesture`), `onReorder`, swipe `Dismissible`, `InkWell`, `GestureDetector`, `TextField`, `Switch`, `Checkbox`, `Radio`, `Slider`, `DropdownButton`, `SegmentedButton` (one element per segment group is fine; list the segments in `label`), `TabBar` tabs, `ExpansionTile` (type `list-tile`, action `other`, effect "expands ..."), keyboard shortcuts handled by the page (type `gesture`, label like `Ctrl+F`).
- For list items built in a loop / builder, record ONE element for the item template (label pattern like `<session title>`) plus one element per distinct trailing action/swipe/long-press on it.
- Read the callback body. Do not guess from the label. If it delegates to a controller/state method, name it (`conn.refreshSessions()`, `_controller.deleteProfile(p)`).
- `action`: `navigate` = pushes a full screen or switches root tab; `open-sheet`; `open-dialog`; `mutate` = changes server/app data; `toggle-setting` = flips a persisted preference; `submit` = sends a form/prompt; `copy` = clipboard; `external-link` = leaves the app (url launcher, intent, share sheet); `dismiss` = closes the current surface (pop / cancel); `other` = local view state only (expand, filter, scroll, select).
- If a callback does several things, choose the action by the user-visible outcome and describe the rest in `effect`.
- `target`: page id for `navigate` / `open-sheet` / `open-dialog`, else `null`. If the target is defined in your own files, use the id you gave it. If it is defined elsewhere, derive the id with the naming rule above AND always fill `targetWidget` with the exact Dart class or `show*` function name that is invoked; the coordinator resolves ids by `targetWidget`. Named routes: set `targetWidget` to the route string, e.g. `"/servers"`. For confirm flows that open a confirmation first and then act, the element's action is `open-dialog`/`open-sheet` with the confirmation page as target, and the confirmation page's confirm button carries the `mutate` and `destructive: true`.
- `destructive`: true for delete/remove/revert/discard/reset/kill/abort/disconnect/sign-out/overwrite.
- `id`: `<page-id>-<slug>`; unique within your file.
- `label`: visible text, else tooltip, else semantics label, else a bracketed description `[drag handle]`. Dynamic labels: give the pattern, e.g. `Delete "<name>"`.
- `key`: the string inside `ValueKey('...')` / `Key('...')` if present, else null.

### Embedded widgets

Widgets under `lib/ui/widgets/` (cards, strips, banners, rows) that are embedded
in a screen are not pages. Attach their interactive elements to the host page
that embeds them and set the element's `file` to the widget file. If the host
page belongs to another agent (grep for the widget class name to find the host),
emit a Page with `"kind": "overlay"`, `"id": "embedded-<widget-kebab>"`,
`"embeddedIn": ["<HostWidgetClass>", ...]` holding those elements; the
coordinator merges it into the host. Callback parameters passed in by the host
(e.g. `onRetry`) should be traced to the host when the host is in your files;
otherwise describe the callback contract in `effect`.

## Resolving labels (l10n)

Strings come from `lib/l10n/app_en.arb` through `AppLocalizations` (often via a
local helper such as `_l10n(context).someKey` or `lookupAppLocalizations(...)`).
Look keys up, e.g.:

    python3 - <<'EOF'
    import json; d=json.load(open('/home/eslam/Storage/Code/oc_app/.claude/worktrees/agent-ae9d46939f3782892/lib/l10n/app_en.arb'))
    for k in ['e7WorkspaceFiles','globalSessionsRefresh']: print(k,'=>',d.get(k))
    EOF

Batch many keys per call. Record the English text, not the key. Placeholders stay
as `{name}`.

## Method

1. For each assigned file run a grep for the interaction markers to get a
   checklist of line numbers, e.g.
   `grep -nE "onPressed|onTap|onChanged|onSelected|onLongPress|onSubmitted|onDoubleTap|onDismissed|onRefresh|onReorder|onDestinationSelected|showModalBottomSheet|showDialog|showMenu|PopupMenuButton|MenuAnchor|MaterialPageRoute|pushNamed|Navigator\.|showConfirmSheet|launchUrl|Clipboard|Share\." <file>`
   Every hit must be accounted for by an element (or be a plain `Navigator.pop`
   inside an element you already recorded).
2. Read the files fully (in chunks of up to ~700 lines). Large files are normal.
3. Build the JSON with a small Python script or by writing it directly; keep
   line numbers accurate (they are the evidence other agents will use).
4. Self-check before finishing: valid JSON; unique element ids; each grep hit
   covered; every assigned file accounted for.

## Final reply

Reply with: output path, number of pages and elements, and the list of
unresolved items. Keep it under 200 words. Do not paste the JSON.
