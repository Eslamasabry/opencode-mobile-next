# Approved iconography contract

Baseline: `395f374`; candidate branch starts at integration `0eabc2d`. This contract replaces the initial suggestion to retain Material Rounded after the maintainer explicitly requested a different icon family.

Coordinator approved the package, glyph mapping and Android asset implementation on 2026-09-09. Page adoption is coordinated separately.

## Selected direction

Use **Phosphor** as the coherent in-app family. Regular is the default; selected navigation may use duotone of the same silhouette. Use fill only for Stop and where a state must remain unambiguous at small sizes. Do not mix thin/light/bold weights within a toolbar. Provider logos and file-type artwork remain identity assets rather than being forced into this family.

The comparison board contains actual Material font glyphs from the local Flutter toolchain and actual glyphs from the published Phosphor Flutter 2.1.0 archive. It is an optical reference rendered with Pillow/FreeType, not a Flutter screenshot. It intentionally shows equal 48px comparison size; that is not the proposed control size. [Comparison](material-phosphor-comparison.png).

Phosphor regular has a consistent lighter line and simpler silhouettes across the folder, bell, sliders, branch and terminal. Its workspace conversation pair communicates the app's actual subject more directly than Material's three circles. Duotone adds a quiet material cue while retaining the regular outline; using it on every action would introduce unnecessary decoration. The plus and arrow should remain regular, and More should keep its regular dots when selected because its duotone adds a rectangular plate inconsistent with other navigation destinations.

## Font and runtime contract

Artwork is the exact official Phosphor 2.1.0 regular, duotone and fill fonts, copied unchanged with the MIT license. Source archive SHA-256 and individual hashes are recorded in `assets/fonts/phosphor/provenance.json`. Full license is bundled in `LICENSES/MIT-Phosphor.txt` and listed in Third-Party Notices. [Published source](https://pub.dev/packages/phosphor_flutter), [official repository](https://github.com/phosphor-icons/flutter).

The initially approved `phosphor_flutter: 2.1.0` dependency resolves but **does not compile on pinned Flutter 3.47.2**: its `PhosphorIconData extends IconData` conflicts with Flutter's final `IconData` class. The coordinator approved the bounded fallback: native static const `IconData` declarations with verified published codepoints, three bundled font families, and an explicit three-entry duotone background map. No patched cache or SDK, fork, additional dependency, or runtime font download. The final lockfile has no change.

Every foreground and background glyph is a literal const IconData. `AppIconography` has `@staticIconProvider`; no runtime IconData construction or computed secondary codepoints. The duotone map is keyed by integer foreground codepoint and consulted only for the duotone family. This satisfies static-source requirements for release tree shaking; final release font subsetting and APK size still need build evidence.

## Public API

`lib/ui/app_iconography.dart` owns `AppIconography` constants, `AppGlyph`, and `AppBrandMark`. Page code imports this module rather than the package. Constructor:

```dart
const AppGlyph(
  IconData icon, {
  Key? key,
  double? size,
  Color? color,
  String? semanticLabel,
  TextDirection? textDirection,
});
```

`const AppBrandMark({Key? key, double size = 32, String? semanticLabel})` renders the transparent portal SVG at 24 or 32px with theme-primary tint. It has no launcher background or shadow. It is decorative unless a standalone semantic label is supplied; use no label beside the app name. The SVG viewBox is cropped to the mark rather than retaining adaptive safe-space padding.

`AppGlyph` inherits IconTheme sizing/color when unspecified and gives duotone a fixed 20% second-layer opacity. Any semantic label is exposed once; adjacent visible text or IconButton tooltip should carry meaning when possible. Only directional navigation arrows and chevrons mirror in RTL. Technical symbols preserve their orientation; the wrapper also accepts an explicit direction.

| Meaning | Constant name | Published glyph |
|---|---|---|
| Workspace | `workspace` / `workspaceSelected` | regular/duotone `chatsCircle` |
| Files | `files` / `filesSelected` | regular/duotone `folderSimple` |
| Activity | `activity` / `activitySelected` | regular/duotone `bellSimple` |
| More | `more` | regular `dotsThree` |
| Row menu | `menu` | regular `dotsThreeVertical` |
| New session | `add` | regular `plus` |
| Send | `send` | regular `arrowUp` |
| Stop | `stop` | fill `stop` |
| Settings | `settings` | regular `slidersHorizontal` |
| Search | `search` | regular `magnifyingGlass` |
| Branch/isolated task | `branch` | regular `gitBranch` |
| Terminal | `terminal` | regular `terminalWindow` |
| Copy | `copy` | regular `copySimple` |
| Expand | `expand` | regular `arrowsOutSimple` |
| Close | `close` | regular `x` |
| Back | `back` | regular `arrowLeft` |
| Retry | `retry` | regular `arrowClockwise` |
| External link | `externalLink` | regular `arrowSquareOut` |
| Review/changes | `review` | regular `gitDiff` |

Additional whole-page vocabulary, verified against the published 2.1.0 source:

| Constants | Published regular glyphs, in matching order |
|---|---|
| `chevronRight`, `chevronDown`, `chevronUp` | `caretRight`, `caretDown`, `caretUp` |
| `check`, `info`, `warning`, `error` | `check`, `info`, `warning`, `xCircle` |
| `cloud`, `cloudOff`, `server` | `cloud`, `cloudSlash`, `hardDrives` |
| `model`, `agent`, `tools`, `extensions` | `brain`, `robot`, `wrench`, `puzzlePiece` |
| `keyboard`, `guide`, `bug`, `appearance` | `keyboard`, `bookOpen`, `bug`, `palette` |
| `privacy`, `diagnostics`, `usage` | `shieldCheck`, `pulse`, `chartBar` |
| `link`, `unlink`, `attach` | `link`, `linkBreak`, `paperclip` |
| `download`, `upload`, `file`, `code` | `downloadSimple`, `uploadSimple`, `file`, `code` |
| `archive`, `delete`, `edit`, `pin`, `unpin` | `archive`, `trash`, `pencilSimple`, `pushPin`, `pushPinSlash` |
| `computer`, `folderAdd`, `question`, `permissions` | `desktop`, `folderSimplePlus`, `question`, `key` |
| `clock`, `star`, `mic`, `image`, `camera` | `clock`, `star`, `microphone`, `image`, `camera` |

`starFilled` uses `PhosphorIconsFill.star`. The server symbol uses a familiar stacked-device silhouette (`hardDrives`); it must retain its Server label because the glyph can also mean storage. Brain is a model-selection metaphor, not a claim about cognition. Agent uses the package's robot silhouette. Privacy uses a shield; permissions use a key so the two concepts do not share a symbol. Diagnostics uses a pulse; usage uses a chart, keeping health and consumption distinct. These symbols accompany clear labels in broad menus; they do not make unfamiliar technical terms self-explanatory.

Sizes: 24px navigation and principal toolbar glyphs, 20px inline actions/status, 16px only noninteractive metadata. Interactive regions remain at least 48dp. Use color and a quiet indicator to reinforce selection; do not change the underlying action meaning when toggled. Transition icon state over 150–180ms, with no spatial movement of the hit target. Reduced-motion uses immediate state changes. Do not repeatedly pulse inactive navigation or add independently animated decoration to every glyph.

## Adoption boundary

This branch owns the approved bundled-font declarations, module, Android branding resources and shared 256px branding bitmap; the lockfile remains unchanged. Coordinator owns shared theme and page-adoption contracts. No broad replacements in other owners' pages. Migrate one complete toolbar/navigation family at a time to avoid a half-Material, half-Phosphor screen. Retain existing `AppIcons` until coordinator integrates a compatibility mapping; do not create two competing spellings of the same action indefinitely.

## Verification and remaining evidence

Verified on Shorebird Flutter 3.47.2 (`e16cf749ccaa38d7050335ff305def49b1c7c84c`): package resolution, five focused widget tests, two light/dark font-and-SVG render fixtures, scoped Dart analysis, and Android AAPT2 36.1.0 resource compilation. The SVG brand, regular glyphs, selected duotone, disabled send and filled stop renders were visually inspected. The component fixtures are in `docs/qa/clear-iconography/`; they are not installed app screenshots. XML and density checks plus deterministic regeneration of all five legacy PNGs passed.

Remaining integration evidence: whole-page adoption, large-text page layout, release font subsetting and installed Android launcher/themed-icon capture. No signing or installation occurred in this slice.

## Whole-app migration map

`material-mapping.json` covers the coordinator's 272-name Material inventory. Values are semantic `AppIconography` names; `KEEP_*` explicitly preserves the original icon where this family has no equivalent state or operation. Unknown/provider/platform marks must also remain unchanged. This is a reviewed vocabulary map, not proof that every calling page has adopted the family.

Twenty names retain Material, including search/key/timer off, extension/folder off, sync error/lock, file move/restore and wrap-text. Replacing these with an ordinary enabled/search/upload icon would change the communicated meaning. No custom slash overlay is introduced in this batch. Distinct available states use actual Phosphor symbols: eye/eyeSlash, camera/cameraSlash, cloud/cloudSlash, pencilSimple/pencilSimpleSlash, deviceMobile/deviceMobileSlash, checked/empty radio and checkbox, warning/error/check, and filled star/stop.

Some conceptual substitutions require their existing visible labels: globe-off uses a slashed network, cloud-sync uses cyclic arrows, support uses headset, model uses brain, server uses stacked drives, projects uses buildings. The map does not turn these into unlabeled commands. Glyph mirroring is limited to navigation directions; terminal, branch, code, clocks, processor and other technical shapes keep their orientation.

The 120 supplemental declarations are literal native const IconData with source names in `glyph-sources.json`. Every codepoint and font style was compared against the published 2.1.0 Dart source; all mapping references resolve. These source-only additions follow the tested base commit and await the coordinator's integration format/analyze/test checkpoint. No new renderer, asset or dependency is introduced by this follow-up.
