# Phosphor icon fonts (vendored)

The three fonts in `native/ui/kit/src/commonMain/composeResources/font/phosphor_*.ttf` are byte-for-byte copies of the
Flutter app's `assets/fonts/phosphor/*.ttf` at commit `8302de53773adef45eaaad016df2bd3cd51d1ebe` (FLUTTER_REF of the native UI rebuild), renamed to
Compose resource names. Those files come from the `phosphor_flutter` 2.1.0 archive
(https://pub.dev/api/archives/phosphor_flutter-2.1.0.tar.gz, sha256 `8a14f238f28a0b54842c5a4dc20676598dd4811fcba284ed828bd5a262c11fde`); Phosphor 2.1.0 artwork, MIT license, Copyright (c) 2020-2021
Phosphor Icons. The license is `LICENSE.txt` beside this file; `THIRD_PARTY_NOTICES.md` lists the component.

| Native file | Flutter file | sha256 | bytes |
|---|---|---|---|
| `phosphor_regular.ttf` | `assets/fonts/phosphor/Phosphor.ttf` | `06b91e022b7ee899a63efced879392a74f0bacbda54e4467e9f663220d173a10` | 488636 |
| `phosphor_duotone.ttf` | `assets/fonts/phosphor/Phosphor-Duotone.ttf` | `80e6f0d6fa8ce96a9933f48aaabddc592c5d2b52caf8f010a7e54a17c74084bf` | 567808 |
| `phosphor_fill.ttf` | `assets/fonts/phosphor/Phosphor-Fill.ttf` | `a53f5d2630cab5e3b7536ecb9d69d71519a2190298c22b1f8d770dd37bc2940a` | 449252 |

Update only by copying the files of a newer FLUTTER_REF again and editing this table; never edit the fonts.
