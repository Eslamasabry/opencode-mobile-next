# Linux desktop test CJK fallback font fixtures

These two subsetted Noto Sans CJK Regular faces (`NotoSansCJKjp-Regular-subset.otf`
and `NotoSansCJKsc-Regular-subset.otf`) are test fixtures licensed under the SIL
Open Font License 1.1 (`NotoSansCJK-OFL.txt`). They are not application resources
and are not packaged into an Android or desktop app.

They were restored from this repository's prior l10n test fixtures (originally
subsetted from Debian `fonts-noto-cjk` to cover the Japanese and Simplified
Chinese string catalogs, including `日本語` and `简体中文`).

`native/gradle/desktop-golden-fonts.gradle` syncs these files beside the DejaVu
Sans faces into each module's isolated `build/golden-fonts/fonts/` directory and
configures Fontconfig to prefer `DejaVu Sans` first for `sans-serif` and `Noto Sans`,
falling back to `Noto Sans CJK JP` and `Noto Sans CJK SC` only for codepoints
DejaVu Sans does not cover. That keeps all existing English and Arabic desktop
golden metrics identical while preventing tofu boxes in CJK renders (B-603).
